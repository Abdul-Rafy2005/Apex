package com.abdulrafy.backend.common.exception;

public class InsufficientHoldingsException extends ApexException {
    public InsufficientHoldingsException(String message) {
        super("insufficient-holdings", message, 400);
    }
}
