package com.seonghyeon.jukebox.repository;

import com.seonghyeon.jukebox.AbstractIntegrationTest;
import com.seonghyeon.jukebox.entity.SongLikeEntity;
import com.seonghyeon.jukebox.entity.like.Action;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SongLikeRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private SongLikeRepository songLikeRepository;

    @BeforeEach
    void cleanUp() {
        // 테스트 간 데이터 격리를 위해 초기화
        songLikeRepository.deleteAll().block();
    }

    @Test
    @DisplayName("특정 사용자의 곡에 대한 좋아요 합계 상태를 정확히 계산한다")
    void countUserLikeStatusTest() {
        // given: 한 사용자가 같은 곡에 대해 LIKE -> UNLIKE -> LIKE 순서로 기록을 남겼다고 가정
        Long songId = 1L;
        Long userId = 100L;

        List<SongLikeEntity> logs = List.of(
                SongLikeEntity.of(songId, userId, Action.LIKE),
                SongLikeEntity.of(songId, userId, Action.UNLIKE),
                SongLikeEntity.of(songId, userId, Action.LIKE)
        );

        // when & then
        songLikeRepository.saveAll(logs)
                .then(songLikeRepository.countUserLikeStatus(songId, userId))
                .as(StepVerifier::create)
                .expectNext(1) // (1 - 1 + 1) = 1
                .verifyComplete();
    }

    @Test
    @DisplayName("좋아요 기록이 없는 경우 countUserLikeStatus는 0을 반환한다")
    void countUserLikeStatusEmptyTest() {
        // when & then
        songLikeRepository.countUserLikeStatus(999L, 999L)
                .as(StepVerifier::create)
                .expectNext(0) // COALESCE 처리에 의해 0 반환
                .verifyComplete();
    }

    @Test
    @DisplayName("시간 범위 내에서 순증가(LIKE > UNLIKE)가 발생한 곡만 내림차순으로 조회하며, 0개인 곡은 제외한다")
    void findTopLikedSongsExcludeZeroTest() {
        // given
        LocalDateTime since = LocalDateTime.now().minusHours(1);

        // 데이터 구성:
        // 1번 곡: LIKE 2 -> +2
        // 2번 곡: LIKE 1 -> +1
        // 3번 곡: LIKE 1, UNLIKE 1 -> 0 (결과에서 제외되어야 함)
        // 4번 곡: UNLIKE 1 -> -1 (결과에서 제외되어야 함)
        List<SongLikeEntity> logs = List.of(
                SongLikeEntity.of(1L, 101L, Action.LIKE),
                SongLikeEntity.of(1L, 102L, Action.LIKE),
                SongLikeEntity.of(2L, 103L, Action.LIKE),
                SongLikeEntity.of(3L, 104L, Action.LIKE),
                SongLikeEntity.of(3L, 105L, Action.UNLIKE),
                SongLikeEntity.of(4L, 106L, Action.UNLIKE)
        );

        // when & then
        songLikeRepository.saveAll(logs)
                .thenMany(songLikeRepository.findTopLikedSongs(since, 10))
                .as(StepVerifier::create)
                // 1순위: 1번 곡 (2개)
                .assertNext(dto -> {
                    assertThat(dto.songId()).isEqualTo(1L);
                    assertThat(dto.likeCount()).isEqualTo(2);
                })
                // 2순위: 2번 곡 (1개)
                .assertNext(dto -> {
                    assertThat(dto.songId()).isEqualTo(2L);
                    assertThat(dto.likeCount()).isEqualTo(1);
                })
                // 3번 곡(0개)과 4번 곡(-1개)은 HAVING like_count > 0 조건에 의해 반환되지 않아야 함
                .verifyComplete();
    }
}
