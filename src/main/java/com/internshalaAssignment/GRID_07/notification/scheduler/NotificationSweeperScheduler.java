package com.internshalaAssignment.GRID_07.notification.scheduler;

import com.internshalaAssignment.GRID_07.notification.service.NotificationFormatterService;
import com.internshalaAssignment.GRID_07.notification.service.PendingNotificationIndexService;
import com.internshalaAssignment.GRID_07.notification.service.PendingNotificationQueueService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationSweeperScheduler {

	private static final Logger log = LoggerFactory.getLogger(NotificationSweeperScheduler.class);

	private final PendingNotificationIndexService pendingNotificationIndexService;
	private final PendingNotificationQueueService pendingNotificationQueueService;
	private final NotificationFormatterService notificationFormatterService;

	public NotificationSweeperScheduler(
		PendingNotificationIndexService pendingNotificationIndexService,
		PendingNotificationQueueService pendingNotificationQueueService,
		NotificationFormatterService notificationFormatterService
	) {
		this.pendingNotificationIndexService = pendingNotificationIndexService;
		this.pendingNotificationQueueService = pendingNotificationQueueService;
		this.notificationFormatterService = notificationFormatterService;
	}

	@Scheduled(cron = "${app.notification.sweep-cron}")
	public void sweepPendingNotifications() {
		for (Long userId : pendingNotificationIndexService.getAllUsers()) {
			List<String> notifications = pendingNotificationQueueService.drainAll(userId);
			if (notifications.isEmpty()) {
				pendingNotificationIndexService.removeUser(userId);
				continue;
			}

			String message = notificationFormatterService.buildSummaryLogMessage(
				notifications.get(0),
				notifications.size()
			);
			log.info("{} [userId={}]", message, userId);
			pendingNotificationIndexService.removeUser(userId);
		}
	}
}
