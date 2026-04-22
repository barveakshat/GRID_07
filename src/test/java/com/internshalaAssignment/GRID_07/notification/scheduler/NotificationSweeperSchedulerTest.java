package com.internshalaAssignment.GRID_07.notification.scheduler;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.internshalaAssignment.GRID_07.notification.service.NotificationFormatterService;
import com.internshalaAssignment.GRID_07.notification.service.PendingNotificationIndexService;
import com.internshalaAssignment.GRID_07.notification.service.PendingNotificationQueueService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationSweeperSchedulerTest {

	@Mock
	private PendingNotificationIndexService pendingNotificationIndexService;

	@Mock
	private PendingNotificationQueueService pendingNotificationQueueService;

	@Mock
	private NotificationFormatterService notificationFormatterService;

	@InjectMocks
	private NotificationSweeperScheduler notificationSweeperScheduler;

	@Test
	void sweeperRemovesUserWhenQueueIsEmpty() {
		when(pendingNotificationIndexService.getAllUsers()).thenReturn(Set.of(11L));
		when(pendingNotificationQueueService.drainAll(11L)).thenReturn(List.of());

		notificationSweeperScheduler.sweepPendingNotifications();

		verify(pendingNotificationIndexService).removeUser(11L);
		verify(notificationFormatterService, never()).buildSummaryLogMessage(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
	}

	@Test
	void sweeperBuildsSummaryAndRemovesUserWhenNotificationsExist() {
		when(pendingNotificationIndexService.getAllUsers()).thenReturn(Set.of(7L));
		when(pendingNotificationQueueService.drainAll(7L)).thenReturn(List.of("Bot Alpha replied", "Bot Beta replied"));
		when(notificationFormatterService.buildSummaryLogMessage("Bot Alpha replied", 2))
			.thenReturn("Summarized Push Notification: Bot Alpha replied and 1 others interacted with your posts.");

		notificationSweeperScheduler.sweepPendingNotifications();

		verify(notificationFormatterService).buildSummaryLogMessage("Bot Alpha replied", 2);
		verify(pendingNotificationIndexService).removeUser(7L);
	}
}
