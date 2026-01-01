package com.seonghyeon.jukebox.controller.dto.response;

import com.seonghyeon.jukebox.repository.dto.SongLikeCountDto;
import io.swagger.v3.oas.annotations.media.Schema;

public record TopLikedResponse(
        @Schema(description = "노래 ID", example = "794169986393843581")
        Long songId,
        @Schema(description = "노래 제목", example = "Dreams")
        String title,
        @Schema(description = "가수 이름", example = "Fleetwood Mac")
        String artist,
        @Schema(description = "앨범 이름", example = "Rumours")
        String album,
        @Schema(description = "좋아요 수", example = "150")
        Long likeCount
) {

    public static TopLikedResponse from(SongLikeCountDto s) {
        return new TopLikedResponse(s.songId(), s.title(), s.artist(), s.album(), s.likeCount());
    }
}
