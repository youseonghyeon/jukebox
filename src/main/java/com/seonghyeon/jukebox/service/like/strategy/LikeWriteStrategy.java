package com.seonghyeon.jukebox.service.like.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
public abstract class LikeWriteStrategy {

    public abstract Mono<Void> addLike(Long songId);

    public abstract Mono<Void> removeLike(Long songId);

    @Scheduled(cron = "${jukebox.like.write-buffer.cron:0 0/5 * * * *}")
    public void run() {
        flushToDatabase()
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        null,
                        e -> log.error("Error occurred while flushing likes to database", e)
                );
    }

    // CircuitBreaker 대상
    protected abstract Mono<Void> flushToDatabase();

}
