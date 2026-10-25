package com.boltpe.creditscore.client.kycflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.HttpStatus.*;

import com.boltpe.creditscore.client.kycflow.dto.request.VendorGetOtpRequest;
import com.boltpe.creditscore.client.kycflow.dto.request.VendorSubmitOtpRequest;
import com.boltpe.creditscore.config.VendorProperties;
import com.boltpe.creditscore.exception.VendorApiException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class VendorClientTest {
    private VendorProperties props(int retries) {
        return new VendorProperties("https://vendor.test", "/genOtp/creditScoreCheckV4", "/verifyOtp/getCreditScoreCheckV4", "client", "secret", 1000, 1000, retries);
    }

    private VendorGetOtpRequest request() {
        return new VendorGetOtpRequest("Tejas", "Jawale", "ABCDE1234F", "20000101", "9876543210", "a@b.com", "male", "411001", "consent");
    }

    @Test
    void returnsSuccessfulVendorResponse() {
        String json = """
                {"code":200,"message":"SMS sent successfully !","result":{"sendOtpResponse":{"return":true,"request_id":"req-1","message":["SMS sent successfully."]},"token":"token-1"}}
                """;
        WebClient web = WebClient.builder().exchangeFunction(req -> {
            assertEquals("client", req.headers().getFirst("clientId"));
            assertEquals("secret", req.headers().getFirst("secretKey"));
            return Mono.just(ClientResponse.create(OK).header("Content-Type", MediaType.APPLICATION_JSON_VALUE).body(json).build());
        }).build();

        var result = new VendorClient(web, props(0)).generateOtp(request());
        assertEquals(200, result.httpStatus());
        assertTrue(result.body().isSuccess());
        assertEquals("req-1", result.body().result().sendOtpResponse().requestId());
    }

    @Test
    void preservesVendor400ForStableServiceMapping() {
        WebClient web = WebClient.builder().exchangeFunction(req -> Mono.just(
                ClientResponse.create(BAD_REQUEST).header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"code\":400,\"message\":\"Invalid Data\"}").build())).build();
        var result = new VendorClient(web, props(0)).generateOtp(request());
        assertEquals(400, result.httpStatus());
        assertEquals(400, result.body().code());
    }

    @Test
    void retriesVendor5xxThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        String ok = "{\"code\":200,\"message\":\"ok\",\"result\":{\"sendOtpResponse\":{\"return\":true,\"request_id\":\"r\",\"message\":[\"ok\"]},\"token\":\"t\"}}";
        WebClient web = WebClient.builder().exchangeFunction(req -> {
            if (calls.incrementAndGet() == 1)
                return Mono.just(ClientResponse.create(SERVICE_UNAVAILABLE).body("down").build());
            return Mono.just(ClientResponse.create(OK).header("Content-Type", MediaType.APPLICATION_JSON_VALUE).body(ok).build());
        }).build();
        var result = new VendorClient(web, props(1)).generateOtp(request());
        assertEquals(200, result.httpStatus());
        assertEquals(2, calls.get());
    }

    @Test
    void convertsExhausted5xxToControlledException() {
        WebClient web = WebClient.builder().exchangeFunction(req -> Mono.just(ClientResponse.create(INTERNAL_SERVER_ERROR).body("down").build())).build();
        VendorApiException ex = assertThrows(VendorApiException.class, () -> new VendorClient(web, props(0)).generateOtp(request()));
        assertEquals("CREDIT_SCORE_SERVICE_UNAVAILABLE", ex.getCode());
    }

    @Test
    void retries429ThenGenerateOtpSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        String ok = "{\"code\":200,\"message\":\"ok\",\"result\":{\"sendOtpResponse\":{\"return\":true,\"request_id\":\"r-429\",\"message\":[\"ok\"]},\"token\":\"t\"}}";
        WebClient web = WebClient.builder().exchangeFunction(req -> {
            if (calls.incrementAndGet() == 1) {
                return Mono.just(ClientResponse.create(TOO_MANY_REQUESTS)
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"code\":429,\"message\":\"Too Many Requests\"}").build());
            }
            return Mono.just(ClientResponse.create(OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body(ok).build());
        }).build();

        var result = new VendorClient(web, props(1)).generateOtp(request());

        assertEquals(200, result.httpStatus());
        assertEquals(2, calls.get());
    }

    @Test
    void returnsFinal429AfterBoundedGenerateOtpRetries() {
        AtomicInteger calls = new AtomicInteger();
        WebClient web = WebClient.builder().exchangeFunction(req -> {
            calls.incrementAndGet();
            return Mono.just(ClientResponse.create(TOO_MANY_REQUESTS)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"code\":429,\"message\":\"Too Many Requests\"}").build());
        }).build();

        var result = new VendorClient(web, props(2)).generateOtp(request());

        assertEquals(429, result.httpStatus());
        assertEquals(429, result.body().code());
        assertEquals(3, calls.get()); // first call + 2 bounded retries
    }

    @Test
    void submitOtp429ShouldNotRetry() {
        AtomicInteger calls = new AtomicInteger();
        WebClient web = WebClient.builder().exchangeFunction(req -> {
            calls.incrementAndGet();
            return Mono.just(ClientResponse.create(TOO_MANY_REQUESTS)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"code\":429,\"message\":\"Too Many Requests\"}").build());
        }).build();

        var result = new VendorClient(web, props(2))
                .submitOtp(new VendorSubmitOtpRequest("token", "123456", "consent"));

        assertEquals(429, result.httpStatus());
        assertEquals(429, result.body().code());
        assertEquals(1, calls.get()); // submit OTP must not retry
    }

    @Test
    void vendor401IsNotRetriedAndIsReturnedForSafeServiceMapping() {
        AtomicInteger calls = new AtomicInteger();
        WebClient web = WebClient.builder().exchangeFunction(req -> {
            calls.incrementAndGet();
            return Mono.just(ClientResponse.create(UNAUTHORIZED)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"code\":401,\"message\":\"Unauthorized\"}").build());
        }).build();

        var result = new VendorClient(web, props(2)).generateOtp(request());

        assertEquals(401, result.httpStatus());
        assertEquals(1, calls.get());
    }

    @Test
    void vendor403IsNotRetriedAndIsReturnedForSafeServiceMapping() {
        AtomicInteger calls = new AtomicInteger();
        WebClient web = WebClient.builder().exchangeFunction(req -> {
            calls.incrementAndGet();
            return Mono.just(ClientResponse.create(FORBIDDEN)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"code\":403,\"message\":\"Forbidden\"}").build());
        }).build();

        var result = new VendorClient(web, props(2)).generateOtp(request());

        assertEquals(403, result.httpStatus());
        assertEquals(1, calls.get());
    }
}
