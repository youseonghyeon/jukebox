package com.seonghyeon.jukebox.controller.dto.response;

import com.seonghyeon.jukebox.repository.dto.SongLikeCountDto;

public record TopLikedResponse(
        Long songId,
        Long likeCount
) {

    public static TopLikedResponse from(SongLikeCountDto songLikeCountDto) {
        return new TopLikedResponse(songLikeCountDto.songId(), songLikeCountDto.likeCount());
    }
}
