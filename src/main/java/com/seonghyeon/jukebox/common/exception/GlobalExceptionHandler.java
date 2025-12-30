package com.seonghyeon.jukebox.common.exception;

import com.seonghyeon.jukebox.common.exception.like.AlreadyLikedException;
import com.seonghyeon.jukebox.common.exception.like.NotLikedException;
import com.seonghyeon.jukebox.common.exception.like.SongNotFoundException;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Slf4j
@Hidden
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ServerWebInputException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<ErrorResponse> handleTypeMismatch(ServerWebInputException ex) {
        log.debug("Type Mismatch: {}", ex.getMessage());
        return Mono.just(ErrorResponse.of("BAD_REQUEST", "잘못된 타입의 값이 입력되었습니다."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        log.warn("Bad Request: {}", ex.getMessage());
        return Mono.just(ErrorResponse.of("BAD_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(NotLikedException.class)
    @ResponseStatus(HttpStatus.CONFLICT) // 409
    public Mono<ErrorResponse> handleNotLikedException(NotLikedException e) {
        log.warn("[LikeError] User attempted to unlike a song they haven't liked: {}", e.getMessage());
        return Mono.just(ErrorResponse.of("NOT_LIKED", e.getMessage()));
    }

    @ExceptionHandler(AlreadyLikedException.class)
    @ResponseStatus(HttpStatus.CONFLICT) // 409
    public Mono<ErrorResponse> handleAlreadyLikedException(AlreadyLikedException e) {
        return Mono.just(ErrorResponse.of("ALREADY_LIKED", e.getMessage()));
    }

    @ExceptionHandler(SongNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Mono<ErrorResponse> handleSongNotFound(SongNotFoundException ex) {
        log.warn("Not Found: {}", ex.getMessage());
        return Mono.just(ErrorResponse.of("SONG_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<ErrorResponse> handleValidationException(WebExchangeBindException e) {
        // 가장 첫 번째 에러 메시지를 가져오거나, 모든 에러를 합칠 수 있습니다.
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(error -> String.format("[%s] %s", error.getField(), error.getDefaultMessage()))
                .findFirst()
                .orElse("입력 값이 유효하지 않습니다.");

        log.warn("[ValidationError] Invalid request: {}", errorMessage);
        return Mono.just(ErrorResponse.of("BAD_REQUEST", errorMessage));
    }

    // 시스템 내부 오류
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Mono<ErrorResponse> handleInternalError(Exception ex) {
        log.error("Internal Server Error", ex);
        return Mono.just(ErrorResponse.of("SERVER_ERROR", "서버 내부 오류가 발생했습니다."));
    }
}
