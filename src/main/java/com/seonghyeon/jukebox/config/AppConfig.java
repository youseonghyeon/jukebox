package com.seonghyeon.jukebox.config;

import com.seonghyeon.jukebox.service.like.LikeBatchWriter;
import com.seonghyeon.jukebox.service.like.strategy.LikeWriteStrategy;
import com.seonghyeon.jukebox.service.like.strategy.MemoryLikeWriteStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.reactive.TransactionalOperator;

@Configuration
@EnableScheduling
public class AppConfig {

    @Bean
    @ConditionalOnProperty(name = "jukebox.like.strategy", havingValue = "memory", matchIfMissing = true)
    public LikeWriteStrategy memoryLikeStrategy(LikeBatchWriter likeBatchWriter, TransactionalOperator to) {
        return new MemoryLikeWriteStrategy(likeBatchWriter::updateLike, to);
    }
}
