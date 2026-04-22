package com.internshalaAssignment.GRID_07.domain.event;

import com.internshalaAssignment.GRID_07.notification.service.NotificationDispatchService;
import com.internshalaAssignment.GRID_07.repository.BotRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

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

	@EventListener
	public void onBotInteraction(BotInteractionEvent event) {
		botRepository.findById(event.botId()).ifPresent(bot ->
			notificationDispatchService.handleBotInteraction(event.targetUserId(), bot.getName(), event.postId())
		);
	}
}
