package com.boltpe.creditscore.exception;

import com.boltpe.creditscore.dto.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.MissingRequestValueException;
import org.springframework.web.server.ServerWebInputException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /*
     * Business/request validation errors
     */
    @ExceptionHandler(RequestValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessValidation(
            RequestValidationException ex) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(
                        ex.getCode(),
                        ex.getMessage()
                ));
    }

    /*
     * Spring MVC bean validation errors from @Valid request body
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {

        String message = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("VALIDATION_ERROR", message));
    }

    /*
     * WebFlux bean validation errors from @Valid request body
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            WebExchangeBindException ex) {

        String message = ex.getFieldErrors()
                .stream()
                .map(error ->
                        error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(
                        "VALIDATION_ERROR",
                        message
                ));
    }

    /*
     * Validation errors from request parameters/path variables
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
            ConstraintViolationException ex) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(
                        "VALIDATION_ERROR",
                        "Invalid request parameters."
                ));
    }

    /*
     * Missing required header/request value
     * Example: X-Registered-Mobile missing
     */
    @ExceptionHandler(MissingRequestValueException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestValue(
            MissingRequestValueException ex) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(
                        "INVALID_REQUEST",
                        "Required request information is missing."
                ));
    }

    /*
     * Malformed JSON / unreadable request body
     */
    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequestBody(
            ServerWebInputException ex) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(
                        "INVALID_REQUEST",
                        "Invalid request body."
                ));
    }

    /*
     * Vendor/network/service unavailable errors
     */
    @ExceptionHandler(VendorApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleVendorError(
            VendorApiException ex) {

        return ResponseEntity
                .status("RATE_LIMITED".equals(ex.getCode()) ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.fail(
                        ex.getCode(),
                        ex.getMessage()
                ));
    }

    /*
     * Other known application exceptions
     */
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiResponse<Void>> handleApplicationException(
            ApplicationException ex) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(
                        ex.getCode(),
                        ex.getMessage()
                ));
    }

    /*
     * Unexpected/internal application errors
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(
            Exception ex) {

        log.error(
                "Unexpected error while processing get OTP request",
                ex
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(
                        "INTERNAL_SERVER_ERROR",
                        "Something went wrong. Please try again later."
                ));
    }
}