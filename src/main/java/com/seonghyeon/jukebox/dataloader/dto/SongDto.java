package com.seonghyeon.jukebox.dataloader.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SongDto(
        @JsonProperty("Artist(s)") String artists,
        @JsonProperty("song") String song,
        @JsonProperty("text") String text,
        @JsonProperty("Length") String length,
        @JsonProperty("emotion") String emotion,
        @JsonProperty("Genre") String genre,
        @JsonProperty("Album") String album,
        @JsonProperty("Release Date") String releaseDate,
        @JsonProperty("Key") String key,
        @JsonProperty("Tempo") Double tempo,
        @JsonProperty("Loudness (db)") Double loudnessDb,
        @JsonProperty("Time signature") String timeSignature,
        @JsonProperty("Explicit") String explicit, // 없음
        @JsonProperty("Popularity") Integer popularity,
        @JsonProperty("Energy") Integer energy,
        @JsonProperty("Danceability") Integer danceability,
        @JsonProperty("Positiveness") Integer positiveness,
        @JsonProperty("Speechiness") Integer speechiness,
        @JsonProperty("Liveness") Integer liveness,
        @JsonProperty("Acousticness") Integer acousticness,
        @JsonProperty("Instrumentalness") Integer instrumentalness,

        @JsonProperty("Good for Party") Integer goodForParty,
        @JsonProperty("Good for Work/Study") Integer goodForWorkStudy,
        @JsonProperty("Good for Relaxation/Meditation") Integer goodForRelaxation,
        @JsonProperty("Good for Exercise") Integer goodForExercise,
        @JsonProperty("Good for Running") Integer goodForRunning,
        @JsonProperty("Good for Yoga/Stretching") Integer goodForYoga,
        @JsonProperty("Good for Driving") Integer goodForDriving,
        @JsonProperty("Good for Social Gatherings") Integer goodForSocial,
        @JsonProperty("Good for Morning Routine") Integer goodForMorning,

        // 곡 유사도 리스트
        @JsonProperty("Similar Songs") List<SimilarSongDto> similarSongs
) {
}
