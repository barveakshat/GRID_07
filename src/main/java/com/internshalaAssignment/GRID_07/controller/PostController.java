package com.internshalaAssignment.GRID_07.controller;

import com.internshalaAssignment.GRID_07.api.dto.request.CreateCommentRequest;
import com.internshalaAssignment.GRID_07.api.dto.request.CreatePostRequest;
import com.internshalaAssignment.GRID_07.api.dto.request.LikePostRequest;
import com.internshalaAssignment.GRID_07.api.dto.response.CommentResponse;
import com.internshalaAssignment.GRID_07.api.dto.response.PostResponse;
import com.internshalaAssignment.GRID_07.service.CommentService;
import com.internshalaAssignment.GRID_07.service.LikeService;
import com.internshalaAssignment.GRID_07.service.PostService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
public class PostController {

	private final PostService postService;
	private final CommentService commentService;
	private final LikeService likeService;

	public PostController(PostService postService, CommentService commentService, LikeService likeService) {
		this.postService = postService;
		this.commentService = commentService;
		this.likeService = likeService;
	}

	@PostMapping
	public ResponseEntity<PostResponse> createPost(@Valid @RequestBody CreatePostRequest request) {
		PostResponse response = postService.createPost(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@PostMapping("/{postId}/comments")
	public ResponseEntity<CommentResponse> addComment(
		@PathVariable Long postId,
		@Valid @RequestBody CreateCommentRequest request
	) {
		CommentResponse response = commentService.createComment(postId, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@PostMapping("/{postId}/like")
	public ResponseEntity<Map<String, Object>> likePost(
		@PathVariable Long postId,
		@Valid @RequestBody LikePostRequest request
	) {
		long likeCount = likeService.likePost(postId, request);
		Map<String, Object> response = Map.of(
			"postId", postId,
			"likeCount", likeCount,
			"message", "Post liked successfully"
		);
		return ResponseEntity.ok(response);
	}
}

