package com.internshalaAssignment.GRID_07.redis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class RedisScriptConfig {

	@Bean
	public RedisScript<Long> claimBotReplySlotScript() {
		DefaultRedisScript<Long> script = new DefaultRedisScript<>();
		script.setLocation(new ClassPathResource("redis/claim_bot_reply_slot.lua"));
		script.setResultType(Long.class);
		return script;
	}
}

