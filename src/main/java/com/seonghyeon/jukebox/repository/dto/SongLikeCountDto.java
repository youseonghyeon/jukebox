package com.seonghyeon.jukebox.repository.dto;

public record SongLikeCountDto(
        Long songId,
        Long likeCount
) {
}
