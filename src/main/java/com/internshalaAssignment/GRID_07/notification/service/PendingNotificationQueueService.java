package com.internshalaAssignment.GRID_07.notification.service;

import com.internshalaAssignment.GRID_07.notification.key.NotificationRedisKeyBuilder;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PendingNotificationQueueService {

	private final StringRedisTemplate stringRedisTemplate;
	private final NotificationRedisKeyBuilder notificationRedisKeyBuilder;

	public PendingNotificationQueueService(
		StringRedisTemplate stringRedisTemplate,
		NotificationRedisKeyBuilder notificationRedisKeyBuilder
	) {
		this.stringRedisTemplate = stringRedisTemplate;
		this.notificationRedisKeyBuilder = notificationRedisKeyBuilder;
	}

	public void enqueue(Long userId, String message) {
		String queueKey = notificationRedisKeyBuilder.userPendingNotificationsKey(userId);
		stringRedisTemplate.opsForList().rightPush(queueKey, message);
	}

	public List<String> drainAll(Long userId) {
		String queueKey = notificationRedisKeyBuilder.userPendingNotificationsKey(userId);
		List<String> messages = new ArrayList<>();
		while (true) {
			String message = stringRedisTemplate.opsForList().leftPop(queueKey);
			if (message == null) {
				break;
			}
			messages.add(message);
		}
		return messages;
	}
}
