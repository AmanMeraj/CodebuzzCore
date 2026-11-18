package com.codebuzzers.codebuzzcore.job;

public class PermanentFailure extends RuntimeException {
    public PermanentFailure(String message) {
        super(message);
    }
}
