package com.seonghyeon.jukebox.service.like;

import com.seonghyeon.jukebox.common.exception.like.AlreadyLikedException;
import com.seonghyeon.jukebox.common.exception.like.NotLikedException;
import com.seonghyeon.jukebox.entity.SongLikeEntity;
import com.seonghyeon.jukebox.entity.like.Action;
import com.seonghyeon.jukebox.repository.SongLikeRepository;
import com.seonghyeon.jukebox.service.like.strategy.LikeWriteStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class SongLikeService {

    private final LikeWriteStrategy likeWriteStrategy;
    private final SongLikeRepository songLikeRepository;

    public Mono<Void> likeSong(Long songId, Long userId) {
        return songLikeRepository.countUserLikeStatus(songId, userId)
                .flatMap(likeCount -> {
                    if (likeCount >= 1) {
                        log.debug("User {} has already liked song {}", userId, songId);
                        return Mono.error(new AlreadyLikedException());
                    } else {
                        SongLikeEntity entity = SongLikeEntity.of(songId, userId, Action.LIKE);
                        Mono<Void> saveHistoryAction = songLikeRepository.save(entity).then();
                        return likeWriteStrategy.addLike(songId, saveHistoryAction);
                    }
                });
    }

    public Mono<Void> unlikeSong(Long songId, Long userId) {
        return songLikeRepository.countUserLikeStatus(songId, userId)
                .flatMap(likeCount -> {
                    if (likeCount <= 0) {
                        log.debug("User {} has not liked song {}", userId, songId);
                        return Mono.error(new NotLikedException());
                    } else {
                        SongLikeEntity entity = SongLikeEntity.of(songId, userId, Action.UNLIKE);
                        Mono<Void> deleteHistoryAction = songLikeRepository.save(entity).then();
                        return likeWriteStrategy.removeLike(songId, deleteHistoryAction);
                    }
                });
    }
}
