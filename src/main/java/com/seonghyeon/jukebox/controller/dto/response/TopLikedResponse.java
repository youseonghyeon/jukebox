package com.seonghyeon.jukebox.controller.dto.response;

import com.seonghyeon.jukebox.repository.dto.SongLikeCountDto;
import io.swagger.v3.oas.annotations.media.Schema;

public record TopLikedResponse(
        @Schema(description = "노래 ID", example = "794169986393843581")
        Long songId,
        @Schema(description = "좋아요 수", example = "150")
        Long likeCount
) {

    public static TopLikedResponse from(SongLikeCountDto songLikeCountDto) {
        return new TopLikedResponse(songLikeCountDto.songId(), songLikeCountDto.likeCount());
    }
}
