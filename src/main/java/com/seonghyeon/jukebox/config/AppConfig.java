package com.seonghyeon.jukebox.config;

import com.seonghyeon.jukebox.service.like.LikeBatchWriter;
import com.seonghyeon.jukebox.service.like.strategy.LikeWriteStrategy;
import com.seonghyeon.jukebox.service.like.strategy.MemoryLikeWriteStrategy;
import com.seonghyeon.jukebox.service.like.strategy.RedisLikeWriteStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.reactive.TransactionalOperator;

import java.time.Clock;

@Slf4j
@Configuration
@EnableScheduling
public class AppConfig {

    @Bean
    @ConditionalOnProperty(name = "jukebox.like.strategy", havingValue = "memory", matchIfMissing = true)
    public LikeWriteStrategy memoryLikeStrategy(LikeBatchWriter likeBatchWriter, TransactionalOperator to) {
        log.debug("Using MemoryLikeWriteStrategy");
        return new MemoryLikeWriteStrategy(likeBatchWriter::updateLike, to);
    }

    @Bean
    @ConditionalOnProperty(name = "jukebox.like.strategy", havingValue = "redis")
    public LikeWriteStrategy redisLikeStrategy(ReactiveRedisTemplate<String, String> reactiveRedisTemplate, LikeBatchWriter likeBatchWriter, TransactionalOperator to) {
        log.debug("Using RedisLikeWriteStrategy");
        return new RedisLikeWriteStrategy(reactiveRedisTemplate, likeBatchWriter::updateLike, to);
    }
}
