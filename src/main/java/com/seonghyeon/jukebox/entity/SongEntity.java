package com.seonghyeon.jukebox.entity;

import com.seonghyeon.jukebox.dataloader.dto.SongDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;

@Slf4j
@Table("songs")
public record SongEntity(
        @Id Long id,
        String artist,
        String title,
        String album,
        @Column("release_date") LocalDate releaseDate,
        @Column("release_year") Integer releaseYear,
        String genre,
        String lyrics,
        String length,
        String emotion,
        @Column("total_likes") Long totalLikes
) {

    public static SongEntity fromDto(SongDto dto) {
        LocalDate releaseDate = convertReleaseDate(dto.releaseDate());
        Integer releaseYear = (releaseDate != null) ? releaseDate.getYear() : null;
        return new SongEntity(
                null,
                dto.artists(),
                dto.song(),
                dto.album(),
                releaseDate,
                releaseYear,
                dto.genre(),
                dto.text(),
                dto.length(),
                dto.emotion(),
                0L
        );
    }

    private static LocalDate convertReleaseDate(String dateString) {
        if (dateString == null || dateString.isBlank()) return null;
        try {
            return LocalDate.parse(dateString);
        } catch (Exception e) {
            log.debug("Invalid date format: {}", dateString);
            return null;
        }
    }
}
