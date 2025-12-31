package com.seonghyeon.jukebox.service.like;

import com.seonghyeon.jukebox.common.exception.like.AlreadyLikedException;
import com.seonghyeon.jukebox.common.exception.like.NotLikedException;
import com.seonghyeon.jukebox.common.exception.like.SongNotFoundException;
import com.seonghyeon.jukebox.entity.SongLikeEntity;
import com.seonghyeon.jukebox.entity.like.Action;
import com.seonghyeon.jukebox.repository.SongLikeRepository;
import com.seonghyeon.jukebox.repository.SongRepository;
import com.seonghyeon.jukebox.repository.dto.SongLikeCountDto;
import com.seonghyeon.jukebox.service.like.strategy.LikeWriteStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SongLikeService {

    private final LikeWriteStrategy likeWriteStrategy;
    private final SongLikeRepository songLikeRepository;
    private final SongRepository songRepository;

    public Mono<Void> likeSong(Long songId, Long userId) {
        return songRepository.existsById(songId)
                .filter(exists -> exists)
                // 노래 존재 유무 검증
                .switchIfEmpty(Mono.error(new SongNotFoundException("Song not found with ID: " + songId)))
                .then(Mono.defer(() -> songLikeRepository.countUserLikeStatus(songId, userId)))
                .filter(this::canLike)
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("[LikeService] Conflict detected: User {} already liked song {}", userId, songId);
                    return Mono.error(new AlreadyLikedException(String.format("The song (ID: %d) is already liked.", songId)));
                }))
                // 좋아요 기록 저장 및 카운트 증가
                .then(Mono.defer(() -> songLikeRepository.save(SongLikeEntity.of(songId, userId, Action.LIKE))))
                .then(Mono.defer(() -> likeWriteStrategy.addLike(songId)));
    }

    public Mono<Void> unlikeSong(Long songId, Long userId) {
        return songLikeRepository.countUserLikeStatus(songId, userId)
                // 좋아요 취소 가능 여부 검증
                .filter(this::canUnlike)
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("[LikeService] Conflict detected: User {} has no active unlike for song {}", userId, songId);
                    return Mono.error(new NotLikedException(String.format("The song (ID: %d) is not currently liked.", songId)));
                }))
                // 좋아요 취소 기록 저장 및 카운트 감소
                .then(Mono.defer(() -> songLikeRepository.save(SongLikeEntity.of(songId, userId, Action.UNLIKE))))
                .then(Mono.defer(() -> likeWriteStrategy.removeLike(songId)));
    }

    private boolean canLike(Integer likeCount) {
        return likeCount != null && likeCount < 1; // 좋아요를 하지 않은 상태
    }

    private boolean canUnlike(Integer likeCount) {
        return likeCount != null && likeCount > 0; // 좋아요를 한 상태
    }

    public Flux<SongLikeCountDto> getTopLikedSongs(LocalDateTime since, int limit) {
        log.debug("Fetching top {} liked songs since {}", limit, since);
        return songLikeRepository.findTopLikedSongs(since, limit);
    }
}
