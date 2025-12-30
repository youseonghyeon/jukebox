package com.seonghyeon.jukebox.repository;

import com.seonghyeon.jukebox.entity.SongStatisticsEntity;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SongStatisticsCustomRepository {
    Flux<SongStatisticsEntity> findAllByYearAndArtist(Integer year, String artist, Pageable pageable);

    Mono<Long> countByYearAndArtist(Integer year, String artist);
}
