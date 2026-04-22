package com.internshalaAssignment.GRID_07.redis.key;

import org.springframework.stereotype.Component;

@Component
public class RedisKeyBuilder {

	public String postViralityScoreKey(Long postId) {
		return "post:" + postId + ":virality_score";
	}

	public String postBotCountKey(Long postId) {
		return "post:" + postId + ":bot_count";
	}

	public String botHumanCooldownKey(Long botId, Long humanId) {
		return "cooldown:bot_" + botId + ":human_" + humanId;
	}
}
