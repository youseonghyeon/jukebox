package com.seonghyeon.jukebox.repository.impl;


import com.seonghyeon.jukebox.AbstractIntegrationTest;
import com.seonghyeon.jukebox.entity.SongStatisticsEntity;
import com.seonghyeon.jukebox.repository.SongStatisticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

class SongStatisticsCustomRepositoryImplTest extends AbstractIntegrationTest {

    @Autowired
    private SongStatisticsRepository songStatisticsRepository;

    @Autowired
    private R2dbcEntityTemplate r2dbcEntityTemplate;

    @BeforeEach
    void setup() {
        r2dbcEntityTemplate.getDatabaseClient().sql("DELETE FROM song_statistics").fetch().rowsUpdated().block();

        // 테스트 데이터 삽입
        List<SongStatisticsEntity> testData = List.of(
                new SongStatisticsEntity(null, 2024, "NewJeans", 2L),
                new SongStatisticsEntity(null, 2024, "IVE", 3L),
                new SongStatisticsEntity(null, 2023, "NewJeans", 5L),
                new SongStatisticsEntity(null, 2023, "Aespa", 1L)
        );

        Flux.fromIterable(testData)
                .flatMap(r2dbcEntityTemplate::insert)
                .blockLast();
    }

    @Test
    @DisplayName("연도 필터가 정상적으로 작동해야 한다")
    void filterByYear() {
        songStatisticsRepository.findAllByYearAndArtist(2024, null, PageRequest.of(0, 10))
                .as(StepVerifier::create)
                .expectNextCount(2) // 2024년 데이터는 NewJeans, IVE 총 2개
                .verifyComplete();
    }

    @Test
    @DisplayName("가수 필터가 정상적으로 작동해야 한다")
    void filterByArtist() {
        songStatisticsRepository.findAllByYearAndArtist(null, "NewJeans", PageRequest.of(0, 10))
                .as(StepVerifier::create)
                .expectNextCount(2) // NewJeans 데이터는 2023, 2024 총 2개
                .verifyComplete();
    }

    @Test
    @DisplayName("정렬 화이트리스트가 작동하여 허용되지 않은 필드로 요청 시 기본 정렬이 적용되어야 한다")
    void validateSortWhitelist() {
        // given: 허용되지 않은 필드 'id'로 정렬 요청
        Pageable invalidPageable = PageRequest.of(0, 10, Sort.by("id").ascending());

        // when & then: 에러 없이 실행되며, 기본 정렬(releaseYear DESC)에 의해 2024년 데이터가 먼저 나와야 함
        songStatisticsRepository.findAllByYearAndArtist(null, null, invalidPageable)
                .map(SongStatisticsEntity::releaseYear)
                .as(StepVerifier::create)
                .expectNext(2024, 2024, 2023, 2023) // 기본 정렬 DESC 적용 확인
                .verifyComplete();
    }

    @Test
    @DisplayName("조건에 맞는 데이터의 총 개수를 정확히 반환해야 한다")
    void countWithFilters() {
        songStatisticsRepository.countByYearAndArtist(2024, "NewJeans")
                .as(StepVerifier::create)
                .expectNext(1L)
                .verifyComplete();
    }
}
