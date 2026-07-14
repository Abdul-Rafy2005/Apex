package com.abdulrafy.backend.common.exception;

public class ConflictException extends ApexException {
    public ConflictException(String message) {
        super("conflict", message, 409);
    }
}
