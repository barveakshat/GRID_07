package com.internshalaAssignment.GRID_07.redis.key;

import org.springframework.stereotype.Component;

@Component
public class RedisKeyBuilder {

	public String postViralityScoreKey(Long postId) {
		return "post:" + postId + ":virality_score";
	}
}
