package com.seonghyeon.jukebox.service.like;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeBatchWriter {

    private final DatabaseClient databaseClient;

    public Mono<Void> updateLike(Map<Long, Long> snapshot) {
        if (snapshot.isEmpty()) {
            return Mono.empty();
        }

        return Flux.fromIterable(snapshot.entrySet())
                .flatMap(entry -> {
                    Long songId = entry.getKey();
                    long diff = entry.getValue();
                    if (diff == 0) return Mono.empty();

                    return databaseClient.sql("UPDATE songs SET total_likes = total_likes + :diff WHERE id = :id")
                            .bind("diff", diff)
                            .bind("id", songId)
                            .fetch()
                            .rowsUpdated()
                            .doOnNext(updated -> {
                                if (updated == 0) {
                                    log.warn("No song found to update likes. songId: {}", songId);
                                }
                            });
                }, 10)
                .then();
    }

}
