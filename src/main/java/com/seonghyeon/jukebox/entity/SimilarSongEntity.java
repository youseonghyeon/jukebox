package com.seonghyeon.jukebox.entity;

import com.seonghyeon.jukebox.dataloader.dto.SimilarSongDto;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("similar_songs")
public record SimilarSongEntity(
        @Id Long id,
        @Column("song_id") Long songId,
        @Column("similar_artist") String similarArtist,
        @Column("similar_title") String similarTitle,
        @Column("similarity_score") Double similarityScore
) {

    public static SimilarSongEntity fromDto(SimilarSongDto subDto, Long parentId) {
        return new SimilarSongEntity(
                null,
                parentId,
                subDto.artist(),
                subDto.song(),
                subDto.similarityScore()
        );
    }
}
