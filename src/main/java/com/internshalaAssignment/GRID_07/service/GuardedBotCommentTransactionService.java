package com.internshalaAssignment.GRID_07.service;

import com.internshalaAssignment.GRID_07.api.dto.request.CreateCommentRequest;
import com.internshalaAssignment.GRID_07.api.dto.response.CommentResponse;
import com.internshalaAssignment.GRID_07.domain.entity.Comment;
import com.internshalaAssignment.GRID_07.domain.entity.Post;
import com.internshalaAssignment.GRID_07.domain.enums.AuthorType;
import com.internshalaAssignment.GRID_07.domain.enums.InteractionType;
import com.internshalaAssignment.GRID_07.domain.event.BotInteractionEventPublisher;
import com.internshalaAssignment.GRID_07.exception.BusinessRuleViolationException;
import com.internshalaAssignment.GRID_07.exception.ResourceNotFoundException;
import com.internshalaAssignment.GRID_07.redis.service.BotGuardrailService;
import com.internshalaAssignment.GRID_07.redis.service.ViralityScoreService;
import com.internshalaAssignment.GRID_07.repository.BotRepository;
import com.internshalaAssignment.GRID_07.repository.CommentRepository;
import com.internshalaAssignment.GRID_07.repository.PostRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuardedBotCommentTransactionService {

	private static final int MAX_DEPTH = 20;

	private final PostRepository postRepository;
	private final BotRepository botRepository;
	private final CommentRepository commentRepository;
	private final BotGuardrailService botGuardrailService;
	private final ViralityScoreService viralityScoreService;
	private final BotInteractionEventPublisher botInteractionEventPublisher;

	public GuardedBotCommentTransactionService(
		PostRepository postRepository,
		BotRepository botRepository,
		CommentRepository commentRepository,
		BotGuardrailService botGuardrailService,
		ViralityScoreService viralityScoreService,
		BotInteractionEventPublisher botInteractionEventPublisher
	) {
		this.postRepository = postRepository;
		this.botRepository = botRepository;
		this.commentRepository = commentRepository;
		this.botGuardrailService = botGuardrailService;
		this.viralityScoreService = viralityScoreService;
		this.botInteractionEventPublisher = botInteractionEventPublisher;
	}

	@Transactional(
		isolation = Isolation.READ_COMMITTED,
		propagation = Propagation.REQUIRED,
		timeout = 5
	)
	public CommentResponse createGuardedBotComment(Long postId, CreateCommentRequest request) {
		Post post = postRepository.findById(postId)
			.orElseThrow(() -> new ResourceNotFoundException("Post not found for id: " + postId));

		if (!botRepository.existsById(request.authorId())) {
			throw new ResourceNotFoundException("Author not found for type BOT and id " + request.authorId());
		}

		Comment parentComment = fetchParentComment(postId, request.parentCommentId());
		int depthLevel = resolveDepthLevel(parentComment);
		Long targetHumanId = resolveTargetHumanId(post, parentComment);

		boolean replyClaimed = false;
		boolean cooldownClaimed = false;

		try {
			botGuardrailService.claimBotReplySlot(postId);
			replyClaimed = true;

			if (targetHumanId != null) {
				botGuardrailService.claimBotHumanCooldown(request.authorId(), targetHumanId);
				cooldownClaimed = true;
			}

			Comment comment = new Comment();
			comment.setPostId(postId);
			comment.setParentCommentId(request.parentCommentId());
			comment.setDepthLevel(depthLevel);
			comment.setAuthorType(AuthorType.BOT);
			comment.setAuthorId(request.authorId());
			comment.setContent(request.content().trim());
			comment.setCreatedAt(Instant.now());

			Comment savedComment = commentRepository.save(comment);
			viralityScoreService.incrementScore(postId, InteractionType.BOT_REPLY);

			if (targetHumanId != null) {
				botInteractionEventPublisher.publish(postId, request.authorId(), targetHumanId);
			}

			return new CommentResponse(
				savedComment.getId(),
				savedComment.getPostId(),
				savedComment.getParentCommentId(),
				savedComment.getDepthLevel(),
				savedComment.getAuthorType(),
				savedComment.getAuthorId(),
				savedComment.getContent(),
				savedComment.getCreatedAt()
			);
		} catch (RuntimeException exception) {
			if (cooldownClaimed && targetHumanId != null) {
				botGuardrailService.releaseBotHumanCooldown(request.authorId(), targetHumanId);
			}
			if (replyClaimed) {
				botGuardrailService.releaseBotReplySlot(postId);
			}
			throw exception;
		}
	}

	private Comment fetchParentComment(Long postId, Long parentCommentId) {
		if (parentCommentId == null) {
			return null;
		}
		return commentRepository.findByIdAndPostId(parentCommentId, postId)
			.orElseThrow(() -> new ResourceNotFoundException("Parent comment not found for id: " + parentCommentId));
	}

	private int resolveDepthLevel(Comment parentComment) {
		if (parentComment == null) {
			return 1;
		}

		int depthLevel = parentComment.getDepthLevel() + 1;
		if (depthLevel > MAX_DEPTH) {
			throw new BusinessRuleViolationException("Comment depth cannot exceed " + MAX_DEPTH);
		}
		return depthLevel;
	}

	private Long resolveTargetHumanId(Post post, Comment parentComment) {
		if (parentComment != null && parentComment.getAuthorType() == AuthorType.USER) {
			return parentComment.getAuthorId();
		}
		if (post.getAuthorType() == AuthorType.USER) {
			return post.getAuthorId();
		}
		return null;
	}
}
