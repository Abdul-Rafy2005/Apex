package com.abdulrafy.backend.common.exception;

public class UnauthorizedException extends ApexException {
    public UnauthorizedException(String message) {
        super("unauthorized", message, 401);
    }
}
