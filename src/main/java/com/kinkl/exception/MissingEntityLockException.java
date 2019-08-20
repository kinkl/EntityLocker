package com.kinkl.exception;

public class MissingEntityLockException extends RuntimeException {

    public MissingEntityLockException(String message) {
        super(message);
    }
}
