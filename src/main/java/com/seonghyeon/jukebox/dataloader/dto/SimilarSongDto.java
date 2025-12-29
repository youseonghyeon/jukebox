package com.seonghyeon.jukebox.dataloader.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Map;
import java.util.Objects;

public record SimilarSongDto(
        String artist,
        String song,
        Double similarityScore
) {

    @JsonCreator
    public SimilarSongDto(Map<String, Object> data) {
        this(
                extractValue(data, "Similar Artist"),
                extractValue(data, "Similar Song"),
                extractScore(data)
        );
    }

    // 값 추출
    private static String extractValue(Map<String, Object> data, String prefix) {
        if (data == null) return null;

        return data.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().startsWith(prefix))
                .map(Map.Entry::getValue)
                .filter(Objects::nonNull)
                .map(Object::toString)
                .findFirst()
                .orElse(null);
    }

    // 점수 추출
    private static Double extractScore(Map<String, Object> data) {
        Object val = data.get("Similarity Score");
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        return 0.0;
    }
}
