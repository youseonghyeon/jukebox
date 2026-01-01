package com.seonghyeon.jukebox.repository;

import com.seonghyeon.jukebox.entity.SongLikeEntity;
import com.seonghyeon.jukebox.repository.dto.SongLikeCountDto;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface SongLikeRepository extends R2dbcRepository<SongLikeEntity, Long> {

    @Query("""
        SELECT COALESCE(SUM(IF(action = 'LIKE', 1, -1)), 0)
        FROM song_likes
        WHERE song_id = :songId AND user_id = :userId
    """)
    Mono<Integer> countUserLikeStatus(Long songId, Long userId);

    @Query("""
        SELECT
            s.id AS song_id,
            s.title AS title,
            s.artist AS artist,
            s.album AS album,
            l.like_count AS like_count
        FROM songs s
        JOIN (
            SELECT
                song_id,
                SUM(IF(action = 'LIKE', 1, -1)) AS like_count
            FROM song_likes
            WHERE created_at >= :since
            GROUP BY song_id
            HAVING like_count > 0
        ) l ON s.id = l.song_id
        ORDER BY l.like_count DESC
        LIMIT :limit
    """)
    Flux<SongLikeCountDto> findTopLikedSongs(LocalDateTime since, int limit);
}
