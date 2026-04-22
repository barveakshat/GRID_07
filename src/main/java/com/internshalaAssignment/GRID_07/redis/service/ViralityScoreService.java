package com.internshalaAssignment.GRID_07.redis.service;

import com.internshalaAssignment.GRID_07.domain.enums.InteractionType;
import com.internshalaAssignment.GRID_07.redis.key.RedisKeyBuilder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ViralityScoreService {

	private final StringRedisTemplate stringRedisTemplate;
	private final RedisKeyBuilder redisKeyBuilder;

	public ViralityScoreService(StringRedisTemplate stringRedisTemplate, RedisKeyBuilder redisKeyBuilder) {
		this.stringRedisTemplate = stringRedisTemplate;
		this.redisKeyBuilder = redisKeyBuilder;
	}

	public long incrementScore(Long postId, InteractionType interactionType) {
		String key = redisKeyBuilder.postViralityScoreKey(postId);
		Long score = stringRedisTemplate.opsForValue().increment(key, interactionType.score());
		return score == null ? 0L : score;
	}
}
