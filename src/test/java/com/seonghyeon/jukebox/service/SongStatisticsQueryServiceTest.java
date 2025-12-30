package com.seonghyeon.jukebox.service;

import com.seonghyeon.jukebox.entity.SongStatisticsEntity;
import com.seonghyeon.jukebox.repository.SongStatisticsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SongStatisticsQueryServiceTest {

    @Mock
    private SongStatisticsRepository songStatisticsRepository;

    @InjectMocks
    private SongStatisticsQueryService songStatisticsQueryService;

    @Test
    @DisplayName("연도와 가수 필터가 모두 제공되었을 때 페이징 결과를 반환한다")
    void getStatsWithFullFilters() {
        // given
        Integer year = 2024;
        String artist = "King Gizzard";
        Pageable pageable = PageRequest.of(0, 20, Sort.by("releaseYear").descending());
        SongStatisticsEntity entity = new SongStatisticsEntity(1L, year, artist, 5L);

        given(songStatisticsRepository.countByYearAndArtist(year, artist)).willReturn(Mono.just(1L));
        given(songStatisticsRepository.findAllByYearAndArtist(eq(year), eq(artist), eq(pageable)))
                .willReturn(Flux.just(entity));

        // when & then
        songStatisticsQueryService.getAlbumStatsByYearAndArtist(year, artist, pageable)
                .as(StepVerifier::create)
                .assertNext(page -> {
                    assertThat(page.getTotalElements()).isEqualTo(1L);
                    assertThat(page.getContent()).hasSize(1);
                    assertThat(page.getContent().get(0).artist()).isEqualTo(artist);
                })
                .verifyComplete();

        verify(songStatisticsRepository).countByYearAndArtist(year, artist);
        verify(songStatisticsRepository).findAllByYearAndArtist(year, artist, pageable);
    }

    @Test
    @DisplayName("필터값이 모두 null(Nullable)일 때 전체 조회를 수행한다")
    void getStatsWithNullFilters() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        given(songStatisticsRepository.countByYearAndArtist(isNull(), isNull())).willReturn(Mono.just(100L));
        given(songStatisticsRepository.findAllByYearAndArtist(isNull(), isNull(), eq(pageable)))
                .willReturn(Flux.empty());

        // when & then
        songStatisticsQueryService.getAlbumStatsByYearAndArtist(null, null, pageable)
                .as(StepVerifier::create)
                .assertNext(page -> {
                    assertThat(page.getTotalElements()).isEqualTo(100L);
                    assertThat(page.getContent()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("데이터가 없는 경우에도 에러 없이 빈 Page 객체를 반환한다")
    void getStatsWhenEmpty() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        given(songStatisticsRepository.countByYearAndArtist(any(), any())).willReturn(Mono.just(0L));
        given(songStatisticsRepository.findAllByYearAndArtist(any(), any(), any())).willReturn(Flux.empty());

        // when & then
        songStatisticsQueryService.getAlbumStatsByYearAndArtist(2025, "Unknown", pageable)
                .as(StepVerifier::create)
                .assertNext(page -> {
                    assertThat(page.getTotalElements()).isZero();
                    assertThat(page.getContent()).isEmpty();
                    assertThat(page.getTotalPages()).isZero();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("리포지토리 호출 중 에러가 발생하면 Mono.error를 전파한다")
    void getStatsWhenRepositoryFails() {
        // given
        // 1. findAll... 메서드가 null을 반환하지 않도록 빈 Flux라도 반환하게 설정
        given(songStatisticsRepository.findAllByYearAndArtist(any(), any(), any()))
                .willReturn(Flux.empty());

        // 2. count... 메서드에서 실제 에러 발생 설정
        given(songStatisticsRepository.countByYearAndArtist(any(), any()))
                .willReturn(Mono.error(new RuntimeException("Database Connection Error")));

        // when & then
        songStatisticsQueryService.getAlbumStatsByYearAndArtist(2024, "Artist", PageRequest.of(0, 20))
                .as(StepVerifier::create)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    @DisplayName("사용자가 지정한 정렬 조건이 결과 Page 객체에도 유지되어야 한다")
    void getStatsPreservesSort() {
        // given
        Sort customSort = Sort.by("albumCount").ascending();
        Pageable pageable = PageRequest.of(0, 10, customSort);

        given(songStatisticsRepository.countByYearAndArtist(any(), any())).willReturn(Mono.just(5L));
        given(songStatisticsRepository.findAllByYearAndArtist(any(), any(), any())).willReturn(Flux.empty());

        // when & then
        songStatisticsQueryService.getAlbumStatsByYearAndArtist(null, null, pageable)
                .as(StepVerifier::create)
                .assertNext(page -> {
                    assertThat(page.getSort()).isEqualTo(customSort);
                    assertThat(page.getPageable().getSort().getOrderFor("albumCount").getDirection())
                            .isEqualTo(Sort.Direction.ASC);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("목록 조회 리포지토리에서 에러가 발생하면 Mono.error를 전파한다")
    void getStatsWhenFindAllFails() {
        // given
        given(songStatisticsRepository.findAllByYearAndArtist(any(), any(), any()))
                .willReturn(Flux.error(new RuntimeException("FindAll Error")));

        // count는 정상 반환되더라도 결과는 에러여야 함
        given(songStatisticsRepository.countByYearAndArtist(any(), any()))
                .willReturn(Mono.just(10L));

        // when & then
        songStatisticsQueryService.getAlbumStatsByYearAndArtist(null, null, PageRequest.of(0, 10))
                .as(StepVerifier::create)
                .expectError(RuntimeException.class)
                .verify();
    }
}
