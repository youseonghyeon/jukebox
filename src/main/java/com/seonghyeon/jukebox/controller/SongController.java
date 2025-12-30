package com.seonghyeon.jukebox.controller;

import com.seonghyeon.jukebox.controller.dto.response.AlbumStatsResponse;
import com.seonghyeon.jukebox.service.SongStatisticsQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/v1/songs")
@RequiredArgsConstructor
@Tag(name = "Song", description = "곡 관련 API")
public class SongController {

    private final SongStatisticsQueryService songStatisticsQueryService;

    @Operation(
            summary = "연도/가수별 앨범 수 조회",
            description = "연도별 가수가 발매한 앨범 수를 페이징하여 조회합니다. 정렬 필드는 releaseYear, artist, albumCount가 가능합니다."
    )
    @GetMapping("/stats/album-counts")
    public Mono<Page<AlbumStatsResponse>> getAlbumStatsByYearAndArtist(
            @Parameter(description = "조회 연도 (예: 2024)", example = "2024")
            @RequestParam(required = false) Integer year,

            @Parameter(description = "가수명 (정확히 일치)", example = "King Gizzard & The Lizard Wizard")
            @RequestParam(required = false) String artist,

            @ParameterObject
            @PageableDefault(size = 20, sort = "releaseYear", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return songStatisticsQueryService.getAlbumStatsByYearAndArtist(year, artist, pageable)
                .map(page -> page.map(AlbumStatsResponse::from));
    }

}
