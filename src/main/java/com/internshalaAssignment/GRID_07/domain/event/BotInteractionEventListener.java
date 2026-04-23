package com.internshalaAssignment.GRID_07.domain.event;

import com.internshalaAssignment.GRID_07.notification.service.NotificationDispatchService;
import com.internshalaAssignment.GRID_07.repository.BotRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class BotInteractionEventListener {

	private final BotRepository botRepository;
	private final NotificationDispatchService notificationDispatchService;

	public BotInteractionEventListener(
		BotRepository botRepository,
		NotificationDispatchService notificationDispatchService
	) {
		this.botRepository = botRepository;
		this.notificationDispatchService = notificationDispatchService;
	}

	@TransactionalEventListener
	public void onBotInteraction(BotInteractionEvent event) {
		botRepository.findById(event.botId()).ifPresent(bot ->
			notificationDispatchService.handleBotInteraction(event.targetUserId(), bot.getName(), event.postId())
		);
	}
}
