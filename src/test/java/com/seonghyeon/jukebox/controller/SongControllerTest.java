package com.seonghyeon.jukebox.controller;

import com.seonghyeon.jukebox.entity.SongStatisticsEntity;
import com.seonghyeon.jukebox.service.SongStatisticsQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@WebFluxTest(SongController.class) // 특정 컨트롤러만 로드
class SongControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private SongStatisticsQueryService songStatisticsQueryService;

    @Test
    @DisplayName("성공적으로 연도 및 가수별 앨범 통계를 페이징하여 조회한다")
    void getAlbumStatsSuccess() {
        // given
        Integer year = 2024;
        String artist = "King Gizzard";
        SongStatisticsEntity entity = new SongStatisticsEntity(1L, year, artist, 10L);
        Pageable pageable = PageRequest.of(0, 20);
        PageImpl<SongStatisticsEntity> pageResponse = new PageImpl<>(List.of(entity), pageable, 1);

        given(songStatisticsQueryService.getAlbumStatsByYearAndArtist(eq(year), eq(artist), any(Pageable.class)))
                .willReturn(Mono.just(pageResponse));

        // when & then
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/songs/stats/album-counts")
                        .queryParam("year", year)
                        .queryParam("artist", artist)
                        .queryParam("page", 0)
                        .queryParam("size", 20)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.content[0].releaseYear").isEqualTo(year)
                .jsonPath("$.content[0].artist").isEqualTo(artist)
                .jsonPath("$.content[0].albumCount").isEqualTo(10)
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.totalPages").isEqualTo(1)
                .jsonPath("$.size").isEqualTo(20);
    }

    @Test
    @DisplayName("파라미터 없이 호출해도 기본 정렬 및 페이징 설정이 적용되어 응답한다")
    void getAlbumStatsWithNoParams() {
        // given
        PageImpl<SongStatisticsEntity> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        given(songStatisticsQueryService.getAlbumStatsByYearAndArtist(isNull(), isNull(), any(Pageable.class)))
                .willReturn(Mono.just(emptyPage));

        // when & then
        webTestClient.get()
                .uri("/api/v1/songs/stats/album-counts")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content").isArray()
                .jsonPath("$.totalElements").isEqualTo(0);
    }

    @Test
    @DisplayName("IllegalArgumentException이 발생하면 400 에러를 반환한다")
        // 서비스 단에서 유효성 검사 실패 시
    void getAlbumStatsFailWithBadRequest() {
        // given
        String errorMessage = "유효하지 않은 파라미터입니다.";
        given(songStatisticsQueryService.getAlbumStatsByYearAndArtist(any(), any(), any()))
                .willReturn(Mono.error(new IllegalArgumentException(errorMessage)));

        // when & then
        webTestClient.get()
                .uri("/api/v1/songs/stats/album-counts?year=-1") // 예시로 잘못된 값 전달
                .exchange()
                .expectStatus().isBadRequest() // HttpStatus.BAD_REQUEST (400)
                .expectBody()
                .jsonPath("$.code").isEqualTo("BAD_REQUEST")
                .jsonPath("$.message").isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("서버 내부에서 예상치 못한 Exception이 발생하면 500 에러를 반환한다")
    void getAlbumStatsFailWithInternalError() {
        // given
        given(songStatisticsQueryService.getAlbumStatsByYearAndArtist(any(), any(), any()))
                .willReturn(Mono.error(new RuntimeException("DB Connection Fail")));

        // when & then
        webTestClient.get()
                .uri("/api/v1/songs/stats/album-counts")
                .exchange()
                .expectStatus().is5xxServerError() // HttpStatus.INTERNAL_SERVER_ERROR (500)
                .expectBody()
                .jsonPath("$.code").isEqualTo("SERVER_ERROR")
                .jsonPath("$.message").isEqualTo("서버 내부 오류가 발생했습니다.");
    }

    @Test
    @DisplayName("사용자가 정렬 조건을 명시하면 서비스에 해당 정렬 정보가 전달되어야 한다")
    void getAlbumStatsWithSortParam() {
        // given
        // 클라이언트가 'albumCount,asc'로 요청했다고 가정
        given(songStatisticsQueryService.getAlbumStatsByYearAndArtist(any(), any(), argThat(p ->
                p.getSort().getOrderFor("albumCount") != null &&
                p.getSort().getOrderFor("albumCount").isAscending()
        ))).willReturn(Mono.just(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0)));

        // when & then
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/songs/stats/album-counts")
                        .queryParam("sort", "albumCount,asc")
                        .build())
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("페이지 번호가 범위를 벗어나도 빈 페이지를 정상적으로 반환해야 한다")
    void getAlbumStatsEmptyPageBound() {
        // given: 100번째 페이지 요청
        Pageable pageable = PageRequest.of(100, 20);
        PageImpl<SongStatisticsEntity> emptyPage = new PageImpl<>(List.of(), pageable, 10); // 전체는 10건뿐인데 100페이지 요청

        given(songStatisticsQueryService.getAlbumStatsByYearAndArtist(any(), any(), any()))
                .willReturn(Mono.just(emptyPage));

        // when & then
        webTestClient.get()
                .uri("/api/v1/songs/stats/album-counts?page=100")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content").isEmpty()
                .jsonPath("$.number").isEqualTo(100);
    }

    @Test
    @DisplayName("연도 파라미터에 숫자가 아닌 형식이 들어오면 400 에러를 반환한다")
    void getAlbumStatsTypeMismatch() {
        // when & then
        webTestClient.get()
                .uri("/api/v1/songs/stats/album-counts?year=not-a-number")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("BAD_REQUEST");
    }

    @Test
    @DisplayName("페이지 사이즈가 100을 초과하면 100으로 제한되어야 한다")
    void getAlbumStatsSizeLimit() {
        // given: 클라이언트가 500을 요청함
        int requestedSize = 500;
        int expectedSize = 100;

        // 서비스가 실제로 100이라는 사이즈를 인자로 받는지 검증 (argThat 사용)
        given(songStatisticsQueryService.getAlbumStatsByYearAndArtist(any(), any(), argThat(p ->
                p.getPageSize() == expectedSize
        ))).willReturn(Mono.just(new PageImpl<>(List.of(), PageRequest.of(0, expectedSize), 0)));

        // when & then
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/songs/stats/album-counts")
                        .queryParam("size", requestedSize)
                        .build())
                .exchange()
                .expectStatus().isOk();
    }
}
