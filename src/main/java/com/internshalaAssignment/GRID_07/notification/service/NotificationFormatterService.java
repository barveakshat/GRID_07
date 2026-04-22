package com.internshalaAssignment.GRID_07.notification.service;

import org.springframework.stereotype.Service;

@Service
public class NotificationFormatterService {

	public String buildBotInteractionMessage(String botName, Long postId) {
		return "Bot " + botName + " replied to your post " + postId;
	}

	public String buildImmediateLogMessage(Long userId, String message) {
		return "Push Notification Sent to User " + userId + ": " + message;
	}
}
