package com.seonghyeon.jukebox.service.like.strategy;

import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Mono;

public abstract class LikeWriteStrategy {

    public abstract Mono<Void> addLike(Long songId, Mono<Void> saveHistoryAction);

    public abstract Mono<Void> removeLike(Long songId, Mono<Void> deleteHistoryAction);

    @Scheduled(cron = "${jukebox.like.write-buffer.cron:0 0/5 * * * *}")
    public void run() {
        flushToDatabase().block();
    }

    @PreDestroy
    public void onDestroy() {
        flushToDatabase().block();
    }

    protected abstract Mono<Void> flushToDatabase();
}
