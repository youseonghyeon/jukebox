package com.seonghyeon.jukebox.service.like.strategy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.testcontainers.shaded.com.fasterxml.jackson.core.type.TypeReference;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemoryLikeWriteStrategyTest {

    @Mock
    private Function<Map<Long, Long>, Mono<Void>> likeBatchWriter;

    @Mock
    private TransactionalOperator transactionalOperator;

    private MemoryLikeWriteStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new MemoryLikeWriteStrategy(likeBatchWriter, transactionalOperator);

        strategy.setBackupPath(tempDir.toString());

        // lenient()를 추가하여 모든 테스트에서 사용되지 않더라도 예외를 발생시키지 않음
        lenient().when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() throws IOException {
        Path outboxPath = Paths.get("outbox");
        if (Files.exists(outboxPath)) {
            try (var files = Files.walk(outboxPath)) {
                files.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
    }

    @Test
    @DisplayName("좋아요와 좋아요 취소가 버퍼에 정상적으로 누적된다")
    void accumulateLikesInBuffer() {
        // given
        Long songId = 1L;

        // when
        strategy.addLike(songId).block();
        strategy.addLike(songId).block();
        strategy.removeLike(songId).block();

        // then: flushToDatabase를 통해 스냅샷 내부 데이터 검증
        given(likeBatchWriter.apply(any())).willReturn(Mono.empty());

        strategy.flushToDatabase().block();

        // batchWriter에 전달된 Map의 값 확인 (1 + 1 - 1 = 1)
        verify(likeBatchWriter).apply(argThat(map -> {
            assertThat(map.get(songId)).isEqualTo(1L);
            return true;
        }));
    }

    @Test
    @DisplayName("flushToDatabase 호출 시 버퍼가 비워지고 새로운 스냅샷이 생성된다")
    void flushSwapsBuffer() {
        // given
        strategy.addLike(1L).block();
        given(likeBatchWriter.apply(any())).willReturn(Mono.empty());

        // when (첫 번째 flush)
        strategy.flushToDatabase().block();

        // then (두 번째 flush는 비어있어야 함)
        strategy.flushToDatabase().as(StepVerifier::create)
                .verifyComplete();

        // batchWriter는 데이터가 있던 첫 번째 호출에서만 실행됨
        verify(likeBatchWriter, times(1)).apply(any());
    }

    @Test
    @DisplayName("버퍼가 비어있을 경우 배치가 실행되지 않고 종료된다")
    void flushEmptyBuffer() {
        // when & then
        strategy.flushToDatabase().as(StepVerifier::create)
                .verifyComplete();

        verifyNoInteractions(likeBatchWriter);
    }

    @Test
    @DisplayName("배치 업데이트 중 에러 발생 시 트랜잭션 에러를 전파한다")
    void flushErrorPropagation() {
        // given
        strategy.addLike(1L).block();
        given(likeBatchWriter.apply(any())).willReturn(Mono.error(new RuntimeException("DB Error")));

        // when & then
        strategy.flushToDatabase().as(StepVerifier::create)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    @DisplayName("수천 개의 동시 요청이 발생해도 데이터 유실 없이 정확히 합산된다")
    void concurrentLikeRequestTest() throws InterruptedException {
        // given
        int threadCount = 1000;
        Long songId = 1L;
        given(likeBatchWriter.apply(any())).willReturn(Mono.empty());

        // 멀티스레드 실행을 위한 ExecutorService
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when: 1000개의 스레드가 동시에 좋아요 클릭
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    strategy.addLike(songId).block();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(); // 모든 스레드가 끝날 때까지 대기

        // then: flush 후 합계가 1000인지 확인
        strategy.flushToDatabase().block();

        verify(likeBatchWriter).apply(argThat(map -> {
            assertThat(map.get(songId)).isEqualTo((long) threadCount);
            return true;
        }));

        executorService.shutdown();
    }

    @Test
    @DisplayName("DB Flush 실패 시 onDestroy를 통해 데이터를 파일로 백업한다")
    void backupToFileOnFlushError() {
        // given
        strategy.addLike(99L).block();
        given(likeBatchWriter.apply(any())).willReturn(Mono.error(new RuntimeException("DB Error")));

        // when
        try {
            strategy.onDestroy(); // onDestroy 내부에서 flushToDatabase를 호출하고 실패 시 백업함
        } catch (Exception ignored) {
        }

        // then
        File[] files = tempDir.toFile().listFiles();
        assertThat(files).isNotNull().isNotEmpty();
    }

    /**
     * 실제 테스트 순서: 서버 기동 -> 좋아요 추가(DB 반영 안함) -> DB 종료 -> 서버 종료 -> onDestroy 호출 -> flushToDatabase 실패 -> 임시 디렉토리에 백업 파일 생성
     */
    @Test
    @DisplayName("종료 시 DB Flush 실패하면 임시 디렉토리에 백업하고 데이터 정합성을 확인한다")
    void backupOnDestroyFailure() throws IOException {
        // given
        Long songId = 50L;
        Long likeCount = 1L;
        strategy.addLike(songId).block();

        // onDestroy 내의 flushToDatabase가 실패하도록 설정
        given(likeBatchWriter.apply(any())).willReturn(Mono.error(new RuntimeException("Shutdown DB Error")));

        // when
        strategy.onDestroy();

        // then 1: 임시 디렉토리에 백업 파일이 생성되었는지 확인
        File[] files = tempDir.toFile().listFiles((dir, name) -> name.startsWith("backup-"));
        assertThat(files).isNotNull().hasSize(1);

        File backupFile = files[0];

        // then 2: 파일 내용(데이터 정합성) 검증
        // ObjectMapper를 사용하여 파일 내용을 Map으로 읽어옴
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Integer> backupData = objectMapper.readValue(backupFile, new TypeReference<>() {});

        // 저장했던 songId와 count가 일치하는지 확인
        // JSON Key는 기본적으로 String으로 저장되므로 "50"으로 체크
        assertThat(backupData).containsKey(String.valueOf(songId));
        assertThat(backupData.get(String.valueOf(songId))).isEqualTo(likeCount.intValue());
    }
}
