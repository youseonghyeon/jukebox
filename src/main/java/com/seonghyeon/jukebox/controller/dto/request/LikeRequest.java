package com.seonghyeon.jukebox.controller.dto.request;

import com.seonghyeon.jukebox.entity.like.Action;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.NotNull;

public record LikeRequest(
        @Parameter(description = "사용자 ID", example = "1")
        @NotNull(message = "사용자 ID는 필수입니다.")
        Long userId,

        @Parameter(description = "액션 (LIKE 또는 UNLIKE)", example = "LIKE")
        @NotNull(message = "액션(LIKE/UNLIKE)은 필수입니다.")
        Action action
) {
}
