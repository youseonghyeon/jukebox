package com.seonghyeon.jukebox.controller.dto.response;

import com.seonghyeon.jukebox.entity.SongStatisticsEntity;
import io.swagger.v3.oas.annotations.media.Schema;

public record AlbumStatsResponse(
        @Schema(description = "발매 연도", example = "2023")
        int releaseYear,

        @Schema(description = "가수 이름", example = "아이유")
        String artist,

        @Schema(description = "해당 연도 발매 앨범 수", example = "3")
        Long albumCount
) {

    public static AlbumStatsResponse from(SongStatisticsEntity dto) {
        return new AlbumStatsResponse(dto.releaseYear(), dto.artist(), dto.albumCount()
        );
    }
}
