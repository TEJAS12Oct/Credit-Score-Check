package com.boltpe.creditscore.exception;

public class RequestValidationException extends ApplicationException {
    public RequestValidationException(String code, String message) {
        super(code, message);
    }
}
