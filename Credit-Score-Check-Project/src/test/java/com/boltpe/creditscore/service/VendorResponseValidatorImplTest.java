package com.boltpe.creditscore.service;
import static org.junit.jupiter.api.Assertions.*; import com.boltpe.creditscore.client.kycflow.dto.response.VendorSubmitOtpResponse; import com.boltpe.creditscore.exception.VendorApiException; import com.boltpe.creditscore.service.impl.VendorResponseValidatorImpl; import com.fasterxml.jackson.databind.ObjectMapper; import org.junit.jupiter.api.Test;
class VendorResponseValidatorImplTest { private final VendorResponseValidatorImpl v=new VendorResponseValidatorImpl(); private final ObjectMapper m=new ObjectMapper();
 @Test void acceptsObjectResult() throws Exception {assertDoesNotThrow(()->v.validateSuccessful(new VendorSubmitOtpResponse(200,"ok",m.readTree("{}"))));}
 @Test void rejectsMalformedSuccess() throws Exception {assertThrows(VendorApiException.class,()->v.validateSuccessful(new VendorSubmitOtpResponse(200,"ok",m.readTree("[]"))));assertThrows(VendorApiException.class,()->v.validateSuccessful(null));}
}
