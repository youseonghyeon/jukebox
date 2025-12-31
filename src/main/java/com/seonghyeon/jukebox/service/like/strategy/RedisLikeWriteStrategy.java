package com.seonghyeon.jukebox.service.like.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class RedisLikeWriteStrategy extends LikeWriteStrategy {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private final Function<Map<Long, Long>, Mono<Void>> likeBatchWriter;
    private static final Integer BATCH_WRITER_BUFFER_SIZE = 500;
    private final TransactionalOperator transactionalOperator;

    private static final String REDIS_KEY = "jukebox:like:buffer"; // 좋아요 임시 저장소 키
    private static final String SNAPSHOT_KEY = REDIS_KEY + ":snapshot"; // 스냅샷 키
    private static final String LOCK_KEY = "jukebox:like:lock"; // 분산 락 키

    // [SERVER_ID, LOCK_EXPIRY] 서버 환경에 맞춰 환경 변수로 설정 필요. 현재는 임시 값 사용
    private static final String SERVER_ID = "jukebox-server-1"; // 임시 서버 ID
    // 스케줄링 주기(1분)보다 1초 짧게 설정하여, 이전 작업이 지연될 경우 다음 주기의 중복 실행을 방지하고 최소한의 안전 마진을 확보
    private static final Duration LOCK_EXPIRY = Duration.ofSeconds(59);

    @Override
    public Mono<Void> addLike(Long songId) {
        log.debug("[RedisStrategy] addLike: songId={}", songId);
        return reactiveRedisTemplate.opsForHash()
                .increment(REDIS_KEY, songId.toString(), 1L)
                .then();
    }

    @Override
    public Mono<Void> removeLike(Long songId) {
        log.debug("[RedisStrategy] removeLike: songId={}", songId);
        return reactiveRedisTemplate.opsForHash()
                .increment(REDIS_KEY, songId.toString(), -1L)
                .then();
    }

    /**
     * Redis 분산 락과 원자적 스위칭(Atomic Rename)을 이용한 좋아요 데이터 Flush 로직.
     * * <p>이 구현체는 다중 인스턴스 환경에서 데이터 정합성을 보장하고,
     * 예상치 못한 장애 발생 시에도 데이터 유실을 방지하기 위해 설계되었습니다.</p>
     * * <h3>주요 처리 프로세스:</h3>
     * <ul>
     * <li><b>락 획득(Distributed Lock):</b> {@code LOCK_KEY}를 사용하여 중복 플러시를 방지합니다.
     * 이미 락이 선점된 경우 작업을 즉시 종료합니다.</li>
     * <li><b>장애 복구(Crash Recovery):</b> 작업 시작 전 {@code SNAPSHOT_KEY}의 존재를 확인합니다.
     * 이전 작업이 DB 반영 직전 실패했다면, 새 스냅샷을 뜨지 않고 기존 스냅샷을 먼저 재처리(Resume)합니다.</li>
     * <li><b>원자적 스냅샷(Atomic Snapshot):</b> Redis의 {@code RENAME} 명령어를 사용하여
     * 버퍼 데이터를 스냅샷 키로 즉시 격리합니다. 이 과정은 원자적으로 실행되어 데이터 유입의 공백을 없앱니다.</li>
     * <li><b>타임아웃 및 가용성:</b> {@code LOCK_EXPIRY}를 통해 특정 인스턴스의 지연이
     * 전체 시스템의 차단으로 이어지지 않도록 방어하며, 작업 완료 후에만 락을 명시적으로 해제합니다.</li>
     * </ul>
     */
    @Override
    protected Mono<Void> flushToDatabase() {
        return Mono.defer(() -> {
            long startTime = System.currentTimeMillis();

            return reactiveRedisTemplate.opsForValue()
                    .setIfAbsent(LOCK_KEY, SERVER_ID, LOCK_EXPIRY)
                    .filter(Boolean.TRUE::equals)
                    .switchIfEmpty(Mono.fromRunnable(() -> log.debug("[RedisStrategy] Flush skip: lock held by another instance")))
                    .flatMap(isLocked -> executeFlush()
                            .then(Mono.defer(() -> reactiveRedisTemplate.delete(LOCK_KEY)))
                            .doOnSuccess(v -> log.info("[RedisStrategy] Flush completed in {} ms", System.currentTimeMillis() - startTime))
                            .onErrorResume(e -> reactiveRedisTemplate.delete(LOCK_KEY).then(Mono.error(e)))
                    ).then();
        });
    }

    private Mono<Void> executeFlush() {
        return reactiveRedisTemplate.hasKey(SNAPSHOT_KEY)
                .flatMap(hasSnapshot -> {
                    if (hasSnapshot) { // Lock 획득했으나, 과거 미처리된 스냅샷이 존재하는 경우
                        log.warn("[RedisStrategy] Resume: Processing existing snapshot");
                        return processSnapshot();
                    }
                    return reactiveRedisTemplate.hasKey(REDIS_KEY)
                            .filter(exists -> exists)
                            .flatMap(exists -> {
                                log.debug("[RedisStrategy] Preparing snapshot for flush. Starting rename operation.");
                                return reactiveRedisTemplate.rename(REDIS_KEY, SNAPSHOT_KEY);
                            })
                            .flatMap(renamed -> {
                                log.debug("[RedisStrategy] Rename successful: Preparing to process snapshot (snapshot -> DB).");
                                return processSnapshot();
                            });
                })
                .doOnError(e -> log.error("[RedisStrategy] Flush failed! Error: {}", e.getMessage()));
    }

    private Mono<Void> processSnapshot() {
        ScanOptions options = ScanOptions.scanOptions().count(BATCH_WRITER_BUFFER_SIZE).build();
        return reactiveRedisTemplate.opsForHash().scan(SNAPSHOT_KEY, options)
                .buffer(BATCH_WRITER_BUFFER_SIZE)
                .flatMap(entries -> {
                    Map<Long, Long> snapshotMap = convertToSnapshotMap(entries);
                    if (snapshotMap.isEmpty()) return Mono.empty();
                    log.debug("[RedisStrategy] Writing batch of {} items to DB", snapshotMap.size());
                    return likeBatchWriter.apply(snapshotMap);
                })
                .as(transactionalOperator::transactional)
                .then(Mono.defer(() -> reactiveRedisTemplate.delete(SNAPSHOT_KEY)))
                .timeout(LOCK_EXPIRY, Mono.error(new RuntimeException("Snapshot processing exceeded lock expiry time")))
                .doOnSuccess(v -> log.debug("[RedisStrategy] Snapshot flush processed successfully"))
                .doOnError(e -> log.error("[RedisStrategy] Flush failed, snapshot preserved.", e))
                .then();
    }

    private Map<Long, Long> convertToSnapshotMap(List<Map.Entry<Object, Object>> entries) {
        return entries.stream()
                .map(entry -> {
                    try {
                        Long key = Long.parseLong(entry.getKey().toString());
                        Long value = Long.parseLong(entry.getValue().toString());
                        return Map.entry(key, value);
                    } catch (NumberFormatException e) {
                        log.error("[CRITICAL] Data corruption detected! Skipping. Key: {}, Val: {}", entry.getKey(), entry.getValue());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .filter(entry -> entry.getValue() != 0L)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v1));
    }

}
