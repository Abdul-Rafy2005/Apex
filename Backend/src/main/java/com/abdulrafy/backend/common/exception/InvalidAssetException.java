package com.abdulrafy.backend.common.exception;

public class InvalidAssetException extends ApexException {
    public InvalidAssetException(String message) {
        super("invalid-asset", message, 404);
    }
}
