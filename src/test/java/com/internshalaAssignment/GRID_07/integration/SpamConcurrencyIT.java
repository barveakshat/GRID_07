package com.internshalaAssignment.GRID_07.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.internshalaAssignment.GRID_07.api.dto.request.CreateCommentRequest;
import com.internshalaAssignment.GRID_07.domain.entity.Bot;
import com.internshalaAssignment.GRID_07.domain.entity.Post;
import com.internshalaAssignment.GRID_07.domain.entity.User;
import com.internshalaAssignment.GRID_07.domain.enums.AuthorType;
import com.internshalaAssignment.GRID_07.exception.TooManyRequestsException;
import com.internshalaAssignment.GRID_07.repository.BotRepository;
import com.internshalaAssignment.GRID_07.repository.CommentRepository;
import com.internshalaAssignment.GRID_07.repository.PostLikeRepository;
import com.internshalaAssignment.GRID_07.repository.PostRepository;
import com.internshalaAssignment.GRID_07.repository.UserRepository;
import com.internshalaAssignment.GRID_07.service.CommentService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SpamConcurrencyIT {

	private static final int TOTAL_REQUESTS = 200;
	private static final int BOT_REPLY_CAP = 100;

	@Autowired
	private CommentService commentService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private BotRepository botRepository;

	@Autowired
	private PostRepository postRepository;

	@Autowired
	private PostLikeRepository postLikeRepository;

	@Autowired
	private CommentRepository commentRepository;

	@Autowired
	private StringRedisTemplate stringRedisTemplate;

	@AfterEach
	void cleanState() {
		commentRepository.deleteAllInBatch();
		postLikeRepository.deleteAllInBatch();
		postRepository.deleteAllInBatch();
		botRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
		flushRedis();
	}

	@Test
	void shouldPersistExactlyHundredBotCommentsWhenTwoHundredRequestsHitSamePost() throws InterruptedException {
		Assumptions.assumeTrue(redisAvailable(), "Redis is not reachable for concurrency integration test.");
		flushRedis();

		User postAuthor = new User();
		postAuthor.setUsername("spam-target-user");
		postAuthor.setPremium(false);
		postAuthor = userRepository.save(postAuthor);

		Post post = new Post();
		post.setAuthorType(AuthorType.USER);
		post.setAuthorId(postAuthor.getId());
		post.setContent("Concurrency guardrail post");
		post.setCreatedAt(Instant.now());
		post = postRepository.save(post);
		Long postId = post.getId();

		List<Bot> bots = new ArrayList<>();
		for (int i = 0; i < TOTAL_REQUESTS; i++) {
			Bot bot = new Bot();
			bot.setName("spam-bot-" + i);
			bot.setPersonaDescription("concurrency test bot " + i);
			bots.add(bot);
		}
		bots = botRepository.saveAll(bots);

		AtomicInteger rejectedByCap = new AtomicInteger();
		ConcurrentLinkedQueue<Throwable> unexpectedFailures = new ConcurrentLinkedQueue<>();
		CountDownLatch ready = new CountDownLatch(TOTAL_REQUESTS);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(TOTAL_REQUESTS);

		ExecutorService executor = Executors.newFixedThreadPool(TOTAL_REQUESTS);
		try {
			for (Bot bot : bots) {
				executor.submit(() -> {
					ready.countDown();
					try {
						start.await();
						commentService.createComment(
							postId,
							new CreateCommentRequest(AuthorType.BOT, bot.getId(), null, "bot reply " + bot.getId())
						);
					} catch (TooManyRequestsException exception) {
						rejectedByCap.incrementAndGet();
					} catch (Throwable throwable) {
						unexpectedFailures.add(throwable);
					} finally {
						done.countDown();
					}
				});
			}

			assertTrue(ready.await(30, TimeUnit.SECONDS), "Workers did not become ready in time");
			start.countDown();
			assertTrue(done.await(60, TimeUnit.SECONDS), "Workers did not finish in time");
		} finally {
			executor.shutdownNow();
		}

		assertTrue(unexpectedFailures.isEmpty(), "Unexpected failures: " + unexpectedFailures);
		assertEquals(BOT_REPLY_CAP, commentRepository.countByPostId(postId));
		assertEquals(TOTAL_REQUESTS - BOT_REPLY_CAP, rejectedByCap.get());
	}

	private boolean redisAvailable() {
		try {
			String pong = stringRedisTemplate.execute(RedisConnection::ping);
			return pong != null;
		} catch (Exception exception) {
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
