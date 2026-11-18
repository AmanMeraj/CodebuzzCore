package com.codebuzzers.codebuzzcore.meta;

public class MetaApiException extends RuntimeException {

    private final int httpStatus;
    private final int code;
    private final String detail;

    public MetaApiException(int httpStatus, int code, String message) {
        this(httpStatus, code, message, null);
    }

    /** detail is Meta's own error text; it is for logs only and never sent to API clients. */
    public MetaApiException(int httpStatus, int code, String message, String detail) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
        this.detail = detail;
    }

    public String detail() {
        return detail;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public int code() {
        return code;
    }

    public boolean tokenInvalid() {
        return code == 190 || httpStatus == 401 || httpStatus == 403;
    }

    public boolean rateLimited() {
        return httpStatus == 429 || code == 4 || code == 17 || code == 32 || code == 613;
    }

    public boolean transientFailure() {
        return httpStatus >= 500 || httpStatus == 408 || code == 1 || code == 2 || rateLimited();
    }
}