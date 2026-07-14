package com.abdulrafy.backend.common.exception;

public class ApexException extends RuntimeException {

    private final String type;
    private final int status;

    public ApexException(String type, String message, int status) {
        super(message);
        this.type = type;
        this.status = status;
    }

    public String getType() {
        return type;
    }

    public int getStatus() {
        return status;
    }
}
