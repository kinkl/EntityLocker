package com.kinkl.exception;

public class OtherThreadEntityUnlockAttemptException extends RuntimeException {

    public OtherThreadEntityUnlockAttemptException(String message) {
        super(message);
    }
}
