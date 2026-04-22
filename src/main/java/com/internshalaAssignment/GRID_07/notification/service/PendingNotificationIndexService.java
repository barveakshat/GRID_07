package com.internshalaAssignment.GRID_07.notification.service;

import com.internshalaAssignment.GRID_07.notification.key.NotificationRedisKeyBuilder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PendingNotificationIndexService {

	private final StringRedisTemplate stringRedisTemplate;
	private final NotificationRedisKeyBuilder notificationRedisKeyBuilder;

	public PendingNotificationIndexService(
		StringRedisTemplate stringRedisTemplate,
		NotificationRedisKeyBuilder notificationRedisKeyBuilder
	) {
		this.stringRedisTemplate = stringRedisTemplate;
		this.notificationRedisKeyBuilder = notificationRedisKeyBuilder;
	}

	public void addUser(Long userId) {
		String indexKey = notificationRedisKeyBuilder.usersWithPendingNotificationsKey();
		stringRedisTemplate.opsForSet().add(indexKey, String.valueOf(userId));
	}
}
