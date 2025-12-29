package com.seonghyeon.jukebox.repository;

import com.seonghyeon.jukebox.entity.SongStatisticsEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

public interface SongStatisticsRepository extends R2dbcRepository<SongStatisticsEntity, Long>, SongStatisticsCustomRepository {

    @Modifying
    @Query("""
        INSERT INTO song_statistics (release_year, artist, album_count)
        SELECT YEAR(release_date), artist, COUNT(*)
        FROM songs
        WHERE release_date IS NOT NULL
        GROUP BY YEAR(release_date), artist
    """)
    Mono<Long> buildYearArtistStats();

}
