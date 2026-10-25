package com.boltpe.creditscore.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.boltpe.creditscore.dto.request.GetOtpRequest;
import com.boltpe.creditscore.exception.RequestValidationException;
import com.boltpe.creditscore.mapper.GetOtpVendorMapper;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

class GetOtpRequestValidatorTest {

    private static final String USER_ID = "user-1";
    private static final String MOBILE = "9876543210";
    private static final DateTimeFormatter DOB = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private final GetOtpRequestValidator validator = new GetOtpRequestValidator();

    @Test void acceptsValidRequest() { validator.validate(USER_ID, MOBILE, validRequest()); }
    @Test void acceptsPlus91Mobile() { assertValidMobile("+919876543210"); }

    // Mobile validation
    @Test void rejectsMissingMobile() { assertMobile(" ", "MOBILE_REQUIRED", "Mobile number is required"); }
    @Test void rejectsShortMobile() { assertMobile("987654321", "INVALID_MOBILE", "Mobile number must contain exactly 10 digits"); }
    @Test void rejectsLongMobile() { assertMobile("98765432100", "INVALID_MOBILE", "Mobile number must contain exactly 10 digits"); }
    @Test void rejectsInvalidStartingDigit() { assertMobile("1234567890", "INVALID_MOBILE", "Mobile number must start with 6, 7, 8, or 9"); }
    @Test void rejectsInvalidPlus91Format() { assertMobile("+91-9876543210", "INVALID_MOBILE", "Mobile number must contain digits only, with optional +91 prefix"); }
    @Test void rejectsAlphabetInMobile() { assertMobile("98765abc10", "INVALID_MOBILE", "Mobile number must contain digits only, with optional +91 prefix"); }
    @Test void rejectsOtherCountryCode() { assertMobile("+19876543210", "INVALID_MOBILE", "Mobile number must be a valid 10-digit Indian number or +91 followed by 10 digits"); }
    @Test void rejectsMobileWithSpace() { assertMobile("98765 43210", "INVALID_MOBILE", "Mobile number must contain digits only, with optional +91 prefix"); }
    @Test void rejectsMobileMismatch() { assertMobile("9999999999", "MOBILE_MISMATCH", "Mobile number must match the registered mobile number"); }
    @Test void normalizesPlus91Mobile() { assertEquals(MOBILE, validator.normalizeIndianMobile("+919876543210")); }

    // Email validation
    @Test void rejectsMissingEmail() { assertEmail(" ", "EMAIL_REQUIRED", "Email ID is required"); }
    @Test void rejectsMissingAt() { assertEmail("sampleexample.com", "INVALID_EMAIL", "Email ID must contain a valid @ symbol"); }
    @Test void rejectsMissingLocalPart() { assertEmail("@example.com", "INVALID_EMAIL", "Email ID must contain a valid username before @"); }
    @Test void rejectsMissingDomain() { assertEmail("sample@", "INVALID_EMAIL", "Email ID must contain a valid domain"); }
    @Test void rejectsMissingDomainExtension() { assertEmail("sample@example", "INVALID_EMAIL", "Email ID must contain a valid domain extension"); }
    @Test void rejectsSpaceInEmail() { assertEmail("sample @example.com", "INVALID_EMAIL", "Invalid email ID format"); }
    @Test void rejectsMultipleAt() { assertEmail("sample@@example.com", "INVALID_EMAIL", "Invalid email ID format"); }
    @Test void rejectsMalformedEmail() { assertEmail("sample..name@example.com", "INVALID_EMAIL", "Invalid email ID format"); }
    @Test void acceptsStandardEmail() { assertValidEmail("sample@example.com"); }
    @Test void acceptsCompanyEmail() { assertValidEmail("tejas.jawale@company.in"); }
    @Test void acceptsCompanyCoInEmail() { assertValidEmail("employee@company.co.in"); }

    // PAN validation
    @Test void rejectsMissingPan() { assertPan(" ", "PAN_REQUIRED", "PAN number is required"); }
    @Test void rejectsShortPan() { assertPan("ABCD1234F", "INVALID_PAN", "PAN number must be exactly 10 characters"); }
    @Test void rejectsLongPan() { assertPan("ABCDE12345F", "INVALID_PAN", "PAN number must be exactly 10 characters"); }
    @Test void rejectsInvalidFirstFivePanCharacters() { assertPan("AB12E1234F", "INVALID_PAN", "First 5 PAN characters must be alphabets"); }
    @Test void rejectsLowercasePan() { assertPan("abcde1234f", "INVALID_PAN", "First 5 PAN characters must be alphabets"); }
    @Test void rejectsInvalidMiddlePanCharacters() { assertPan("ABCDE12A4F", "INVALID_PAN", "PAN characters 6 to 9 must be digits"); }
    @Test void rejectsInvalidLastPanCharacter() { assertPan("ABCDE12345", "INVALID_PAN", "Last PAN character must be an alphabet"); }
    @Test void rejectsPanSpecialCharacter() { assertPan("ABCDE12#4F", "INVALID_PAN", "PAN characters 6 to 9 must be digits"); }
    @Test void rejectsPanWithSpace() { assertPan("ABCDE 1234", "INVALID_PAN", "PAN characters 6 to 9 must be digits"); }
    @Test void acceptsValidPan() { validator.validatePan("ABCDE1234F"); }

    // Pincode validation
    @Test void rejectsMissingPinCode() { assertPin(" ", "PIN_CODE_REQUIRED", "Pincode is required"); }
    @Test void rejectsShortPinCode() { assertPin("11001", "INVALID_PIN_CODE", "Pincode must contain exactly 6 digits"); }
    @Test void rejectsLongPinCode() { assertPin("1100011", "INVALID_PIN_CODE", "Pincode must contain exactly 6 digits"); }
    @Test void rejectsPinCodeStartingZero() { assertPin("011001", "INVALID_PIN_CODE", "Pincode must not start with 0"); }
    @Test void rejectsAlphaNumericPinCode() { assertPin("41A033", "INVALID_PIN_CODE", "Pincode must contain digits only"); }
    @Test void rejectsPinCodeWithSpace() { assertPin("411 033", "INVALID_PIN_CODE", "Pincode must be a valid 6-digit Indian pincode and must not start with 0"); }
    @Test void rejectsPinCodeWithHyphen() { assertPin("411-033", "INVALID_PIN_CODE", "Pincode must be a valid 6-digit Indian pincode and must not start with 0"); }
    @Test void acceptsValidDelhiPin() { validator.validatePinCode("110001"); }
    @Test void acceptsValidPunePin() { validator.validatePinCode("411033"); }
    @Test void acceptsValidMumbaiPin() { validator.validatePinCode("400001"); }

    // Existing DOB / age checks
    @Test void rejectsMissingDob() { assertCode("DOB_REQUIRED", copy(validRequest(), MOBILE, validRequest().panNumber(), " ", validRequest().email(), validRequest().pinCode())); }
    @Test void rejectsInvalidCalendarDate() { assertCode("INVALID_DOB", copy(validRequest(), MOBILE, validRequest().panNumber(), "31-02-2000", validRequest().email(), validRequest().pinCode())); }
    @Test void rejectsWrongDobFormat() { assertCode("INVALID_DOB", copy(validRequest(), MOBILE, validRequest().panNumber(), "2000-01-01", validRequest().email(), validRequest().pinCode())); }
    @Test void rejectsFutureDob() { assertCode("INVALID_DOB", copy(validRequest(), MOBILE, validRequest().panNumber(), LocalDate.now().plusDays(1).format(DOB), validRequest().email(), validRequest().pinCode())); }
    @Test void rejectsMinor() { assertCode("AGE_NOT_ELIGIBLE", copy(validRequest(), MOBILE, validRequest().panNumber(), LocalDate.now().minusYears(17).format(DOB), validRequest().email(), validRequest().pinCode())); }

    private void assertValidMobile(String mobile) {
        validator.validate(USER_ID, MOBILE, copy(validRequest(), mobile, validRequest().panNumber(), validRequest().dob(), validRequest().email(), validRequest().pinCode()));
    }

    private void assertMobile(String mobile, String code, String message) {
        assertException(code, message, () -> validator.validateMobile(mobile, MOBILE));
    }

    private void assertEmail(String email, String code, String message) {
        assertException(code, message, () -> validator.validateEmail(email));
    }

    private void assertValidEmail(String email) { validator.validateEmail(email); }

    private void assertPan(String pan, String code, String message) {
        assertException(code, message, () -> validator.validatePan(pan));
    }

    private void assertPin(String pin, String code, String message) {
        assertException(code, message, () -> validator.validatePinCode(pin));
    }

    private void assertException(String code, String message, Runnable action) {
        RequestValidationException ex = assertThrows(RequestValidationException.class, action::run);
        assertEquals(code, ex.getCode());
        assertEquals(message, ex.getMessage());
    }

    private void assertCode(String expectedCode, GetOtpRequest request) {
        RequestValidationException ex = assertThrows(RequestValidationException.class,
                () -> validator.validate(USER_ID, MOBILE, request));
        assertEquals(expectedCode, ex.getCode());
    }

    private GetOtpRequest validRequest() {
        return new GetOtpRequest(
                "Tejas", "Jawale", "ABCDE1234F",
                LocalDate.now().minusYears(25).format(DOB), MOBILE,
                "tejas@example.com", "male", "411033", GetOtpVendorMapper.CONSENT_TEXT);
    }

    private GetOtpRequest copy(GetOtpRequest source, String mobile, String pan, String dob, String email, String pin) {
        return new GetOtpRequest(source.firstName(), source.lastName(), pan, dob, mobile,
                email, source.gender(), pin, source.consent());
    }
}
