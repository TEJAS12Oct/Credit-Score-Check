package com.boltpe.creditscore.exception;

public class VendorApiException extends ApplicationException {

    public VendorApiException(String code, String message) {
        super(code, message);
    }
}