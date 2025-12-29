package com.seonghyeon.jukebox.service;

import com.seonghyeon.jukebox.repository.SongRepository;
import com.seonghyeon.jukebox.repository.dto.YearArtistStatsDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class SongQueryService {

    private final SongRepository songRepository;

    @Transactional(readOnly = true)
    public Mono<Page<YearArtistStatsDto>> getAlbumStatsByYearAndArtist(Pageable pageable) {
        return Mono.zip(
                        songRepository.findStatsGrouped(pageable).collectList(),
                        songRepository.countStatsGrouped()
                )
                .map(tuple -> new PageImpl<>(tuple.getT1(), pageable, tuple.getT2()));
    }
}
