package com.internshalaAssignment.GRID_07.domain.enums;

public enum InteractionType {
	BOT_REPLY(1),
	HUMAN_LIKE(20),
	HUMAN_COMMENT(50);

	private final long score;

	InteractionType(long score) {
		this.score = score;
	}

	public long score() {
		return score;
	}
}
