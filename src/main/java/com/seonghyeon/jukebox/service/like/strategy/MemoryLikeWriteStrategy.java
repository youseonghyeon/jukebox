package com.seonghyeon.jukebox.service.like.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

@Slf4j
@RequiredArgsConstructor
public class MemoryLikeWriteStrategy extends LikeWriteStrategy {

    private final AtomicReference<ConcurrentHashMap<Long, LongAdder>> currentBuffer = new AtomicReference<>(new ConcurrentHashMap<>());
    private final Function<Map<Long, LongAdder>, Mono<Void>> batchUpdater;
    private final TransactionalOperator transactionalOperator;

    @Override
    public Mono<Void> addLike(Long songId, Mono<Void> saveHistoryAction) {
        log.debug("[MemoryStrategy] addLike: songId={}", songId);
        return saveHistoryAction.doOnSuccess(v -> {
            currentBuffer.get()
                    .computeIfAbsent(songId, k -> new LongAdder())
                    .increment();
        });
    }

    @Override
    public Mono<Void> removeLike(Long songId, Mono<Void> deleteHistoryAction) {
        log.debug("[MemoryStrategy] removeLike: songId={}", songId);
        return deleteHistoryAction.doOnSuccess(v -> {
            currentBuffer.get()
                    .computeIfAbsent(songId, k -> new LongAdder())
                    .decrement();
        });
    }

    @Override
    protected Mono<Void> flushToDatabase() {
        log.debug("[MemoryStrategy] flushToDatabase started");
        ConcurrentHashMap<Long, LongAdder> snapshot = currentBuffer.getAndSet(new ConcurrentHashMap<>());
        if (snapshot.isEmpty()) {
            return Mono.empty();
        }

        log.info("메모리 플러시 시작: {}건", snapshot.size());
        return batchUpdater.apply(snapshot)
                .as(transactionalOperator::transactional) // 트랜잭션 적용
                .doOnSuccess(v -> log.info("데이터베이스 플러시 성공"))
                .doOnError(e -> {
                    log.error("Failed to flush likes to database", e);
                    // backupToOutbox(snapshot);
                });
    }

}
