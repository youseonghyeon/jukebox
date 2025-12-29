package com.seonghyeon.jukebox.service;

import com.seonghyeon.jukebox.entity.SongStatisticsEntity;
import com.seonghyeon.jukebox.repository.SongStatisticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class SongStatisticsQueryService {

    private final SongStatisticsRepository songStatisticsRepository;

    @Transactional(readOnly = true)
    public Mono<Page<SongStatisticsEntity>> getAlbumStatsByYearAndArtist(@Nullable Integer year, @Nullable String artist, Pageable pageable) {
        return songStatisticsRepository.findAllByYearAndArtist(year, artist, pageable)
                .collectList()
                .zipWith(songStatisticsRepository.countByYearAndArtist(year, artist))
                .map(tuple -> new PageImpl<>(tuple.getT1(), pageable, tuple.getT2()));
    }

}
