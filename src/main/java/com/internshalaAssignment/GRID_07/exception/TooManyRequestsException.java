package com.internshalaAssignment.GRID_07.exception;

public class TooManyRequestsException extends RuntimeException {

	public TooManyRequestsException(String message) {
		super(message);
	}
}
