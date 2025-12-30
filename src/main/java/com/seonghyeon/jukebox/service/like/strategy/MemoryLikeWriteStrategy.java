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

    private final Function<Map<Long, LongAdder>, Mono<Void>> likeBatchWriter;
    private final TransactionalOperator transactionalOperator;

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
            ConcurrentHashMap<Long, LongAdder> snapshot = currentBuffer.getAndSet(new ConcurrentHashMap<>());

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
                        log.error("[MemoryStrategy] Flush failed! Data lost potential for {} songs. Error: {}", targetCount, e.getMessage(), e);
                        // TODO 재시도 또는 백업 로직 추가
                    });
        });
    }

}
