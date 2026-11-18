package com.codebuzzers.codebuzzcore.job;

public class TransientFailure extends RuntimeException {
    public TransientFailure(String message) {
        super(message);
    }
}
