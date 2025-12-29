package com.seonghyeon.jukebox.controller;

import com.seonghyeon.jukebox.controller.dto.response.AlbumStatsResponse;
import com.seonghyeon.jukebox.service.SongQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/v1/songs")
@RequiredArgsConstructor
public class SongController {

    private final SongQueryService songQueryService;

    @GetMapping("/stats/album-counts")
    public Mono<Page<AlbumStatsResponse>> getAlbumStatsByYearAndArtist(Pageable pageable) {
        return songQueryService.getAlbumStatsByYearAndArtist(pageable)
                .map(page -> page.map(AlbumStatsResponse::from));
    }

}
