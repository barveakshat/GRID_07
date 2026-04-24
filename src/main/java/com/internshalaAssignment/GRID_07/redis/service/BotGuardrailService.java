package com.internshalaAssignment.GRID_07.redis.service;

import com.internshalaAssignment.GRID_07.exception.TooManyRequestsException;
import com.internshalaAssignment.GRID_07.redis.key.RedisKeyBuilder;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

@Service
public class BotGuardrailService {

	private static final long MAX_BOT_REPLIES_PER_POST = 100;
	private static final Duration COOLDOWN_TTL = Duration.ofMinutes(10);

	private final StringRedisTemplate stringRedisTemplate;
	private final RedisKeyBuilder redisKeyBuilder;
	private final RedisScript<Long> claimBotReplySlotScript;

	public BotGuardrailService(
		StringRedisTemplate stringRedisTemplate,
		RedisKeyBuilder redisKeyBuilder,
		@Qualifier("claimBotReplySlotScript") RedisScript<Long> claimBotReplySlotScript
	) {
		this.stringRedisTemplate = stringRedisTemplate;
		this.redisKeyBuilder = redisKeyBuilder;
		this.claimBotReplySlotScript = claimBotReplySlotScript;
	}

	public void claimBotReplySlot(Long postId) {
		String key = redisKeyBuilder.postBotCountKey(postId);
		Long claimed = stringRedisTemplate.execute(
			claimBotReplySlotScript,
			List.of(key),
			String.valueOf(MAX_BOT_REPLIES_PER_POST)
		);
		if (!Long.valueOf(1L).equals(claimed)) {
			throw new TooManyRequestsException("Bot reply cap reached for post " + postId);
		}
	}

	public void claimBotHumanCooldown(Long botId, Long humanId) {
		String key = redisKeyBuilder.botHumanCooldownKey(botId, humanId);
		Boolean cooldownClaimed = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", COOLDOWN_TTL);
		if (!Boolean.TRUE.equals(cooldownClaimed)) {
			throw new TooManyRequestsException("Bot-human cooldown active for bot " + botId + " and human " + humanId);
		}
	}

	public void releaseBotReplySlot(Long postId) {
		String key = redisKeyBuilder.postBotCountKey(postId);
		Long count = stringRedisTemplate.opsForValue().decrement(key);
		if (count != null && count < 0) {
			stringRedisTemplate.opsForValue().set(key, "0");
		}
	}

	public void releaseBotHumanCooldown(Long botId, Long humanId) {
		String key = redisKeyBuilder.botHumanCooldownKey(botId, humanId);
		stringRedisTemplate.delete(key);
	}
}
