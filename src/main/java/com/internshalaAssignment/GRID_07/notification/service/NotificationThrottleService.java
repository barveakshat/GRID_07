package com.internshalaAssignment.GRID_07.notification.service;

import com.internshalaAssignment.GRID_07.notification.key.NotificationRedisKeyBuilder;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationThrottleService {

	private static final Duration NOTIFICATION_COOLDOWN = Duration.ofMinutes(15);

	private final StringRedisTemplate stringRedisTemplate;
	private final NotificationRedisKeyBuilder notificationRedisKeyBuilder;

	public NotificationThrottleService(
		StringRedisTemplate stringRedisTemplate,
		NotificationRedisKeyBuilder notificationRedisKeyBuilder
	) {
		this.stringRedisTemplate = stringRedisTemplate;
		this.notificationRedisKeyBuilder = notificationRedisKeyBuilder;
	}

	public boolean tryAcquireImmediateNotificationSlot(Long userId) {
		String cooldownKey = notificationRedisKeyBuilder.userNotificationCooldownKey(userId);
		Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(cooldownKey, "1", NOTIFICATION_COOLDOWN);
		return Boolean.TRUE.equals(acquired);
	}
}
