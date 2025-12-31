package com.seonghyeon.jukebox.service.like;

import com.seonghyeon.jukebox.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;

class LikeBatchWriterTest extends AbstractIntegrationTest {

    @Autowired
    private LikeBatchWriter likeBatchWriter;

    @Autowired
    private DatabaseClient databaseClient;

    @BeforeEach
    void setUp() {
        // 테스트용 노래 데이터 삽입 (ID: 1, 2)
        databaseClient.sql("DELETE FROM songs").fetch().rowsUpdated().block();
        databaseClient.sql("INSERT INTO songs (id, title, total_likes) VALUES (1, 'Song A', 10)").fetch().rowsUpdated().block();
        databaseClient.sql("INSERT INTO songs (id, title, total_likes) VALUES (2, 'Song B', 20)").fetch().rowsUpdated().block();
    }

    @Test
    @DisplayName("스냅샷에 담긴 차이(diff)만큼 DB의 좋아요 수가 정확히 업데이트되어야 한다")
    void updateLikeLegacySuccess() {
        // given: Map<Long, LongAdder> 대신 Map<Long, Long> 사용
        Map<Long, Long> snapshot = new HashMap<>();
        snapshot.put(1L, 5L);  // 10 + 5 = 15 예상
        snapshot.put(2L, -3L); // 20 - 3 = 17 예상

        // when
        likeBatchWriter.updateLike(snapshot)
                .as(StepVerifier::create)
                .verifyComplete();

        // then
        verifyTotalLikes(1L, 15L);
        verifyTotalLikes(2L, 17L);
    }

    @Test
    @DisplayName("차이(diff)가 0인 항목은 업데이트 쿼리를 실행하지 않아야 한다")
    void skipUpdateWhenDiffIsZero() {
        // given
        Map<Long, Long> snapshot = Map.of(1L, 0L);

        // when
        likeBatchWriter.updateLike(snapshot)
                .as(StepVerifier::create)
                .verifyComplete();

        // then: 초기값 10 유지
        verifyTotalLikes(1L, 10L);
    }

    @Test
    @DisplayName("존재하지 않는 노래 ID에 대해서는 에러 없이 진행한다")
    void handleNonExistentSong() {
        // given
        Map<Long, Long> snapshot = Map.of(999L, 1L);

        // when & then
        likeBatchWriter.updateLike(snapshot)
                .as(StepVerifier::create)
                .verifyComplete();
    }

    @Test
    @DisplayName("스냅샷이 비어있으면 즉시 종료된다")
    void emptySnapshotTest() {
        // when & then
        likeBatchWriter.updateLike(Map.of())
                .as(StepVerifier::create)
                .verifyComplete();
    }

    @Test
    @DisplayName("대량의 노래 데이터를 한꺼번에 업데이트해도 모든 값이 정확히 반영되어야 한다")
    void updateLargeAmountOfSongs() {
        databaseClient.sql("DELETE FROM songs").fetch().rowsUpdated().block();

        // given: 100개의 노래 데이터 준비
        int songCount = 100;
        Map<Long, Long> snapshot = new HashMap<>();

        Flux.range(1, songCount)
                .flatMap(i -> databaseClient.sql("INSERT INTO songs (id, title, total_likes) VALUES (:id, :title, 0)")
                        .bind("id", i)
                        .bind("title", "Song " + i)
                        .fetch().rowsUpdated())
                .blockLast();

        for (long i = 1; i <= songCount; i++) {
            snapshot.put(i, i); // 각 노래 ID만큼의 좋아요 증가량 설정
        }

        // when
        likeBatchWriter.updateLike(snapshot)
                .as(StepVerifier::create)
                .verifyComplete();

        // then
        verifyTotalLikes(100L, 100L);
        verifyTotalLikes(1L, 1L);
    }

    @Test
    @DisplayName("좋아요 취소로 인해 총 좋아요 수가 음수가 되는 경우 (성공 후 운영자 메뉴얼 처리)")
    void updateLikeResultNegative() {
        // given: 현재 좋아요 10개
        Map<Long, Long> snapshot = Map.of(1L, -20L);

        // when
        likeBatchWriter.updateLike(snapshot)
                .as(StepVerifier::create)
                .verifyComplete();

        // then
        verifyTotalLikes(1L, -10L);
    }

    private void verifyTotalLikes(Long songId, Long expectedLikes) {
        databaseClient.sql("SELECT total_likes FROM songs WHERE id = :id")
                .bind("id", songId)
                .map(row -> row.get("total_likes", Long.class))
                .one()
                .as(StepVerifier::create)
                .expectNext(expectedLikes)
                .verifyComplete();
    }
}
