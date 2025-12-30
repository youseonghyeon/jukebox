package com.seonghyeon.jukebox.controller;

import com.seonghyeon.jukebox.common.exception.like.AlreadyLikedException;
import com.seonghyeon.jukebox.common.exception.like.NotLikedException;
import com.seonghyeon.jukebox.common.exception.like.SongNotFoundException;
import com.seonghyeon.jukebox.controller.dto.request.LikeRequest;
import com.seonghyeon.jukebox.controller.dto.response.TopLikedResponse;
import com.seonghyeon.jukebox.entity.SongStatisticsEntity;
import com.seonghyeon.jukebox.entity.like.Action;
import com.seonghyeon.jukebox.repository.dto.SongLikeCountDto;
import com.seonghyeon.jukebox.service.SongStatisticsQueryService;
import com.seonghyeon.jukebox.service.like.SongLikeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@WebFluxTest(SongController.class) // 특정 컨트롤러만 로드
class SongControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private SongStatisticsQueryService songStatisticsQueryService;

    @MockitoBean
    private SongLikeService songLikeService;

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

    // --- 좋아요 처리 (POST /{songId}/likes) 테스트 ---

    @Test
    @DisplayName("성공적으로 곡에 좋아요를 표시한다")
    void likeSongSuccess() {
        // given
        Long songId = 1L;
        LikeRequest request = new LikeRequest(100L, Action.LIKE);

        given(songLikeService.likeSong(eq(songId), eq(request.userId())))
                .willReturn(Mono.empty());

        // when & then
        webTestClient.post()
                .uri("/api/v1/songs/{songId}/likes", songId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("이미 좋아요를 누른 곡이면 409 Conflict 에러를 반환한다")
    void likeSongFailAlreadyLiked() {
        // given
        Long songId = 1L;
        LikeRequest request = new LikeRequest(100L, Action.LIKE);
        String errorMsg = "The song is already liked.";

        given(songLikeService.likeSong(anyLong(), anyLong()))
                .willReturn(Mono.error(new AlreadyLikedException(errorMsg)));

        // when & then
        webTestClient.post()
                .uri("/api/v1/songs/{songId}/likes", songId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.code").isEqualTo("ALREADY_LIKED")
                .jsonPath("$.message").isEqualTo(errorMsg);
    }

    @Test
    @DisplayName("존재하지 않는 곡에 좋아요를 하면 404 에러를 반환한다")
    void likeSongFailNotFound() {
        // given
        Long songId = 999L;
        LikeRequest request = new LikeRequest(100L, Action.LIKE);

        given(songLikeService.likeSong(anyLong(), anyLong()))
                .willReturn(Mono.error(new SongNotFoundException("Song not found")));

        // when & then
        webTestClient.post()
                .uri("/api/v1/songs/{songId}/likes", songId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("성공적으로 곡의 좋아요를 취소한다")
    void unlikeSongSuccess() {
        // given
        Long songId = 1L;
        LikeRequest request = new LikeRequest(100L, Action.UNLIKE);

        given(songLikeService.unlikeSong(eq(songId), eq(request.userId())))
                .willReturn(Mono.empty());

        // when & then
        webTestClient.post()
                .uri("/api/v1/songs/{songId}/likes", songId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk();
    }

    // --- 인기 곡 조회 (GET /top-liked) 테스트 ---

    @Test
    @DisplayName("최근 1시간 내 최다 좋아요 곡 10개를 조회한다")
    void getTopLikedSongsSuccess() {
        // given
        SongLikeCountDto dto1 = new SongLikeCountDto(1L, 50L);
        SongLikeCountDto dto2 = new SongLikeCountDto(2L, 30L);

        given(songLikeService.getTopLikedSongs(any(Duration.class), anyInt()))
                .willReturn(Flux.just(dto1, dto2));

        // when & then
        webTestClient.get()
                .uri("/api/v1/songs/top-liked")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(TopLikedResponse.class)
                .hasSize(2)
                .consumeWith(result -> {
                    List<TopLikedResponse> responses = result.getResponseBody();
                    assert responses != null;
                    org.assertj.core.api.Assertions.assertThat(responses.get(0).songId()).isEqualTo(1L);
                    org.assertj.core.api.Assertions.assertThat(responses.get(0).likeCount()).isEqualTo(50L);
                });
    }

    @Test
    @DisplayName("좋아요를 하지 않은 곡에 대해 취소를 요청하면 409 Conflict 에러를 반환한다")
    void unlikeSongFailNotLiked() {
        // given
        Long songId = 1L;
        LikeRequest request = new LikeRequest(100L, Action.UNLIKE);
        String errorMsg = "No active like found for song to remove.";

        given(songLikeService.unlikeSong(anyLong(), anyLong()))
                .willReturn(Mono.error(new NotLikedException(errorMsg)));

        // when & then
        webTestClient.post()
                .uri("/api/v1/songs/{songId}/likes", songId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.code").isEqualTo("NOT_LIKED") // GlobalHandler에 설정한 코드
                .jsonPath("$.message").isEqualTo(errorMsg);
    }

    @Test
    @DisplayName("필수 파라미터(userId)가 누락된 좋아요 요청은 400 에러를 반환한다")
    void likeRequestValidationFail() {
        // given: userId가 null인 객체 생성
        LikeRequest invalidRequest = new LikeRequest(null, Action.LIKE);

        // when & then
        webTestClient.post()
                .uri("/api/v1/songs/1/likes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest) // String이 아닌 객체를 직접 전달
                .exchange()
                .expectStatus().isBadRequest(); // 이제 정상적으로 400 에러 포착
    }

    @Test
    @DisplayName("최근 1시간 내 좋아요 데이터가 없으면 빈 목록을 반환한다")
    void getTopLikedSongsEmpty() {
        // given
        given(songLikeService.getTopLikedSongs(any(Duration.class), anyInt()))
                .willReturn(Flux.empty());

        // when & then
        webTestClient.get()
                .uri("/api/v1/songs/top-liked")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()").isEqualTo(0);
    }

    @Test
    @DisplayName("지원하지 않는 액션 값이 들어오면 400 에러를 반환한다")
    void likeRequestInvalidAction() {
        // given: Action enum에 존재하지 않는 문자열을 포함한 JSON
        String invalidJson = "{\"userId\": 100, \"action\": \"HATE\"}";

        // when & then
        webTestClient.post()
                .uri("/api/v1/songs/1/likes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidJson) // 객체 대신 문자열로 잘못된 값을 직접 전달
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("BAD_REQUEST");
    }
}
