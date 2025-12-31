package com.seonghyeon.jukebox.controller;

import com.seonghyeon.jukebox.controller.dto.request.LikeRequest;
import com.seonghyeon.jukebox.controller.dto.response.AlbumStatsResponse;
import com.seonghyeon.jukebox.controller.dto.response.TopLikedResponse;
import com.seonghyeon.jukebox.service.SongStatisticsQueryService;
import com.seonghyeon.jukebox.service.like.SongLikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/v1/songs")
@RequiredArgsConstructor
@Tag(name = "Song", description = "곡 관련 API")
public class SongController {

    private final SongStatisticsQueryService songStatisticsQueryService;
    private final SongLikeService songLikeService;

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

    @Operation(
            summary = "곡 좋아요/좋아요 취소 처리",
            description = "특정 곡에 대해 사용자가 좋아요 또는 좋아요 취소를 할 수 있습니다."
    )
    @PostMapping("/{songId}/likes")
    public Mono<ResponseEntity<Void>> handleLike(
            @PathVariable Long songId,
            @RequestBody @Valid LikeRequest request
    ) {
        Mono<Void> mono = switch (request.action()) {
            case LIKE -> songLikeService.likeSong(songId, request.userId());
            case UNLIKE -> songLikeService.unlikeSong(songId, request.userId());
        };
        return mono.thenReturn(ResponseEntity.ok().build());
    }

    @Operation(
            summary = "최근 1시간 내 최다 좋아요 곡 조회",
            description = "최근 1시간 동안 가장 많은 좋아요를 받은 상위 10개 곡을 조회합니다."
    )
    @GetMapping("/top-liked")
    public Flux<TopLikedResponse> getTopLikedSongs() {
        LocalDateTime since = LocalDateTime.now().minus(Duration.ofHours(1));
        return songLikeService.getTopLikedSongs(since, 10)
                .map(TopLikedResponse::from);
    }

}
