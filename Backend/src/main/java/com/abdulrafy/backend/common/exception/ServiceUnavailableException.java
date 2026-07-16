package com.abdulrafy.backend.common.exception;

public class ServiceUnavailableException extends ApexException {
    public ServiceUnavailableException(String message) {
        super("service-unavailable", message, 503);
    }
}
