package com.seonghyeon.jukebox.common.exception;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
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

    // 시스템 내부 오류
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Mono<ErrorResponse> handleInternalError(Exception ex) {
        log.error("Internal Server Error", ex);
        return Mono.just(ErrorResponse.of("SERVER_ERROR", "서버 내부 오류가 발생했습니다."));
    }
}
