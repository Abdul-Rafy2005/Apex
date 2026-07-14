package com.abdulrafy.backend.common.exception;

public class InsufficientFundsException extends ApexException {
    public InsufficientFundsException(String message) {
        super("insufficient-funds", message, 400);
    }
}
