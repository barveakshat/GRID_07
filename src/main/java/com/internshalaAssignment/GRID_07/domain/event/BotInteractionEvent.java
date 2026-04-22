package com.internshalaAssignment.GRID_07.domain.event;

public record BotInteractionEvent(Long postId, Long botId, Long targetUserId) {
}
