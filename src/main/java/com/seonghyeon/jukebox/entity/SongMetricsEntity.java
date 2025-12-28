package com.seonghyeon.jukebox.entity;


import com.seonghyeon.jukebox.dataloader.dto.SongDto;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("song_metrics")
public record SongMetricsEntity(
        @Id @Column("song_id") Long songId, // PK이자 FK
        @Column("musical_key") String musicalKey,
        Double tempo,
        @Column("loudness_db") Double loudnessDb,
        @Column("time_signature") String timeSignature,
        String explicit,
        Integer popularity,
        Integer energy,
        Integer danceability,
        Integer positiveness,
        Integer speechiness,
        Integer liveness,
        Integer acousticness,
        Integer instrumentalness,
        @Column("is_party") boolean isParty,
        @Column("is_study") boolean isStudy,
        @Column("is_relaxation") boolean isRelaxation,
        @Column("is_exercise") boolean isExercise,
        @Column("is_running") boolean isRunning,
        @Column("is_yoga") boolean isYoga,
        @Column("is_driving") boolean isDriving,
        @Column("is_social") boolean isSocial,
        @Column("is_morning") boolean isMorning
) {

    public static SongMetricsEntity fromDto(SongDto dto, Long songId) {
        return new SongMetricsEntity(
                songId,
                dto.key(),
                dto.tempo(),
                dto.loudnessDb(),
                dto.timeSignature(),
                dto.explicit(),
                dto.popularity(),
                dto.energy(),
                dto.danceability(),
                dto.positiveness(),
                dto.speechiness(),
                dto.liveness(),
                dto.acousticness(),
                dto.instrumentalness(),
                dto.goodForParty() == 1,
                dto.goodForWorkStudy() == 1,
                dto.goodForRelaxation() == 1,
                dto.goodForExercise() == 1,
                dto.goodForRunning() == 1,
                dto.goodForYoga() == 1,
                dto.goodForDriving() == 1,
                dto.goodForSocial() == 1,
                dto.goodForMorning() == 1
        );
    }
}
