package com.internshalaAssignment.GRID_07.api.dto.response;

import com.internshalaAssignment.GRID_07.domain.enums.AuthorType;
import java.time.Instant;

public record CommentResponse(
	Long id,
	Long postId,
	Long parentCommentId,
	Integer depthLevel,
	AuthorType authorType,
	Long authorId,
	String content,
	Instant createdAt
) {
}

