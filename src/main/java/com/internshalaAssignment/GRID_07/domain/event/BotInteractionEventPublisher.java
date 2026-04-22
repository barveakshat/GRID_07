package com.internshalaAssignment.GRID_07.domain.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class BotInteractionEventPublisher {

	private final ApplicationEventPublisher applicationEventPublisher;

	public BotInteractionEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

	public void publish(Long postId, Long botId, Long targetUserId) {
		applicationEventPublisher.publishEvent(new BotInteractionEvent(postId, botId, targetUserId));
	}
}
