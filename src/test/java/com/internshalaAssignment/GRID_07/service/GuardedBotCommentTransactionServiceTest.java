package com.internshalaAssignment.GRID_07.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.internshalaAssignment.GRID_07.api.dto.request.CreateCommentRequest;
import com.internshalaAssignment.GRID_07.api.dto.response.CommentResponse;
import com.internshalaAssignment.GRID_07.domain.entity.Comment;
import com.internshalaAssignment.GRID_07.domain.entity.Post;
import com.internshalaAssignment.GRID_07.domain.enums.AuthorType;
import com.internshalaAssignment.GRID_07.domain.enums.InteractionType;
import com.internshalaAssignment.GRID_07.domain.event.BotInteractionEventPublisher;
import com.internshalaAssignment.GRID_07.exception.TooManyRequestsException;
import com.internshalaAssignment.GRID_07.redis.service.BotGuardrailService;
import com.internshalaAssignment.GRID_07.redis.service.ViralityScoreService;
import com.internshalaAssignment.GRID_07.repository.BotRepository;
import com.internshalaAssignment.GRID_07.repository.CommentRepository;
import com.internshalaAssignment.GRID_07.repository.PostRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GuardedBotCommentTransactionServiceTest {

	@Mock
	private PostRepository postRepository;

	@Mock
	private BotRepository botRepository;

	@Mock
	private CommentRepository commentRepository;

	@Mock
	private BotGuardrailService botGuardrailService;

	@Mock
	private ViralityScoreService viralityScoreService;

	@Mock
	private BotInteractionEventPublisher botInteractionEventPublisher;

	@InjectMocks
	private GuardedBotCommentTransactionService guardedBotCommentTransactionService;

	@Test
	void claimsGuardrailsBeforeSaveAndPublishesEvent() {
		Long postId = 101L;
		Long botId = 5L;
		Long targetUserId = 42L;

		Post post = new Post();
		post.setId(postId);
		post.setAuthorType(AuthorType.USER);
		post.setAuthorId(targetUserId);

		CreateCommentRequest request = new CreateCommentRequest(AuthorType.BOT, botId, null, "  bot reply  ");
		Comment saved = new Comment();
		saved.setId(900L);
		saved.setPostId(postId);
		saved.setParentCommentId(null);
		saved.setDepthLevel(1);
		saved.setAuthorType(AuthorType.BOT);
		saved.setAuthorId(botId);
		saved.setContent("bot reply");
		saved.setCreatedAt(Instant.now());

		when(postRepository.findById(postId)).thenReturn(Optional.of(post));
		when(botRepository.existsById(botId)).thenReturn(true);
		when(commentRepository.save(any(Comment.class))).thenReturn(saved);

		CommentResponse response = guardedBotCommentTransactionService.createGuardedBotComment(postId, request);

		ArgumentCaptor<Comment> commentCaptor = ArgumentCaptor.forClass(Comment.class);
		verify(commentRepository).save(commentCaptor.capture());
		assertEquals("bot reply", commentCaptor.getValue().getContent());
		assertEquals(1, commentCaptor.getValue().getDepthLevel());
		assertEquals(AuthorType.BOT, commentCaptor.getValue().getAuthorType());

		InOrder inOrder = inOrder(botGuardrailService, commentRepository, viralityScoreService, botInteractionEventPublisher);
		inOrder.verify(botGuardrailService).claimBotReplySlot(postId);
		inOrder.verify(botGuardrailService).claimBotHumanCooldown(botId, targetUserId);
		inOrder.verify(commentRepository).save(any(Comment.class));
		inOrder.verify(viralityScoreService).incrementScore(postId, InteractionType.BOT_REPLY);
		inOrder.verify(botInteractionEventPublisher).publish(postId, botId, targetUserId);

		assertEquals(900L, response.id());
		assertEquals(postId, response.postId());
	}

	@Test
	void cooldownDenialSkipsDbAndReleasesReplySlot() {
		Long postId = 111L;
		Long botId = 8L;
		Long targetUserId = 77L;

		Post post = new Post();
		post.setId(postId);
		post.setAuthorType(AuthorType.USER);
		post.setAuthorId(targetUserId);

		CreateCommentRequest request = new CreateCommentRequest(AuthorType.BOT, botId, null, "hello");

		when(postRepository.findById(postId)).thenReturn(Optional.of(post));
		when(botRepository.existsById(botId)).thenReturn(true);
		org.mockito.Mockito.doThrow(new TooManyRequestsException("cooldown active"))
			.when(botGuardrailService)
			.claimBotHumanCooldown(botId, targetUserId);

		assertThrows(
			TooManyRequestsException.class,
			() -> guardedBotCommentTransactionService.createGuardedBotComment(postId, request)
		);

		verify(commentRepository, never()).save(any(Comment.class));
		verify(viralityScoreService, never()).incrementScore(any(Long.class), any(InteractionType.class));
		verify(botInteractionEventPublisher, never()).publish(any(Long.class), any(Long.class), any(Long.class));
		verify(botGuardrailService).releaseBotReplySlot(postId);
		verify(botGuardrailService, never()).releaseBotHumanCooldown(botId, targetUserId);
	}

	@Test
	void saveFailureReleasesClaimedReplyAndCooldown() {
		Long postId = 222L;
		Long botId = 9L;
		Long targetUserId = 55L;

		Post post = new Post();
		post.setId(postId);
		post.setAuthorType(AuthorType.USER);
		post.setAuthorId(targetUserId);

		CreateCommentRequest request = new CreateCommentRequest(AuthorType.BOT, botId, null, "boom");

		when(postRepository.findById(postId)).thenReturn(Optional.of(post));
		when(botRepository.existsById(botId)).thenReturn(true);
		org.mockito.Mockito.doThrow(new IllegalStateException("db down"))
			.when(commentRepository)
			.save(any(Comment.class));

		assertThrows(
			IllegalStateException.class,
			() -> guardedBotCommentTransactionService.createGuardedBotComment(postId, request)
		);

		verify(botGuardrailService).releaseBotHumanCooldown(botId, targetUserId);
		verify(botGuardrailService).releaseBotReplySlot(postId);
		verify(viralityScoreService, never()).incrementScore(any(Long.class), any(InteractionType.class));
	}
}

