package com.internshalaAssignment.GRID_07.service;

import com.internshalaAssignment.GRID_07.api.dto.request.LikePostRequest;
import com.internshalaAssignment.GRID_07.domain.entity.PostLike;
import com.internshalaAssignment.GRID_07.domain.enums.InteractionType;
import com.internshalaAssignment.GRID_07.exception.BusinessRuleViolationException;
import com.internshalaAssignment.GRID_07.exception.ResourceNotFoundException;
import com.internshalaAssignment.GRID_07.redis.service.ViralityScoreService;
import com.internshalaAssignment.GRID_07.repository.PostLikeRepository;
import com.internshalaAssignment.GRID_07.repository.PostRepository;
import com.internshalaAssignment.GRID_07.repository.UserRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class LikeService {

	private final PostRepository postRepository;
	private final UserRepository userRepository;
	private final PostLikeRepository postLikeRepository;
	private final ViralityScoreService viralityScoreService;

	public LikeService(
		PostRepository postRepository,
		UserRepository userRepository,
		PostLikeRepository postLikeRepository,
		ViralityScoreService viralityScoreService
	) {
		this.postRepository = postRepository;
		this.userRepository = userRepository;
		this.postLikeRepository = postLikeRepository;
		this.viralityScoreService = viralityScoreService;
	}

	public long likePost(Long postId, LikePostRequest request) {
		if (!postRepository.existsById(postId)) {
			throw new ResourceNotFoundException("Post not found for id: " + postId);
		}

		if (!userRepository.existsById(request.userId())) {
			throw new ResourceNotFoundException("User not found for id: " + request.userId());
		}

		if (postLikeRepository.existsByPostIdAndUserId(postId, request.userId())) {
			throw new BusinessRuleViolationException("User has already liked this post");
		}

		PostLike like = new PostLike();
		like.setPostId(postId);
		like.setUserId(request.userId());
		like.setCreatedAt(Instant.now());
		postLikeRepository.save(like);

		viralityScoreService.incrementScore(postId, InteractionType.HUMAN_LIKE);
		return postLikeRepository.countByPostId(postId);
	}
}
