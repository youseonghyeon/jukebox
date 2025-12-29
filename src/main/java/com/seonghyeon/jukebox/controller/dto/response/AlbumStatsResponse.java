package com.seonghyeon.jukebox.controller.dto.response;

import com.seonghyeon.jukebox.repository.dto.YearArtistStatsDto;

public record AlbumStatsResponse(
        Integer releaseYear,
        String artist,
        Long albumCount
) {

    public static AlbumStatsResponse from(YearArtistStatsDto dto) {
        return new AlbumStatsResponse(
                dto.releaseYear(),
                dto.artist(),
                dto.albumCount()
        );
    }
}
