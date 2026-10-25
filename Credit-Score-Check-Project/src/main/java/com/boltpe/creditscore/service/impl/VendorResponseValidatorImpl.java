package com.boltpe.creditscore.service.impl;
import com.boltpe.creditscore.client.kycflow.dto.response.VendorSubmitOtpResponse;
import com.boltpe.creditscore.exception.VendorApiException;
import com.boltpe.creditscore.service.VendorResponseValidator;
import org.springframework.stereotype.Service;
@Service
public class VendorResponseValidatorImpl implements VendorResponseValidator {
 public void validateSuccessful(VendorSubmitOtpResponse response){
  if(response==null || !Integer.valueOf(200).equals(response.code()) || response.result()==null || !response.result().isObject())
   throw new VendorApiException("MALFORMED_VENDOR_RESPONSE","Credit score service returned an invalid response. Please try again later.");
 }
}
