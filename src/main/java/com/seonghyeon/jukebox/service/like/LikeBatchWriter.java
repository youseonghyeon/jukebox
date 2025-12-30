package com.seonghyeon.jukebox.service.like;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

@Component
public class LikeBatchWriter {

    public Mono<Void> updateLike(Map<Long, LongAdder> snapshot) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

}
