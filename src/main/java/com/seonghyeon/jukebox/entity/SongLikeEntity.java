package com.seonghyeon.jukebox.entity;

import com.seonghyeon.jukebox.entity.like.Action;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Table("song_likes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SongLikeEntity {

    @Id
    private Long id;
    private Long songId;
    private Long userId;
    private String action;

    public static SongLikeEntity of(Long songId, Long userId, Action action) {
        return new SongLikeEntity(null, songId, userId, action.name());
    }

}
