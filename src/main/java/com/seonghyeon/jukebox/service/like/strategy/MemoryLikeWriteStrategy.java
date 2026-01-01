package com.seonghyeon.jukebox.service.like.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class MemoryLikeWriteStrategy extends LikeWriteStrategy {

    private final AtomicReference<ConcurrentHashMap<Long, LongAdder>> currentBuffer = new AtomicReference<>(new ConcurrentHashMap<>());

    private final Function<Map<Long, Long>, Mono<Void>> likeBatchWriter;
    private final TransactionalOperator transactionalOperator;

    @Setter // 사용 시 application.yml 또는 환경 변수로 설정 필요
    private String backupPath = "outbox";

    @Override
    public Mono<Void> addLike(Long songId) {
        log.debug("[MemoryStrategy] addLike: songId={}", songId);
        return Mono.fromRunnable(() -> currentBuffer.get()
                .computeIfAbsent(songId, k -> new LongAdder())
                .increment()
        );
    }

    @Override
    public Mono<Void> removeLike(Long songId) {
        log.debug("[MemoryStrategy] removeLike: songId={}", songId);
        return Mono.fromRunnable(() -> currentBuffer.get()
                .computeIfAbsent(songId, k -> new LongAdder())
                .decrement()
        );
    }

    @Override
    protected Mono<Void> flushToDatabase() {
        return Mono.defer(() -> {
            long startTime = System.currentTimeMillis();
            ConcurrentHashMap<Long, LongAdder> rawSnapshot = currentBuffer.getAndSet(new ConcurrentHashMap<>());

            // Map<Long, LongAddr>(스냅샷)을 Map<Long, Long> 형태로 변환 (0이 아닌 값만)
            Map<Long, Long> snapshot = summarizeSnapshot(rawSnapshot);
            if (snapshot.isEmpty()) {
                log.debug("[MemoryStrategy] Flush skipped: No data in buffer.");
                return Mono.empty();
            }

            int targetCount = snapshot.size();
            log.info("[MemoryStrategy] Flush starting: Target songs count = {}", targetCount);

            return likeBatchWriter.apply(snapshot)
                    .as(transactionalOperator::transactional)
                    .doOnSuccess(v -> {
                        long duration = System.currentTimeMillis() - startTime;
                        log.info("[MemoryStrategy] Flush completed: {} songs updated in {}ms", targetCount, duration);
                    })
                    .doOnError(e -> {
                        log.error("[MemoryStrategy] DB Flush failed! Rolling back to buffer. Error: {}", e.getMessage());
                        // 버퍼 원복: 현재 다시 쌓이고 있는 currentBuffer에 실패한 스냅샷을 다시 합침 (롤백)
                        snapshot.forEach((songId, count) -> {
                            currentBuffer.get().computeIfAbsent(songId, k -> new LongAdder()).add(count);
                        });
                        log.warn("[MemoryStrategy] Successfully rolled back {} items to buffer.", targetCount);
                    });
        });
    }

    @PreDestroy
    public void onDestroy() {
        log.info("Shutting down LikeWriteStrategy, flushing likes to database...");

        flushToDatabase()
                .doOnSuccess(v -> log.info("Successfully flushed likes to database during shutdown."))
                .onErrorResume(error -> {
                    log.error("Error occurred while flushing likes to database during shutdown, saving to outbox file", error);

                    // 버퍼 스냅샷 추출 및 파일 저장
                    ConcurrentHashMap<Long, LongAdder> rawSnapshot = currentBuffer.getAndSet(new ConcurrentHashMap<>());
                    Map<Long, Long> snapshot = summarizeSnapshot(rawSnapshot);
                    saveToOutboxFile(snapshot);
                    return Mono.empty(); // 에러를 삼키고 정상 종료 흐름 유지
                })
                .block(Duration.ofSeconds(10));
    }

    private void saveToOutboxFile(Map<Long, Long> snapshot) {
        try {
            Path path = Paths.get(backupPath, "backup-" + System.currentTimeMillis() + ".json");
            Files.createDirectories(path.getParent());
            ObjectMapper objectMapper = new ObjectMapper();
            String json = objectMapper.writeValueAsString(snapshot);
            Files.writeString(path, json, StandardOpenOption.CREATE);
            log.warn(">>> CRITICAL: Data backed up to {}", path.toAbsolutePath());
        } catch (IOException e) {
            log.error(">>> FATAL: Backup failed! Data lost.", e);
        }
    }

    private Map<Long, Long> summarizeSnapshot(ConcurrentHashMap<Long, LongAdder> rawSnapshot) {
        return rawSnapshot.entrySet().stream()
                .filter(e -> e.getValue().sum() != 0)
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().sum()));
    }

}
