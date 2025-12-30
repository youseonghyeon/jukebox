package com.seonghyeon.jukebox.service.like.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class RedisLikeWriteStrategy extends LikeWriteStrategy {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final Function<Map<Long, LongAdder>, Mono<Void>> likeBatchWriter;
    private final TransactionalOperator transactionalOperator;

    private static final String REDIS_KEY = "jukebox:like:buffer";

    @Override
    public Mono<Void> addLike(Long songId) {
        log.debug("[RedisStrategy] addLike: songId={}", songId);
        return redisTemplate.opsForHash()
                .increment(REDIS_KEY, songId.toString(), 1L)
                .then();
    }

    @Override
    public Mono<Void> removeLike(Long songId) {
        log.debug("[RedisStrategy] removeLike: songId={}", songId);
        return redisTemplate.opsForHash()
                .increment(REDIS_KEY, songId.toString(), -1L)
                .then();
    }

    @Override
    protected Mono<Void> flushToDatabase() {
        return Mono.defer(() -> {
            long startTime = System.currentTimeMillis();
            String snapshotKey = REDIS_KEY + ":snapshot";

            // 1. 기존 스냅샷이 있는지 확인하고 처리
            return redisTemplate.hasKey(snapshotKey)
                    .filter(hasSnapshot -> hasSnapshot)
                    .flatMap(unused -> {
                        log.info("[RedisStrategy] Unprocessed snapshot found. Processing existing snapshot first.");
                        return processSnapshot(snapshotKey, startTime);
                    })
                    // 2. 스냅샷이 없다면(empty), 버퍼를 스냅샷으로 전환 후 처리
                    .switchIfEmpty(
                            redisTemplate.hasKey(REDIS_KEY)
                                    .filter(exists -> exists)
                                    .flatMap(exists -> redisTemplate.rename(REDIS_KEY, snapshotKey))
                                    .then(Mono.defer(() -> processSnapshot(snapshotKey, startTime)))
                    )
                    .doOnError(e -> log.error("[RedisStrategy] Flush failed! Error: {}", e.getMessage()))
                    .then();
        });
    }

    private Mono<Void> processSnapshot(String snapshotKey, long startTime) {
        return redisTemplate.opsForHash().entries(snapshotKey)
                .collectList()
                .filter(entries -> !entries.isEmpty())
                // 데이터가 없으면 스냅샷 키만 지우고 종료
                .switchIfEmpty(redisTemplate.delete(snapshotKey).then(Mono.empty()))
                .flatMap(entries -> {
                    Map<Long, LongAdder> snapshot = convertToSnapshotMap(entries);
                    int targetCount = snapshot.size();

                    log.info("[RedisStrategy] Processing snapshot: Target count = {}", targetCount);

                    return likeBatchWriter.apply(snapshot)
                            .as(transactionalOperator::transactional)
                            .then(redisTemplate.delete(snapshotKey))
                            .doOnSuccess(v -> log.info("[RedisStrategy] Snapshot processed successfully: {} songs in {}ms",
                                    targetCount, System.currentTimeMillis() - startTime));
                })
                .then();
    }

    private Map<Long, LongAdder> convertToSnapshotMap(List<Map.Entry<Object, Object>> entries) {
        return entries.stream().collect(Collectors.toMap(
                e -> Long.parseLong(e.getKey().toString()),
                e -> {
                    LongAdder adder = new LongAdder();
                    adder.add(Long.parseLong(e.getValue().toString()));
                    return adder;
                }
        ));
    }
}
