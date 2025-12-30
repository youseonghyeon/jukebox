package com.seonghyeon.jukebox.repository;

import com.seonghyeon.jukebox.entity.SongLikeEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

public interface SongLikeRepository extends R2dbcRepository<SongLikeEntity, Long> {

    @Query("""
        SELECT COALESCE(SUM(IF(action = 'LIKE', 1, -1)), 0)
        FROM song_likes
        WHERE song_id = :songId AND user_id = :userId
    """)
    Mono<Integer> countUserLikeStatus(Long songId, Long userId);
}
