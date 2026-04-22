package com.internshalaAssignment.GRID_07.api.dto.response;

import com.internshalaAssignment.GRID_07.domain.enums.AuthorType;
import java.time.Instant;

public record PostResponse(
	Long id,
	AuthorType authorType,
	Long authorId,
	String content,
	Instant createdAt,
	long likeCount,
	long commentCount
) {
}

