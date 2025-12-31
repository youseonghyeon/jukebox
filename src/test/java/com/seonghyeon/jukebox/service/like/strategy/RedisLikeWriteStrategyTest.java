package com.seonghyeon.jukebox.service.like.strategy;

import com.seonghyeon.jukebox.AbstractIntegrationTest;
import com.seonghyeon.jukebox.service.like.LikeBatchWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RedisLikeWriteStrategyTest extends AbstractIntegrationTest {

    @Autowired
    private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @Autowired
    private TransactionalOperator transactionalOperator;

    @MockitoBean
    private LikeBatchWriter likeBatchWriter;

    private RedisLikeWriteStrategy strategy;

    private static final String REDIS_KEY = "jukebox:like:buffer";
    private static final String SNAPSHOT_KEY = "jukebox:like:buffer:snapshot";
    private static final String LOCK_KEY = "jukebox:like:lock";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // 1. Redis 초기화 (테스트 간 데이터 간섭 방지)
        reactiveRedisTemplate.execute(conn -> conn.serverCommands().flushAll()).blockLast();

        // 2. Writer Mocking
        when(likeBatchWriter.updateLike(any())).thenReturn(Mono.empty());

        // 3. 전략 객체 수동 생성 (Mock 주입을 위해)
        strategy = new RedisLikeWriteStrategy(
                reactiveRedisTemplate,
                likeBatchWriter::updateLike,
                transactionalOperator
        );
    }

    @Test
    @DisplayName("좋아요 추가 및 취소 요청이 Redis Hash에 정확히 누적된다")
    void addAndRemoveLike_ShouldAccumulateInRedis() {
        // given
        Long songId = 100L;

        // when & then
        StepVerifier.create(strategy.addLike(songId))
                .verifyComplete();

        StepVerifier.create(strategy.addLike(songId))
                .verifyComplete();

        StepVerifier.create(strategy.removeLike(songId))
                .verifyComplete();

        // verify: 1 + 1 - 1 = 1
        StepVerifier.create(reactiveRedisTemplate.opsForHash().get(REDIS_KEY, songId.toString()))
                .expectNext("1")
                .verifyComplete();
    }

    @Test
    @DisplayName("DB 플러시 성공 시: 버퍼 -> 스냅샷 -> DB Writer 호출 -> Redis 삭제 순으로 동작한다")
    void flushToDatabase_Success_ShouldClearRedis() {
        // given
        reactiveRedisTemplate.opsForHash().put(REDIS_KEY, "1", "10").block();
        reactiveRedisTemplate.opsForHash().put(REDIS_KEY, "2", "5").block();

        // when
        StepVerifier.create(strategy.flushToDatabase())
                .verifyComplete();

        // then
        // 1. Writer가 정확한 데이터로 호출되었는지 검증
        ArgumentCaptor<Map<Long, Long>> captor = ArgumentCaptor.forClass(Map.class);
        verify(likeBatchWriter, times(1)).updateLike(captor.capture());

        Map<Long, Long> capturedMap = captor.getValue();
        assertThat(capturedMap).hasSize(2);
        assertThat(capturedMap.get(1L)).isEqualTo(10L);
        assertThat(capturedMap.get(2L)).isEqualTo(5L);

        // 2. Redis의 모든 키(Buffer, Snapshot, Lock)가 깨끗하게 지워졌는지 검증
        StepVerifier.create(reactiveRedisTemplate.hasKey(REDIS_KEY)).expectNext(false).verifyComplete();
        StepVerifier.create(reactiveRedisTemplate.hasKey(SNAPSHOT_KEY)).expectNext(false).verifyComplete();
        StepVerifier.create(reactiveRedisTemplate.hasKey(LOCK_KEY)).expectNext(false).verifyComplete();
    }

    @Test
    @DisplayName("데이터 오염 방어: 숫자가 아닌 데이터는 로그를 남기고 스킵하며, 정상 데이터만 처리한다")
    void flushToDatabase_CorruptData_ShouldSkipAndContinue() {
        // given
        reactiveRedisTemplate.opsForHash().put(REDIS_KEY, "1", "100").block(); // 정상
        reactiveRedisTemplate.opsForHash().put(REDIS_KEY, "2", "NotANumber").block(); // 오염된 데이터
        reactiveRedisTemplate.opsForHash().put(REDIS_KEY, "InvalidKey", "50").block(); // 오염된 키

        // when
        StepVerifier.create(strategy.flushToDatabase())
                .verifyComplete();

        // then
        ArgumentCaptor<Map<Long, Long>> captor = ArgumentCaptor.forClass(Map.class);
        verify(likeBatchWriter, times(1)).updateLike(captor.capture());

        Map<Long, Long> capturedMap = captor.getValue();
        assertThat(capturedMap).hasSize(1); // 오염된 2건 제외, 정상 1건만 존재해야 함
        assertThat(capturedMap.get(1L)).isEqualTo(100L);

        // 정상 처리되었으므로 Redis는 비워져야 함
        StepVerifier.create(reactiveRedisTemplate.hasKey(SNAPSHOT_KEY)).expectNext(false).verifyComplete();
    }

    @Test
    @DisplayName("DB 실패 시: 트랜잭션이 롤백되고 스냅샷은 Redis에 안전하게 보존되어야 한다")
    void flushToDatabase_DbFailure_ShouldPreserveSnapshot() {
        // given
        reactiveRedisTemplate.opsForHash().put(REDIS_KEY, "1", "10").block();

        // Writer가 에러를 던지도록 설정
        when(likeBatchWriter.updateLike(any())).thenReturn(Mono.error(new RuntimeException("DB Connection Fail")));

        // when & then
        StepVerifier.create(strategy.flushToDatabase())
                .expectError(RuntimeException.class)
                .verify();

        // then
        // 1. 스냅샷 키가 지워지지 않고 남아있어야 함 (데이터 유실 방지 핵심)
        StepVerifier.create(reactiveRedisTemplate.hasKey(SNAPSHOT_KEY)).expectNext(true).verifyComplete();

        // 2. 락은 해제되어야 함 (데드락 방지)
        StepVerifier.create(reactiveRedisTemplate.hasKey(LOCK_KEY)).expectNext(false).verifyComplete();
    }

    @Test
    @DisplayName("분산 락: 이미 락이 존재하면 실행을 건너뛴다")
    void flushToDatabase_Locked_ShouldSkipExecution() {
        // given: 누군가 이미 락을 점유 중
        reactiveRedisTemplate.opsForValue().set(LOCK_KEY, "other-server", Duration.ofSeconds(60)).block();
        reactiveRedisTemplate.opsForHash().put(REDIS_KEY, "1", "10").block();

        // when
        StepVerifier.create(strategy.flushToDatabase())
                .verifyComplete();

        // then
        // 1. Writer는 호출되지 않아야 함
        verify(likeBatchWriter, never()).updateLike(any());

        // 2. 데이터도 그대로 남아있어야 함
        StepVerifier.create(reactiveRedisTemplate.opsForHash().hasKey(REDIS_KEY, "1")).expectNext(true).verifyComplete();
    }

    @Test
    @DisplayName("장애 복구: 이전에 처리하다 만 스냅샷이 있으면 그것부터 처리한다")
    void flushToDatabase_ResumeSnapshot_ShouldPrioritizeSnapshot() {
        // given
        // 1. 이전 스냅샷이 존재 (이번 텀에 먼저 처리되어야 함)
        reactiveRedisTemplate.opsForHash().put(SNAPSHOT_KEY, "999", "10").block();

        // 2. 새로운 버퍼도 존재 (이번 텀에는 처리되지 않아야 함)
        reactiveRedisTemplate.opsForHash().put(REDIS_KEY, "888", "20").block();

        // Mock: 정상 처리 가정
        when(likeBatchWriter.updateLike(any())).thenReturn(Mono.empty());

        // when
        StepVerifier.create(strategy.flushToDatabase())
                .verifyComplete();

        // then
        ArgumentCaptor<Map<Long, Long>> captor = ArgumentCaptor.forClass(Map.class);
        verify(likeBatchWriter, times(1)).updateLike(captor.capture());

        // 검증
        Map<Long, Long> capturedMap = captor.getValue();
        assertThat(capturedMap).hasSize(1);
        assertThat(capturedMap).containsKey(999L); // 이제 정상적으로 999L 키가 들어옵니다.
        assertThat(capturedMap).doesNotContainKey(888L); // 새로운 데이터는 건드리지 않음

        // 상태 검증
        StepVerifier.create(reactiveRedisTemplate.hasKey(SNAPSHOT_KEY)).expectNext(false).verifyComplete();
        StepVerifier.create(reactiveRedisTemplate.hasKey(REDIS_KEY)).expectNext(true).verifyComplete();
    }


    @Test
    @DisplayName("빈 Redis 플러시: 데이터가 없으면 Lock만 잡았다가 DB Writer 호출 없이 종료한다")
    void flushToDatabase_EmptyRedis_ShouldNoOp() {
        // given: Redis가 비어있는 상태

        // when
        StepVerifier.create(strategy.flushToDatabase())
                .verifyComplete();

        // then
        // 1. 데이터가 없으므로 Writer는 절대 호출되지 않아야 함
        verify(likeBatchWriter, never()).updateLike(any());

        // 2. 로직이 정상 종료되어 락은 해제되어야 함
        StepVerifier.create(reactiveRedisTemplate.hasKey(LOCK_KEY)).expectNext(false).verifyComplete();
    }

    @Test
    @DisplayName("모든 데이터가 오염된 경우: 빈 맵으로 DB를 호출하지 않도록 방어하고, 스냅샷은 삭제하여 무한 루프를 방지한다")
    void flushToDatabase_AllCorruptData_ShouldCleanUpWithoutDBCall() {
        // given: 유효하지 않은 데이터만 존재 (전부 필터링됨)
        reactiveRedisTemplate.opsForHash().put(REDIS_KEY, "invalid_key_1", "10").block();
        reactiveRedisTemplate.opsForHash().put(REDIS_KEY, "100", "invalid_value").block();

        // when
        StepVerifier.create(strategy.flushToDatabase())
                .verifyComplete();

        // then
        // 1. 필터링 후 남은 데이터가 0건이므로 Writer는 호출되지 않아야 함 (DB 에러 방지)
        verify(likeBatchWriter, never()).updateLike(any());

        // 2. **중요**: 처리는 못 했지만 '쓰레기 데이터'이므로 스냅샷은 삭제되어야 함.
        // (삭제되지 않으면 다음 스케줄마다 계속 읽고 버리는 과정을 무한 반복하게 됨)
        StepVerifier.create(reactiveRedisTemplate.hasKey(SNAPSHOT_KEY)).expectNext(false).verifyComplete();
    }

    @Test
    @DisplayName("배치 처리: 버퍼 사이즈(500)를 초과하는 대량 데이터는 나누어서 처리한다")
    void flushToDatabase_LargeData_ShouldProcessInBatches() {
        // given: 버퍼 사이즈(500)보다 많은 502개의 데이터 생성
        Map<String, String> largeData = new java.util.HashMap<>();
        for (int i = 0; i < 502; i++) {
            largeData.put(String.valueOf(i), "1");
        }
        reactiveRedisTemplate.opsForHash().putAll(REDIS_KEY, largeData).block();

        // when
        StepVerifier.create(strategy.flushToDatabase())
                .verifyComplete();

        // then
        // 1. 500개 단위로 끊어서 처리하므로 Writer가 총 2번 호출되어야 함
        ArgumentCaptor<Map<Long, Long>> captor = ArgumentCaptor.forClass(Map.class);
        verify(likeBatchWriter, times(2)).updateLike(captor.capture());

        // 2. 각 호출의 배치 사이즈 검증
        // (순서는 보장되지 않을 수 있으나, 하나는 500개, 하나는 2개여야 함)
        long countBatch500 = captor.getAllValues().stream().filter(map -> map.size() == 500).count();
        long countBatch2 = captor.getAllValues().stream().filter(map -> map.size() == 2).count();

        assertThat(countBatch500).isEqualTo(1);
        assertThat(countBatch2).isEqualTo(1);

        // 3. 처리 완료 후 Redis 정리 확인
        StepVerifier.create(reactiveRedisTemplate.hasKey(SNAPSHOT_KEY)).expectNext(false).verifyComplete();
    }

    @Test
    @DisplayName("동시성 격리: 스냅샷을 처리하는 도중에 들어온 새로운 버퍼 데이터는 건드리지 않고 안전하게 보존된다")
    void flushToDatabase_WhileProcessingSnapshot_NewBufferShouldBeSafe() {
        // given
        // 1. 이미 스냅샷이 만들어져서 처리 대기 중인 상태 (Key: 999)
        reactiveRedisTemplate.opsForHash().put(SNAPSHOT_KEY, "999", "10").block();

        // 2. 그 사이에 사용자가 좋아요를 눌러서 새로운 버퍼가 생성됨 (Key: 888)
        reactiveRedisTemplate.opsForHash().put(REDIS_KEY, "888", "5").block();

        // when: 플러시 실행
        StepVerifier.create(strategy.flushToDatabase())
                .verifyComplete();

        // then
        ArgumentCaptor<Map<Long, Long>> captor = ArgumentCaptor.forClass(Map.class);
        verify(likeBatchWriter, times(1)).updateLike(captor.capture());

        Map<Long, Long> processedData = captor.getValue();

        // 1. 스냅샷 데이터("999")는 처리되었어야 함
        assertThat(processedData).containsKey(999L);

        // 2. 새로운 버퍼 데이터("888")는 이번 배치에 포함되지 않았어야 함
        assertThat(processedData).doesNotContainKey(888L);

        // 3. Redis 상태 검증
        // 스냅샷 키는 삭제됨
        StepVerifier.create(reactiveRedisTemplate.hasKey(SNAPSHOT_KEY)).expectNext(false).verifyComplete();
        // **중요**: 새로운 버퍼 키는 다음 주기를 위해 그대로 살아있어야 함
        StepVerifier.create(reactiveRedisTemplate.hasKey(REDIS_KEY)).expectNext(true).verifyComplete();
    }

    @Test
    @DisplayName("복합 데이터 처리: 양수, 음수, 0, 오염된 데이터가 섞여 있을 때 유효한 변경분만 DB로 전송한다")
    void flushToDatabase_ComplexScenario_ShouldOnlyProcessValidChanges() {
        // given
        reactiveRedisTemplate.opsForHash().putAll(REDIS_KEY, Map.of(
                "1", "10",            // 정상 (양수)
                "2", "-5",            // 정상 (음수)
                "3", "0",             // 최적화 대상 (0 -> 필터링되어야 함)
                "4", "NotANumber",    // 오염된 데이터 (필터링되어야 함)
                "5", "100"            // 정상 (양수)
        )).block();

        // when
        StepVerifier.create(strategy.flushToDatabase())
                .verifyComplete();

        // then
        ArgumentCaptor<Map<Long, Long>> captor = ArgumentCaptor.forClass(Map.class);
        verify(likeBatchWriter, times(1)).updateLike(captor.capture());

        Map<Long, Long> result = captor.getValue();

        // 1. 결과 맵 사이즈 확인 (1, 2, 5번만 포함되어야 함)
        assertThat(result).hasSize(3);

        // 2. 포함된 데이터 확인
        assertThat(result.get(1L)).isEqualTo(10L);
        assertThat(result.get(2L)).isEqualTo(-5L);
        assertThat(result.get(5L)).isEqualTo(100L);

        // 3. 필터링된 데이터 확인
        assertThat(result).doesNotContainKey(3L); // 0이라서 제외됨
        assertThat(result).doesNotContainKey(4L); // 오염되어 제외됨

        // 4. Redis 정리 확인
        StepVerifier.create(reactiveRedisTemplate.hasKey(SNAPSHOT_KEY)).expectNext(false).verifyComplete();
    }
}
