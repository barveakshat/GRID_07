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
import com.internshalaAssignment.GRID_07.repository.UserRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class CommentService {

	private static final int MAX_DEPTH = 20;

	private final CommentRepository commentRepository;
	private final PostRepository postRepository;
	private final UserRepository userRepository;
	private final BotRepository botRepository;
	private final ViralityScoreService viralityScoreService;
	private final BotGuardrailService botGuardrailService;
	private final BotInteractionEventPublisher botInteractionEventPublisher;

	public CommentService(
		CommentRepository commentRepository,
		PostRepository postRepository,
		UserRepository userRepository,
		BotRepository botRepository,
		ViralityScoreService viralityScoreService,
		BotGuardrailService botGuardrailService,
		BotInteractionEventPublisher botInteractionEventPublisher
	) {
		this.commentRepository = commentRepository;
		this.postRepository = postRepository;
		this.userRepository = userRepository;
		this.botRepository = botRepository;
		this.viralityScoreService = viralityScoreService;
		this.botGuardrailService = botGuardrailService;
		this.botInteractionEventPublisher = botInteractionEventPublisher;
	}

	public CommentResponse createComment(Long postId, CreateCommentRequest request) {
		Post post = postRepository.findById(postId)
			.orElseThrow(() -> new ResourceNotFoundException("Post not found for id: " + postId));

		validateAuthorExists(request.authorType(), request.authorId());
		Comment parentComment = fetchParentComment(postId, request.parentCommentId());
		int depthLevel = resolveDepthLevel(parentComment);
		Long targetHumanId = resolveTargetHumanId(post, parentComment);

		if (request.authorType() == AuthorType.BOT) {
			applyBotGuardrails(request.authorId(), postId, targetHumanId);
		}

		Comment comment = new Comment();
		comment.setPostId(postId);
		comment.setParentCommentId(request.parentCommentId());
		comment.setDepthLevel(depthLevel);
		comment.setAuthorType(request.authorType());
		comment.setAuthorId(request.authorId());
		comment.setContent(request.content().trim());
		comment.setCreatedAt(Instant.now());

		Comment savedComment = commentRepository.save(comment);
		InteractionType interactionType = request.authorType() == AuthorType.BOT
			? InteractionType.BOT_REPLY
			: InteractionType.HUMAN_COMMENT;
		viralityScoreService.incrementScore(postId, interactionType);

		if (request.authorType() == AuthorType.BOT && targetHumanId != null) {
			botInteractionEventPublisher.publish(postId, request.authorId(), targetHumanId);
		}

		return toCommentResponse(savedComment);
	}

	private void applyBotGuardrails(Long botId, Long postId, Long targetHumanId) {
		botGuardrailService.claimBotReplySlot(postId);
		if (targetHumanId != null) {
			botGuardrailService.claimBotHumanCooldown(botId, targetHumanId);
		}
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

	private void validateAuthorExists(AuthorType authorType, Long authorId) {
		boolean exists = authorType == AuthorType.USER
			? userRepository.existsById(authorId)
			: botRepository.existsById(authorId);

		if (!exists) {
			throw new ResourceNotFoundException("Author not found for type " + authorType + " and id " + authorId);
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

	private CommentResponse toCommentResponse(Comment comment) {
		return new CommentResponse(
			comment.getId(),
			comment.getPostId(),
			comment.getParentCommentId(),
			comment.getDepthLevel(),
			comment.getAuthorType(),
			comment.getAuthorId(),
			comment.getContent(),
			comment.getCreatedAt()
		);
	}
}

