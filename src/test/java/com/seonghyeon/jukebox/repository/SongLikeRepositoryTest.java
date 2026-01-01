package com.seonghyeon.jukebox.repository;

import com.seonghyeon.jukebox.AbstractIntegrationTest;
import com.seonghyeon.jukebox.entity.SongLikeEntity;
import com.seonghyeon.jukebox.entity.like.Action;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SongLikeRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private SongLikeRepository songLikeRepository;

    @Autowired
    private SongRepository songRepository;

    @Autowired
    private DatabaseClient databaseClient;

    @BeforeEach
    void cleanUp() {
        songLikeRepository.deleteAll()
                .then(songRepository.deleteAll())
                .block();
    }

    /**
     * 직접 SQL을 실행하여 부모 노래 데이터를 삽입합니다.
     */
    private Mono<Void> insertSong(Long id, String title, String artist) {
        return databaseClient.sql("""
                        INSERT INTO songs (id, title, artist, album, total_likes)
                        VALUES (:id, :title, :artist, 'Test Album', 0)
                        """)
                .bind("id", id)
                .bind("title", title)
                .bind("artist", artist)
                .fetch()
                .rowsUpdated()
                .then();
    }

    @Test
    @DisplayName("특정 사용자의 곡에 대한 좋아요 합계 상태를 정확히 계산한다")
    void countUserLikeStatusTest() {
        // given
        Long songId = 1L;
        Long userId = 100L;

        List<SongLikeEntity> logs = List.of(
                SongLikeEntity.of(songId, userId, Action.LIKE),
                SongLikeEntity.of(songId, userId, Action.UNLIKE),
                SongLikeEntity.of(songId, userId, Action.LIKE)
        );

        // when & then
        insertSong(songId, "Title", "Artist") // 각 테스트 내부에서 데이터 삽입
                .thenMany(songLikeRepository.saveAll(logs))
                .then(songLikeRepository.countUserLikeStatus(songId, userId))
                .as(StepVerifier::create)
                .expectNext(1) // DB SUM 결과에 맞춰 Long 타입(1L) 지정
                .verifyComplete();
    }

    @Test
    @DisplayName("좋아요 기록이 없는 경우 countUserLikeStatus는 0을 반환한다")
    void countUserLikeStatusEmptyTest() {
        // given
        Long songId = 999L;

        // when & then
        insertSong(songId, "Empty", "Artist")
                .then(songLikeRepository.countUserLikeStatus(songId, 999L))
                .as(StepVerifier::create)
                .expectNext(0) // COALESCE 처리에 의해 0L 반환
                .verifyComplete();
    }

    @Test
    @DisplayName("시간 범위 내에서 순증가(LIKE > UNLIKE)가 발생한 곡만 내림차순으로 조회하며, 0개인 곡은 제외한다")
    void findTopLikedSongsExcludeZeroTest() {
        // given
        LocalDateTime since = LocalDateTime.now().minusHours(1);

        // 부모 곡 데이터 4건 삽입 시퀀스 생성
        Mono<Void> insertParentSongs = Mono.when(
                insertSong(1L, "T1", "A1"),
                insertSong(2L, "T2", "A2"),
                insertSong(3L, "T3", "A3"),
                insertSong(4L, "T4", "A4")
        );

        List<SongLikeEntity> logs = List.of(
                SongLikeEntity.of(1L, 101L, Action.LIKE),
                SongLikeEntity.of(1L, 102L, Action.LIKE),
                SongLikeEntity.of(2L, 103L, Action.LIKE),
                SongLikeEntity.of(3L, 104L, Action.LIKE),
                SongLikeEntity.of(3L, 105L, Action.UNLIKE),
                SongLikeEntity.of(4L, 106L, Action.UNLIKE)
        );

        // when & then
        insertParentSongs
                .thenMany(songLikeRepository.saveAll(logs))
                .thenMany(songLikeRepository.findTopLikedSongs(since, 10))
                .as(StepVerifier::create)
                .assertNext(dto -> {
                    assertThat(dto.songId()).isEqualTo(1L);
                    assertThat(dto.likeCount()).isEqualTo(2L);
                })
                .assertNext(dto -> {
                    assertThat(dto.songId()).isEqualTo(2L);
                    assertThat(dto.likeCount()).isEqualTo(1L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("최근 1시간 내의 데이터만 집계에 포함하고, 그 이전 데이터는 제외한다")
    void findTopLikedSongsTimeFilterTest() {
        // given
        Long songId = 10L;
        // 1시간 전 기준점 설정
        LocalDateTime since = LocalDateTime.now().minusHours(1);

        // 부모 곡 데이터 삽입
        Mono<Void> setupParent = insertSong(songId, "Time Filter Title", "Artist");

        // 데이터 구성:
        // 1. 30분 전 좋아요 (집계 포함 대상)
        // 2. 2시간 전 좋아요 (집계 제외 대상)
        List<SongLikeEntity> logs = List.of(
                SongLikeEntity.of(songId, 201L, Action.LIKE, LocalDateTime.now().minusMinutes(30)),
                SongLikeEntity.of(songId, 202L, Action.LIKE, LocalDateTime.now().minusHours(2))
        );

        // when & then
        setupParent
                .thenMany(songLikeRepository.saveAll(logs))
                .thenMany(songLikeRepository.findTopLikedSongs(since, 10))
                .as(StepVerifier::create)
                .assertNext(dto -> {
                    assertThat(dto.songId()).isEqualTo(songId);
                    // 2시간 전 데이터는 제외되어 likeCount가 1이어야 함
                    assertThat(dto.likeCount()).isEqualTo(1L);
                })
                .verifyComplete();
    }
}
