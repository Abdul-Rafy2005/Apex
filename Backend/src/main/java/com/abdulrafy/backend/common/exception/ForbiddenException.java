package com.abdulrafy.backend.common.exception;

public class ForbiddenException extends ApexException {
    public ForbiddenException(String message) {
        super("forbidden", message, 403);
    }
}
