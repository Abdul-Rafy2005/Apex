package com.abdulrafy.backend.common.exception;

public class NotFoundException extends ApexException {
    public NotFoundException(String message) {
        super("not-found", message, 404);
    }
}
