package com.internshalaAssignment.GRID_07.notification.key;

import org.springframework.stereotype.Component;

@Component
public class NotificationRedisKeyBuilder {

	public String userNotificationCooldownKey(Long userId) {
		return "user:" + userId + ":notif_cooldown";
	}

	public String userPendingNotificationsKey(Long userId) {
		return "user:" + userId + ":pending_notifs";
	}

	public String usersWithPendingNotificationsKey() {
		return "notification:users_with_pending";
	}
}
