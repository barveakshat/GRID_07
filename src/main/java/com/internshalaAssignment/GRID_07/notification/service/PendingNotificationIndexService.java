package com.internshalaAssignment.GRID_07.notification.service;

import com.internshalaAssignment.GRID_07.notification.key.NotificationRedisKeyBuilder;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
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

	public Set<Long> getAllUsers() {
		String indexKey = notificationRedisKeyBuilder.usersWithPendingNotificationsKey();
		Set<String> rawUsers = stringRedisTemplate.opsForSet().members(indexKey);
		if (rawUsers == null || rawUsers.isEmpty()) {
			return Collections.emptySet();
		}
		return rawUsers.stream().map(Long::valueOf).collect(Collectors.toSet());
	}

	public void removeUser(Long userId) {
		String indexKey = notificationRedisKeyBuilder.usersWithPendingNotificationsKey();
		stringRedisTemplate.opsForSet().remove(indexKey, String.valueOf(userId));
	}
}
