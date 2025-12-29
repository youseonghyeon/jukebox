package com.seonghyeon.jukebox.repository;

import com.seonghyeon.jukebox.entity.SongEntity;
import com.seonghyeon.jukebox.repository.dto.YearArtistStatsDto;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SongRepository extends R2dbcRepository<SongEntity, Long> {

    @Query("""
                SELECT release_year, artist, COUNT(*) as album_count
                FROM songs
                GROUP BY release_year, artist
                ORDER BY release_year DESC, album_count DESC
                LIMIT :#{#pageable.pageSize}
                OFFSET :#{#pageable.offset}
            """)
    Flux<YearArtistStatsDto> findStatsGrouped(Pageable pageable);

    @Query("""
                SELECT COUNT(*) FROM (
                    SELECT release_year FROM songs GROUP BY release_year, artist
                ) as temp
            """)
    Mono<Long> countStatsGrouped();
}
