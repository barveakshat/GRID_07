package com.internshalaAssignment.GRID_07.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationDispatchService {

	private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

	private final NotificationThrottleService notificationThrottleService;
	private final PendingNotificationQueueService pendingNotificationQueueService;
	private final PendingNotificationIndexService pendingNotificationIndexService;
	private final NotificationFormatterService notificationFormatterService;

	public NotificationDispatchService(
		NotificationThrottleService notificationThrottleService,
		PendingNotificationQueueService pendingNotificationQueueService,
		PendingNotificationIndexService pendingNotificationIndexService,
		NotificationFormatterService notificationFormatterService
	) {
		this.notificationThrottleService = notificationThrottleService;
		this.pendingNotificationQueueService = pendingNotificationQueueService;
		this.pendingNotificationIndexService = pendingNotificationIndexService;
		this.notificationFormatterService = notificationFormatterService;
	}

	public void handleBotInteraction(Long targetUserId, String botName, Long postId) {
		String message = notificationFormatterService.buildBotInteractionMessage(botName, postId);
		boolean sendImmediately = notificationThrottleService.tryAcquireImmediateNotificationSlot(targetUserId);

		if (sendImmediately) {
			log.info(notificationFormatterService.buildImmediateLogMessage(targetUserId, message));
			return;
		}

		pendingNotificationQueueService.enqueue(targetUserId, message);
		pendingNotificationIndexService.addUser(targetUserId);
	}
}
