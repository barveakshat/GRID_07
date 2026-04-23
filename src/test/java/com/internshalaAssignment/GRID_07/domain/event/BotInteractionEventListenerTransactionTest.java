package com.internshalaAssignment.GRID_07.domain.event;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.internshalaAssignment.GRID_07.domain.entity.Bot;
import com.internshalaAssignment.GRID_07.notification.service.NotificationDispatchService;
import com.internshalaAssignment.GRID_07.repository.BotRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class BotInteractionEventListenerTransactionTest {

	@Autowired
	private org.springframework.context.ApplicationEventPublisher applicationEventPublisher;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@MockBean
	private BotRepository botRepository;

	@MockBean
	private NotificationDispatchService notificationDispatchService;

	@Test
	void dispatchesNotificationAfterTransactionCommit() {
		Bot bot = new Bot();
		bot.setId(7L);
		bot.setName("Bot Alpha");
		bot.setPersonaDescription("test persona");
		when(botRepository.findById(7L)).thenReturn(Optional.of(bot));

		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		transactionTemplate.executeWithoutResult(status ->
			applicationEventPublisher.publishEvent(new BotInteractionEvent(100L, 7L, 25L))
		);

		verify(notificationDispatchService).handleBotInteraction(25L, "Bot Alpha", 100L);
	}

	@Test
	void doesNotDispatchNotificationOnTransactionRollback() {
		Bot bot = new Bot();
		bot.setId(9L);
		bot.setName("Bot Beta");
		bot.setPersonaDescription("test persona");
		when(botRepository.findById(9L)).thenReturn(Optional.of(bot));

		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		try {
			transactionTemplate.executeWithoutResult(status -> {
				applicationEventPublisher.publishEvent(new BotInteractionEvent(200L, 9L, 31L));
				throw new RuntimeException("force rollback");
			});
		} catch (RuntimeException ignored) {
			// expected rollback trigger
		}

		verify(notificationDispatchService, never()).handleBotInteraction(31L, "Bot Beta", 200L);
	}
}
