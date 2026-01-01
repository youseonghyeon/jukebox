package com.seonghyeon.jukebox.controller.dto.request;

import com.seonghyeon.jukebox.entity.like.Action;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record LikeRequest(
        @Schema(description = "사용자 ID", example = "1")
        @NotNull(message = "사용자 ID는 필수입니다.")
        Long userId,

        @Schema(description = "액션 (LIKE 또는 UNLIKE)", example = "LIKE")
        @NotNull(message = "값이 누락되었거나 잘못되었습니다. 'LIKE' 또는 'UNLIKE' 중 하나를 입력해야 합니다.")
        Action action
) {
}
