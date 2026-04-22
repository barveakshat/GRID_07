package com.internshalaAssignment.GRID_07.redis.service;

import com.internshalaAssignment.GRID_07.exception.TooManyRequestsException;
import com.internshalaAssignment.GRID_07.redis.key.RedisKeyBuilder;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class BotGuardrailService {

	private static final long MAX_BOT_REPLIES_PER_POST = 100;
	private static final Duration COOLDOWN_TTL = Duration.ofMinutes(10);

	private final StringRedisTemplate stringRedisTemplate;
	private final RedisKeyBuilder redisKeyBuilder;

	public BotGuardrailService(StringRedisTemplate stringRedisTemplate, RedisKeyBuilder redisKeyBuilder) {
		this.stringRedisTemplate = stringRedisTemplate;
		this.redisKeyBuilder = redisKeyBuilder;
	}

	public void claimBotReplySlot(Long postId) {
		String key = redisKeyBuilder.postBotCountKey(postId);
		Long botCount = stringRedisTemplate.opsForValue().increment(key);
		if (botCount != null && botCount > MAX_BOT_REPLIES_PER_POST) {
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
}
