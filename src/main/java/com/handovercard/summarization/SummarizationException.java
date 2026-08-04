package com.handovercard.summarization;

public class SummarizationException extends RuntimeException {

    public SummarizationException(String message) {
        super(message);
    }

    public SummarizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
