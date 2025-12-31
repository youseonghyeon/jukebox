package com.seonghyeon.jukebox.common.exception.like;

public class AlreadyLikedException extends IllegalArgumentException {

    public AlreadyLikedException(String message) {
        super(message);
    }
}
