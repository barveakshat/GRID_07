package com.internshalaAssignment.GRID_07.api.dto.request;

import com.internshalaAssignment.GRID_07.domain.enums.AuthorType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
	@NotNull AuthorType authorType,
	@NotNull Long authorId,
	Long parentCommentId,
	@NotBlank @Size(max = 2000) String content
) {
}

