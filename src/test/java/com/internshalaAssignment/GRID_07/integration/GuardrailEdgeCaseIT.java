package com.internshalaAssignment.GRID_07.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.internshalaAssignment.GRID_07.api.dto.request.CreateCommentRequest;
import com.internshalaAssignment.GRID_07.api.dto.request.CreatePostRequest;
import com.internshalaAssignment.GRID_07.api.dto.request.LikePostRequest;
import com.internshalaAssignment.GRID_07.api.dto.response.CommentResponse;
import com.internshalaAssignment.GRID_07.domain.entity.Bot;
import com.internshalaAssignment.GRID_07.domain.entity.Post;
import com.internshalaAssignment.GRID_07.domain.entity.User;
import com.internshalaAssignment.GRID_07.domain.enums.AuthorType;
import com.internshalaAssignment.GRID_07.exception.BusinessRuleViolationException;
import com.internshalaAssignment.GRID_07.exception.TooManyRequestsException;
import com.internshalaAssignment.GRID_07.notification.scheduler.NotificationSweeperScheduler;
import com.internshalaAssignment.GRID_07.notification.service.NotificationDispatchService;
import com.internshalaAssignment.GRID_07.notification.service.PendingNotificationIndexService;
import com.internshalaAssignment.GRID_07.notification.service.PendingNotificationQueueService;
import com.internshalaAssignment.GRID_07.redis.key.RedisKeyBuilder;
import com.internshalaAssignment.GRID_07.repository.BotRepository;
import com.internshalaAssignment.GRID_07.repository.CommentRepository;
import com.internshalaAssignment.GRID_07.repository.PostLikeRepository;
import com.internshalaAssignment.GRID_07.repository.PostRepository;
import com.internshalaAssignment.GRID_07.repository.UserRepository;
import com.internshalaAssignment.GRID_07.service.CommentService;
import com.internshalaAssignment.GRID_07.service.LikeService;
import com.internshalaAssignment.GRID_07.service.PostService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class GuardrailEdgeCaseIT {

	@Autowired private CommentService commentService;
	@Autowired private PostService postService;
	@Autowired private LikeService likeService;
	@Autowired private NotificationDispatchService notificationDispatchService;
	@Autowired private PendingNotificationQueueService pendingNotificationQueueService;
	@Autowired private PendingNotificationIndexService pendingNotificationIndexService;
	@Autowired private NotificationSweeperScheduler notificationSweeperScheduler;

	@Autowired private UserRepository userRepository;
	@Autowired private BotRepository botRepository;
	@Autowired private PostRepository postRepository;
	@Autowired private CommentRepository commentRepository;
	@Autowired private PostLikeRepository postLikeRepository;
	@Autowired private StringRedisTemplate stringRedisTemplate;
	@Autowired private RedisKeyBuilder redisKeyBuilder;

	@BeforeEach
	void checkRedis() {
		Assumptions.assumeTrue(redisAvailable(), "Redis not reachable — skipping integration test.");
		flushRedis();
	}

	@AfterEach
	void cleanState() {
		commentRepository.deleteAllInBatch();
		postLikeRepository.deleteAllInBatch();
		postRepository.deleteAllInBatch();
		botRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
		flushRedis();
	}

	// --- Cooldown cap ---

	@Test
	void botCannotInteractWithSameHumanTwiceWithinCooldownWindow() {
		User user = saveUser("cooldown-user");
		Bot bot = saveBot("cooldown-bot");
		Post post = savePost(user);

		commentService.createComment(post.getId(),
			new CreateCommentRequest(AuthorType.BOT, bot.getId(), null, "first reply"));

		assertThrows(TooManyRequestsException.class, () ->
			commentService.createComment(post.getId(),
				new CreateCommentRequest(AuthorType.BOT, bot.getId(), null, "second reply"))
		);

		assertEquals(1, commentRepository.countByPostId(post.getId()));
	}

	@Test
	void differentBotCanReplyToSameHumanWhileFirstIsOnCooldown() {
		User user = saveUser("cooldown-user-2");
		Bot botA = saveBot("cooldown-bot-a");
		Bot botB = saveBot("cooldown-bot-b");
		Post post = savePost(user);

		commentService.createComment(post.getId(),
			new CreateCommentRequest(AuthorType.BOT, botA.getId(), null, "reply from A"));
		commentService.createComment(post.getId(),
			new CreateCommentRequest(AuthorType.BOT, botB.getId(), null, "reply from B"));

		assertEquals(2, commentRepository.countByPostId(post.getId()));
	}

	// --- Vertical depth cap ---

	@Test
	void commentDepthCannotExceedTwenty() {
		User user = saveUser("depth-user");
		Bot bot = saveBot("depth-bot");
		Post post = savePost(user);

		// Build a chain 20 levels deep using alternating human and bot comments
		// The first comment is human (depth 1)
		CommentResponse current = commentService.createComment(post.getId(),
			new CreateCommentRequest(AuthorType.USER, user.getId(), null, "depth 1"));

		for (int depth = 2; depth <= 20; depth++) {
			current = commentService.createComment(post.getId(),
				new CreateCommentRequest(AuthorType.USER, user.getId(), current.id(), "depth " + depth));
		}

		// Depth 21 should be rejected
		Long parentAtDepth20 = current.id();
		assertThrows(BusinessRuleViolationException.class, () ->
			commentService.createComment(post.getId(),
				new CreateCommentRequest(AuthorType.USER, user.getId(), parentAtDepth20, "depth 21"))
		);

		assertEquals(20, commentRepository.countByPostId(post.getId()));
	}

	// --- Virality score ---

	@Test
	void viralityScoreIncrementsCorrectlyPerInteractionType() {
		User userA = saveUser("virality-user-a");
		User userB = saveUser("virality-user-b");
		User userC = saveUser("virality-user-c");
		Bot bot = saveBot("virality-bot");
		Post post = savePost(userA);

		// Bot reply = +1
		commentService.createComment(post.getId(),
			new CreateCommentRequest(AuthorType.BOT, bot.getId(), null, "bot says hi"));

		String scoreKey = redisKeyBuilder.postViralityScoreKey(post.getId());
		assertEquals("1", stringRedisTemplate.opsForValue().get(scoreKey));

		// Human like = +20
		likeService.likePost(post.getId(), new LikePostRequest(userB.getId()));
		assertEquals("21", stringRedisTemplate.opsForValue().get(scoreKey));

		// Human comment = +50
		commentService.createComment(post.getId(),
			new CreateCommentRequest(AuthorType.USER, userC.getId(), null, "great post"));
		assertEquals("71", stringRedisTemplate.opsForValue().get(scoreKey));
	}

	// --- Notification batching ---

	@Test
	void notificationQueuesBatchWhenUserIsUnderCooldown() {
		User user = saveUser("notif-user");
		Bot botA = saveBot("notif-bot-a");
		Bot botB = saveBot("notif-bot-b");

		// First dispatch — should go immediately (acquires cooldown slot)
		notificationDispatchService.handleBotInteraction(user.getId(), botA.getName(), 999L);

		// Second dispatch — should be queued because user is under 15min cooldown
		notificationDispatchService.handleBotInteraction(user.getId(), botB.getName(), 999L);

		List<String> pending = pendingNotificationQueueService.drainAll(user.getId());
		assertEquals(1, pending.size());
		assertTrue(pending.get(0).contains(botB.getName()));
	}

	@Test
	void pendingIndexTracksUsersWithQueuedNotifications() {
		User user = saveUser("index-user");

		// First call acquires immediate slot and doesn't queue
		notificationDispatchService.handleBotInteraction(user.getId(), "Bot X", 1L);
		// No pending yet
		Set<Long> beforeQueue = pendingNotificationIndexService.getAllUsers();
		assertTrue(beforeQueue.isEmpty() || !beforeQueue.contains(user.getId()));

		// Second call queues, which should add user to index
		notificationDispatchService.handleBotInteraction(user.getId(), "Bot Y", 1L);
		Set<Long> afterQueue = pendingNotificationIndexService.getAllUsers();
		assertTrue(afterQueue.contains(user.getId()));
	}

	@Test
	void sweeperDrainsQueuedNotificationsAndRemovesUserFromIndex() {
		User user = saveUser("sweep-user");

		notificationDispatchService.handleBotInteraction(user.getId(), "Bot A", 1L);
		notificationDispatchService.handleBotInteraction(user.getId(), "Bot B", 1L);
		notificationDispatchService.handleBotInteraction(user.getId(), "Bot C", 1L);

		assertTrue(pendingNotificationIndexService.getAllUsers().contains(user.getId()));

		notificationSweeperScheduler.sweepPendingNotifications();

		assertFalse(pendingNotificationIndexService.getAllUsers().contains(user.getId()));
		assertTrue(pendingNotificationQueueService.drainAll(user.getId()).isEmpty());
	}

	@Test
	void sweeperRemovesStaleIndexedUserWithEmptyQueue() {
		User user = saveUser("stale-index-user");
		pendingNotificationIndexService.addUser(user.getId());

		notificationSweeperScheduler.sweepPendingNotifications();

		assertFalse(pendingNotificationIndexService.getAllUsers().contains(user.getId()));
	}

	// --- Statelessness ---

	@Test
	void botCounterIsStoredInRedisNotInMemory() {
		User user = saveUser("stateless-user");
		Bot bot = saveBot("stateless-bot");
		Post post = savePost(user);

		commentService.createComment(post.getId(),
			new CreateCommentRequest(AuthorType.BOT, bot.getId(), null, "bot reply"));

		String botCountKey = redisKeyBuilder.postBotCountKey(post.getId());
		String storedCount = stringRedisTemplate.opsForValue().get(botCountKey);
		assertNotNull(storedCount, "Bot count must be tracked in Redis, not in memory");
		assertEquals("1", storedCount);
	}

	@Test
	void viralityScoreIsStoredInRedisNotInMemory() {
		User user = saveUser("stateless-user-2");
		Post post = savePost(user);

		likeService.likePost(post.getId(), new LikePostRequest(user.getId()));

		String scoreKey = redisKeyBuilder.postViralityScoreKey(post.getId());
		String storedScore = stringRedisTemplate.opsForValue().get(scoreKey);
		assertNotNull(storedScore, "Virality score must be tracked in Redis, not in memory");
		assertEquals("20", storedScore);
	}

	// --- Data integrity rollback ---

	@Test
	void botCommentFailureReleasesGuardrailsAndDoesNotPersistComment() {
		User user = saveUser("rollback-user");
		Bot bot = saveBot("rollback-bot");
		Post post = savePost(user);

		long beforeCount = commentRepository.countByPostId(post.getId());

		assertThrows(RuntimeException.class, () ->
			commentService.createComment(post.getId(),
				new CreateCommentRequest(AuthorType.BOT, bot.getId(), null, "x".repeat(2101)))
		);

		assertEquals(beforeCount, commentRepository.countByPostId(post.getId()));

		String botCountKey = redisKeyBuilder.postBotCountKey(post.getId());
		String botCount = stringRedisTemplate.opsForValue().get(botCountKey);
		assertTrue(botCount == null || "0".equals(botCount), "Bot counter should be released after failure");

		String cooldownKey = redisKeyBuilder.botHumanCooldownKey(bot.getId(), user.getId());
		assertFalse(Boolean.TRUE.equals(stringRedisTemplate.hasKey(cooldownKey)), "Cooldown key must be cleared");

		String scoreKey = redisKeyBuilder.postViralityScoreKey(post.getId());
		assertEquals(null, stringRedisTemplate.opsForValue().get(scoreKey));
	}

	// --- helpers ---

	private User saveUser(String username) {
		User user = new User();
		user.setUsername(username);
		user.setPremium(false);
		return userRepository.save(user);
	}

	private Bot saveBot(String name) {
		Bot bot = new Bot();
		bot.setName(name);
		bot.setPersonaDescription("test bot");
		return botRepository.save(bot);
	}

	private Post savePost(User author) {
		Post post = new Post();
		post.setAuthorType(AuthorType.USER);
		post.setAuthorId(author.getId());
		post.setContent("test post");
		post.setCreatedAt(Instant.now());
		return postRepository.save(post);
	}

	private boolean redisAvailable() {
		try {
			String pong = stringRedisTemplate.execute(RedisConnection::ping);
			return pong != null;
		} catch (Exception e) {
			return false;
		}
	}

	private void flushRedis() {
		stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
			connection.serverCommands().flushDb();
			return null;
		});
	}
}
