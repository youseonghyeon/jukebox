package com.seonghyeon.jukebox.repository.dto;

import org.springframework.data.relational.core.mapping.Column;

public record YearArtistStatsDto(
        @Column("release_year")
        Integer releaseYear,
        @Column("artist")
        String artist,
        @Column("album_count")
        Long albumCount
) {
}
