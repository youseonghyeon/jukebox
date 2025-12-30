package com.seonghyeon.jukebox.common.exception.like;

public class SongNotFoundException extends IllegalArgumentException {

    public SongNotFoundException(String message) {
        super(message);
    }
}
