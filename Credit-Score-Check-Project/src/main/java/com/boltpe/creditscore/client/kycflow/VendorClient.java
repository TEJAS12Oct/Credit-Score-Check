package com.boltpe.creditscore.client.kycflow;

import com.boltpe.creditscore.client.kycflow.dto.request.VendorGetOtpRequest;
import com.boltpe.creditscore.client.kycflow.dto.request.VendorSubmitOtpRequest;
import com.boltpe.creditscore.client.kycflow.dto.response.VendorGetOtpResponse;
import com.boltpe.creditscore.client.kycflow.dto.response.VendorSubmitOtpResponse;
import com.boltpe.creditscore.config.VendorProperties;
import com.boltpe.creditscore.exception.VendorApiException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Slf4j
@Component
public class VendorClient {

    private static final String SERVICE_UNAVAILABLE_CODE = "CREDIT_SCORE_SERVICE_UNAVAILABLE";
    private static final String SERVICE_UNAVAILABLE_MESSAGE =
            "Credit score service is temporarily unavailable. Please try again later.";
    private static final Duration RETRY_BACKOFF = Duration.ofMillis(200);

    private final WebClient webClient;
    private final VendorProperties properties;

    public VendorClient(WebClient vendorWebClient, VendorProperties properties) {
        this.webClient = vendorWebClient;
        this.properties = properties;
    }

    public VendorCallResult generateOtp(VendorGetOtpRequest request) {
        try {
            VendorCallResult result = webClient.post()
                    .uri(properties.generateOtpPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("clientId", properties.clientId())
                    .header("secretKey", properties.secretKey())
                    .bodyValue(request)
                    .exchangeToMono(response -> {
                        int status = response.statusCode().value();

                        if (isVendorAuthFailure(response.statusCode())) {
                            logVendorAuthFailure("GENERATE_OTP", status);
                        }

                        if (response.statusCode().value() == 429) {
                            return response.bodyToMono(VendorGetOtpResponse.class)
                                    .defaultIfEmpty(new VendorGetOtpResponse(status, "Rate limited", null))
                                    .flatMap(body -> Mono.error(new VendorRateLimitException(status, body)));
                        }

                        if (response.statusCode().is5xxServerError()) {
                            return response.createException().flatMap(Mono::error);
                        }

                        return response.bodyToMono(VendorGetOtpResponse.class)
                                .defaultIfEmpty(new VendorGetOtpResponse(status, "Empty vendor response", null))
                                .map(body -> new VendorCallResult(status, body));
                    })
                    .retryWhen(retrySpec())
                    .block();

            if (result == null) {
                throw new IllegalStateException("Empty vendor result");
            }
            return result;
        } catch (VendorRateLimitException exception) {
            return new VendorCallResult(exception.httpStatus(), exception.getOtpBody());
        } catch (Exception exception) {
            throw new VendorApiException(SERVICE_UNAVAILABLE_CODE, SERVICE_UNAVAILABLE_MESSAGE);
        }
    }

    public VendorSubmitCallResult submitOtp(VendorSubmitOtpRequest request) {
        try {
            VendorSubmitCallResult result = webClient.post()
                    .uri(properties.submitOtpPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("clientId", properties.clientId())
                    .header("secretKey", properties.secretKey())
                    .bodyValue(request)
                    .exchangeToMono(response -> {
                        int status = response.statusCode().value();

                        if (isVendorAuthFailure(response.statusCode())) {
                            logVendorAuthFailure("SUBMIT_OTP", status);
                        }

                        if (response.statusCode().value() == 429) {
                            return response.bodyToMono(VendorSubmitOtpResponse.class)
                                    .defaultIfEmpty(new VendorSubmitOtpResponse(status, "Rate limited", null))
                                    .flatMap(body -> Mono.error(new VendorRateLimitException(status, body)));
                        }

                        if (response.statusCode().is5xxServerError()) {
                            return response.createException().flatMap(Mono::error);
                        }

                        return response.bodyToMono(VendorSubmitOtpResponse.class)
                                .defaultIfEmpty(new VendorSubmitOtpResponse(status, "Empty vendor response", null))
                                .map(body -> new VendorSubmitCallResult(status, body));
                    })
                    // Do not automatically retry a potentially billable bureau pull.
                    .block();

            if (result == null) {
                throw new IllegalStateException("Empty vendor result");
            }
            return result;
        } catch (VendorRateLimitException exception) {
            return new VendorSubmitCallResult(exception.httpStatus(), exception.getSubmitOtpBody());
        } catch (VendorApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new VendorApiException(SERVICE_UNAVAILABLE_CODE, SERVICE_UNAVAILABLE_MESSAGE);
        }
    }

    private Retry retrySpec() {
        return Retry.backoff(Math.max(0, properties.maxRetries()), RETRY_BACKOFF)
                .filter(this::isRetryable)
                .onRetryExhaustedThrow((spec, signal) -> signal.failure());
    }

    private boolean isRetryable(Throwable throwable) {
        return throwable instanceof VendorRateLimitException
                || throwable instanceof WebClientRequestException
                || (throwable instanceof WebClientResponseException responseException
                        && responseException.getStatusCode().is5xxServerError());
    }

    private boolean isVendorAuthFailure(HttpStatusCode status) {
        return status.value() == 401 || status.value() == 403;
    }

    private void logVendorAuthFailure(String operation, int httpStatus) {
        // Deliberately logs only operation and HTTP status. Never log clientId, secretKey,
        // Authorization headers, PAN, mobile, OTP, request body, or vendor response body here.
        log.error("credit_score_vendor_auth_failure operation={} httpStatus={}", operation, httpStatus);
    }

    public record VendorSubmitCallResult(int httpStatus, VendorSubmitOtpResponse body) {}

    public record VendorCallResult(int httpStatus, VendorGetOtpResponse body) {}

    private static final class VendorRateLimitException extends RuntimeException {
        private final int httpStatus;
        private final Object body;

        private VendorRateLimitException(int httpStatus, Object body) {
            super("Vendor rate limited the request");
            this.httpStatus = httpStatus;
            this.body = body;
        }

        private int httpStatus() {
            return httpStatus;
        }

        private VendorGetOtpResponse getOtpBody() {
            return (VendorGetOtpResponse) body;
        }

        private VendorSubmitOtpResponse getSubmitOtpBody() {
            return (VendorSubmitOtpResponse) body;
        }
    }
}
