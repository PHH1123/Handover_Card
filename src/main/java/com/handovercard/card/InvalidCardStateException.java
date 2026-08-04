package com.handovercard.card;

public class InvalidCardStateException extends RuntimeException {

    public InvalidCardStateException(String message) {
        super(message);
    }
}
