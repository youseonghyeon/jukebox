package com.seonghyeon.jukebox.entity.like;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Action {
    LIKE,
    UNLIKE;

    @JsonCreator
    public static Action fromString(String value) {
        for (Action action : Action.values()) {
            if (action.name().equalsIgnoreCase(value)) {
                return action;
            }
        }
        return null; // Validation 에러 유도 (@NotNull)
    }
}
