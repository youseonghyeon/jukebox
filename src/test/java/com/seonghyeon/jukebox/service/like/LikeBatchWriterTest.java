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
import java.util.concurrent.atomic.LongAdder;

class LikeBatchWriterTest extends AbstractIntegrationTest {

    @Autowired
    private LikeBatchWriter likeBatchWriter;

    @Autowired
    private DatabaseClient databaseClient;

    @BeforeEach
    void setUp() {
        // 테스트용 노래 데이터 삽입 (ID: 1, 2, 3)
        // 기존 데이터를 지우고 새로 세팅하여 테스트 독립성 보장
        databaseClient.sql("DELETE FROM songs").fetch().rowsUpdated().block();
        databaseClient.sql("INSERT INTO songs (id, title, total_likes) VALUES (1, 'Song A', 10)").fetch().rowsUpdated().block();
        databaseClient.sql("INSERT INTO songs (id, title, total_likes) VALUES (2, 'Song B', 20)").fetch().rowsUpdated().block();
    }

    @Test
    @DisplayName("스냅샷에 담긴 차이(diff)만큼 DB의 좋아요 수가 정확히 업데이트되어야 한다")
    void updateLikeSuccess() {
        // given
        Map<Long, LongAdder> snapshot = new HashMap<>();

        LongAdder diff1 = new LongAdder();
        diff1.add(5); // 10 + 5 = 15 예상
        snapshot.put(1L, diff1);

        LongAdder diff2 = new LongAdder();
        diff2.add(-3); // 20 - 3 = 17 예상
        snapshot.put(2L, diff2);

        // when
        likeBatchWriter.updateLike(snapshot)
                .as(StepVerifier::create)
                .verifyComplete();

        // then: DB 값 검증
        verifyTotalLikes(1L, 15L);
        verifyTotalLikes(2L, 17L);
    }

    @Test
    @DisplayName("차이(diff)가 0인 항목은 업데이트 쿼리를 실행하지 않아야 한다")
    void skipUpdateWhenDiffIsZero() {
        // given
        Map<Long, LongAdder> snapshot = new HashMap<>();
        LongAdder zeroDiff = new LongAdder(); // 0
        snapshot.put(1L, zeroDiff);

        // when
        likeBatchWriter.updateLike(snapshot)
                .as(StepVerifier::create)
                .verifyComplete();

        // then: 값이 변하지 않아야 함 (초기값 10 유지)
        verifyTotalLikes(1L, 10L);
    }

    @Test
    @DisplayName("존재하지 않는 노래 ID에 대해서는 경고 로그를 남기고 다음으로 진행한다")
    void handleNonExistentSong() {
        // given
        Map<Long, LongAdder> snapshot = new HashMap<>();
        LongAdder diff = new LongAdder();
        diff.increment();
        snapshot.put(999L, diff); // 존재하지 않는 ID

        // when & then: 에러 없이 완료되어야 함
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
        // 1. 기존 setUp에서 들어간 데이터와 충돌 방지를 위해 테이블 초기화
        databaseClient.sql("DELETE FROM songs").fetch().rowsUpdated().block();

        // given: 100개의 노래 데이터 준비
        int songCount = 100;
        Map<Long, LongAdder> snapshot = new HashMap<>();

        // 2. 여러 건의 데이터를 삽입할 때는 Flux를 활용하는 것이 리액티브 방식에 더 가깝습니다.
        Flux.range(1, songCount)
                .flatMap(i -> databaseClient.sql("INSERT INTO songs (id, title, total_likes) VALUES (:id, :title, 0)")
                        .bind("id", i)
                        .bind("title", "Song " + i)
                        .fetch().rowsUpdated())
                .blockLast(); // 모든 데이터 삽입이 완료될 때까지 대기

        for (long i = 1; i <= songCount; i++) {
            LongAdder adder = new LongAdder();
            adder.add(i);
            snapshot.put(i, adder);
        }

        // when
        likeBatchWriter.updateLike(snapshot)
                .as(StepVerifier::create)
                .verifyComplete();

        // then: 마지막 100번 노래가 100개의 좋아요를 가졌는지 확인
        verifyTotalLikes(100L, 100L);
        verifyTotalLikes(1L, 1L);
    }

    // DB 값을 조회하여 검증하는 헬퍼 메서드
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
