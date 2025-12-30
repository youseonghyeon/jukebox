package com.seonghyeon.jukebox.service.like;

import com.seonghyeon.jukebox.common.exception.like.AlreadyLikedException;
import com.seonghyeon.jukebox.common.exception.like.NotLikedException;
import com.seonghyeon.jukebox.common.exception.like.SongNotFoundException;
import com.seonghyeon.jukebox.entity.SongLikeEntity;
import com.seonghyeon.jukebox.entity.like.Action;
import com.seonghyeon.jukebox.repository.SongLikeRepository;
import com.seonghyeon.jukebox.repository.SongRepository;
import com.seonghyeon.jukebox.service.like.strategy.LikeWriteStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SongLikeServiceTest {

    @Mock
    private SongLikeRepository songLikeRepository;
    @Mock
    private SongRepository songRepository;
    @Mock
    private LikeWriteStrategy likeWriteStrategy;

    private SongLikeService songLikeService;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2025-12-30T10:00:00Z"), ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        songLikeService = new SongLikeService(likeWriteStrategy, songLikeRepository, songRepository, fixedClock);
    }

    @Test
    @DisplayName("정해진 시간 범위(window)를 기준으로 인기 곡을 조회한다")
    void getTopLikedSongs_withFixedClock() {
        // given
        Duration window = Duration.ofHours(1);
        int limit = 10;
        // 고정된 시간 10시에서 1시간 전인 9시가 기준(since)이 되어야 함
        LocalDateTime expectedSince = LocalDateTime.now(fixedClock).minus(window);

        given(songLikeRepository.findTopLikedSongs(eq(expectedSince), eq(limit)))
                .willReturn(Flux.empty());

        // when
        songLikeService.getTopLikedSongs(window, limit).subscribe();

        // then
        verify(songLikeRepository).findTopLikedSongs(expectedSince, limit);
    }

    @Test
    @DisplayName("좋아요 실행 시 곡 확인, 유저 상태 확인, 저장, 전략 실행 순으로 진행된다")
    void likeSong_executionOrder() {
        // given
        Long songId = 1L;
        Long userId = 100L;
        given(songRepository.existsById(songId)).willReturn(Mono.just(true));
        given(songLikeRepository.countUserLikeStatus(songId, userId)).willReturn(Mono.just(0));
        given(songLikeRepository.save(any())).willReturn(Mono.just(SongLikeEntity.of(songId, userId, Action.LIKE)));
        given(likeWriteStrategy.addLike(songId)).willReturn(Mono.empty());

        // when
        songLikeService.likeSong(songId, userId).block();

        // then
        InOrder inOrder = inOrder(songRepository, songLikeRepository, likeWriteStrategy);
        inOrder.verify(songRepository).existsById(songId);
        inOrder.verify(songLikeRepository).countUserLikeStatus(songId, userId);
        inOrder.verify(songLikeRepository).save(any());
        inOrder.verify(likeWriteStrategy).addLike(songId);
    }

    @Test
    @DisplayName("존재하지 않는 노래에 좋아요 시 SongNotFoundException이 발생한다")
    void likeSong_Fail_NotFound() {
        // given
        Long songId = 999L;
        given(songRepository.existsById(songId)).willReturn(Mono.just(false));

        // when & then
        StepVerifier.create(songLikeService.likeSong(songId, 1L))
                .expectError(SongNotFoundException.class)
                .verify();

        // 노래 존재 확인 이후의 로직은 실행되지 않아야 함을 검증
        verify(songLikeRepository, never()).countUserLikeStatus(any(), any());
    }

    @Test
    @DisplayName("이미 좋아요를 누른 곡에 다시 좋아요 시 AlreadyLikedException이 발생한다")
    void likeSong_Fail_AlreadyLiked() {
        // given
        Long songId = 1L;
        Long userId = 100L;
        given(songRepository.existsById(songId)).willReturn(Mono.just(true));
        given(songLikeRepository.countUserLikeStatus(songId, userId)).willReturn(Mono.just(1));

        // when & then
        StepVerifier.create(songLikeService.likeSong(songId, userId))
                .expectError(AlreadyLikedException.class)
                .verify();
    }

    @Test
    @DisplayName("좋아요를 하지 않은 곡을 취소하려 하면 NotLikedException이 발생한다")
    void unlikeSong_Fail_NotLiked() {
        // given
        Long songId = 1L;
        Long userId = 100L;
        given(songLikeRepository.countUserLikeStatus(songId, userId)).willReturn(Mono.just(0));

        // when & then
        StepVerifier.create(songLikeService.unlikeSong(songId, userId))
                .expectError(NotLikedException.class)
                .verify();

        // 저장 로직이 호출되지 않았는지 검증
        verify(songLikeRepository, never()).save(any());
    }

    @Test
    @DisplayName("DB 저장은 성공했으나 전략 실행(addLike) 실패 시 에러를 전파한다")
    void likeSong_Fail_StrategyError() {
        // given
        Long songId = 1L;
        Long userId = 100L;
        given(songRepository.existsById(songId)).willReturn(Mono.just(true));
        given(songLikeRepository.countUserLikeStatus(songId, userId)).willReturn(Mono.just(0));
        given(songLikeRepository.save(any())).willReturn(Mono.just(SongLikeEntity.of(songId, userId, Action.LIKE)));

        // 전략 실행 시 런타임 에러 발생 시뮬레이션
        given(likeWriteStrategy.addLike(songId)).willReturn(Mono.error(new RuntimeException("Redis/Memory Error")));

        // when & then
        StepVerifier.create(songLikeService.likeSong(songId, userId))
                .expectError(RuntimeException.class)
                .verify();
    }
}
