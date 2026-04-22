package com.internshalaAssignment.GRID_07.service;

import com.internshalaAssignment.GRID_07.api.dto.request.CreatePostRequest;
import com.internshalaAssignment.GRID_07.api.dto.response.PostResponse;
import com.internshalaAssignment.GRID_07.domain.entity.Post;
import com.internshalaAssignment.GRID_07.domain.enums.AuthorType;
import com.internshalaAssignment.GRID_07.exception.ResourceNotFoundException;
import com.internshalaAssignment.GRID_07.repository.BotRepository;
import com.internshalaAssignment.GRID_07.repository.CommentRepository;
import com.internshalaAssignment.GRID_07.repository.PostLikeRepository;
import com.internshalaAssignment.GRID_07.repository.PostRepository;
import com.internshalaAssignment.GRID_07.repository.UserRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class PostService {

	private final PostRepository postRepository;
	private final UserRepository userRepository;
	private final BotRepository botRepository;
	private final PostLikeRepository postLikeRepository;
	private final CommentRepository commentRepository;

	public PostService(
		PostRepository postRepository,
		UserRepository userRepository,
		BotRepository botRepository,
		PostLikeRepository postLikeRepository,
		CommentRepository commentRepository
	) {
		this.postRepository = postRepository;
		this.userRepository = userRepository;
		this.botRepository = botRepository;
		this.postLikeRepository = postLikeRepository;
		this.commentRepository = commentRepository;
	}

	public PostResponse createPost(CreatePostRequest request) {
		validateAuthorExists(request.authorType(), request.authorId());

		Post post = new Post();
		post.setAuthorType(request.authorType());
		post.setAuthorId(request.authorId());
		post.setContent(request.content().trim());
		post.setCreatedAt(Instant.now());

		Post savedPost = postRepository.save(post);
		return toPostResponse(savedPost);
	}

	private void validateAuthorExists(AuthorType authorType, Long authorId) {
		boolean exists = authorType == AuthorType.USER
			? userRepository.existsById(authorId)
			: botRepository.existsById(authorId);

		if (!exists) {
			throw new ResourceNotFoundException("Author not found for type " + authorType + " and id " + authorId);
		}
	}

	private PostResponse toPostResponse(Post post) {
		long likeCount = postLikeRepository.countByPostId(post.getId());
		long commentCount = commentRepository.countByPostId(post.getId());
		return new PostResponse(
			post.getId(),
			post.getAuthorType(),
			post.getAuthorId(),
			post.getContent(),
			post.getCreatedAt(),
			likeCount,
			commentCount
		);
	}
}
