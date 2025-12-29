package com.seonghyeon.jukebox.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("song_statistics")
public record SongStatisticsEntity(
        @Id
        Long id,
        Integer releaseYear,
        String artist,
        Long albumCount
) {
}
