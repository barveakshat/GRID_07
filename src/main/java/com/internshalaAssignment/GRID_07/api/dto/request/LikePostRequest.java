package com.internshalaAssignment.GRID_07.api.dto.request;

import jakarta.validation.constraints.NotNull;

public record LikePostRequest(@NotNull Long userId) {
}

