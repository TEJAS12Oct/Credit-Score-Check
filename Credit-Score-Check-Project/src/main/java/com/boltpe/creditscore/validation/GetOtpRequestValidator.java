package com.boltpe.creditscore.validation;

import com.boltpe.creditscore.dto.request.GetOtpRequest;
import com.boltpe.creditscore.exception.RequestValidationException;
import com.boltpe.creditscore.mapper.GetOtpVendorMapper;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class GetOtpRequestValidator {

    private static final Pattern PAN_PATTERN =
            Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");
    private static final Pattern MOBILE_PATTERN =
            Pattern.compile("^(?:\\+91)?[6-9][0-9]{9}$");
    private static final Pattern PIN_CODE_PATTERN =
            Pattern.compile("^[1-9][0-9]{5}$");
    private static final DateTimeFormatter DOB_FORMATTER =
            DateTimeFormatter.ofPattern("dd-MM-uuuu")
                    .withResolverStyle(ResolverStyle.STRICT);

    private static final int MINIMUM_AGE = 18;
    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_GENDER_LENGTH = 20;

    public void validate(String userId, String registeredMobile, GetOtpRequest request) {
        if (request == null) {
            throw new RequestValidationException("INVALID_REQUEST", "Request body is required");
        }

        validateAuthenticationContext(userId, registeredMobile);
        validateName(request.firstName(), "FIRST_NAME_REQUIRED", "First name is required");
        validateName(request.lastName(), "LAST_NAME_REQUIRED", "Last name is required");
        validatePan(request.panNumber());
        validateDobAndAge(request.dob());
        validateMobile(request.mobileNumber(), registeredMobile);
        validateEmail(request.email());
        validateGender(request.gender());
        validatePinCode(request.pinCode());
        validateConsent(request.consent());
    }

    public void validatePan(String panNumber) {
        if (isBlank(panNumber)) {
            throw new RequestValidationException("PAN_REQUIRED", "PAN number is required");
        }
        if (panNumber.length() != 10) {
            throw new RequestValidationException("INVALID_PAN", "PAN number must be exactly 10 characters");
        }
        if (!panNumber.substring(0, 5).matches("[A-Z]{5}")) {
            throw new RequestValidationException("INVALID_PAN", "First 5 PAN characters must be alphabets");
        }
        if (!panNumber.substring(5, 9).matches("[0-9]{4}")) {
            throw new RequestValidationException("INVALID_PAN", "PAN characters 6 to 9 must be digits");
        }
        if (!panNumber.substring(9).matches("[A-Z]")) {
            throw new RequestValidationException("INVALID_PAN", "Last PAN character must be an alphabet");
        }
        if (!PAN_PATTERN.matcher(panNumber).matches()) {
            throw new RequestValidationException("INVALID_PAN", "Invalid PAN number format");
        }
    }

    public LocalDate validateDobAndAge(String dob) {
        if (isBlank(dob)) {
            throw new RequestValidationException("DOB_REQUIRED", "Date of birth is required");
        }

        final LocalDate parsedDob;
        try {
            parsedDob = LocalDate.parse(dob, DOB_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new RequestValidationException(
                    "INVALID_DOB", "DOB must be a valid date in DD-MM-YYYY format");
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (parsedDob.isAfter(today)) {
            throw new RequestValidationException("INVALID_DOB", "Date of birth cannot be a future date");
        }

        int age = Period.between(parsedDob, today).getYears();
        if (age < MINIMUM_AGE) {
            throw new RequestValidationException(
                    "AGE_NOT_ELIGIBLE", "User must be at least 18 years old");
        }
        return parsedDob;
    }

    public void validateMobile(String mobileNumber, String registeredMobile) {
        if (isBlank(mobileNumber)) {
            throw new RequestValidationException("MOBILE_REQUIRED", "Mobile number is required");
        }

        if (mobileNumber.startsWith("+91")) {
            String nationalNumber = mobileNumber.substring(3);
            if (!nationalNumber.matches("[0-9]+")) {
                throw new RequestValidationException(
                        "INVALID_MOBILE", "Mobile number must contain digits only, with optional +91 prefix");
            }
            if (nationalNumber.length() != 10) {
                throw new RequestValidationException(
                        "INVALID_MOBILE", "Mobile number must contain exactly 10 digits");
            }
            if (!nationalNumber.matches("[6-9][0-9]{9}")) {
                throw new RequestValidationException(
                        "INVALID_MOBILE", "Mobile number must start with 6, 7, 8, or 9");
            }
        } else {
            if (mobileNumber.startsWith("+")) {
                throw new RequestValidationException(
                        "INVALID_MOBILE", "Mobile number must be a valid 10-digit Indian number or +91 followed by 10 digits");
            }
            if (!mobileNumber.matches("[0-9]+")) {
                throw new RequestValidationException(
                        "INVALID_MOBILE", "Mobile number must contain digits only, with optional +91 prefix");
            }
            if (mobileNumber.length() != 10) {
                throw new RequestValidationException(
                        "INVALID_MOBILE", "Mobile number must contain exactly 10 digits");
            }
            if (!mobileNumber.matches("[6-9][0-9]{9}")) {
                throw new RequestValidationException(
                        "INVALID_MOBILE", "Mobile number must start with 6, 7, 8, or 9");
            }
        }

        String normalizedRequestMobile = normalizeIndianMobile(mobileNumber);
        String normalizedRegisteredMobile = normalizeIndianMobile(registeredMobile);
        if (!normalizedRequestMobile.equals(normalizedRegisteredMobile)) {
            throw new RequestValidationException(
                    "MOBILE_MISMATCH", "Mobile number must match the registered mobile number");
        }
    }

    public void validateEmail(String email) {
        if (isBlank(email)) {
            throw new RequestValidationException("EMAIL_REQUIRED", "Email ID is required");
        }
        if (email.length() > MAX_EMAIL_LENGTH || email.contains(" ")) {
            throw new RequestValidationException("INVALID_EMAIL", "Invalid email ID format");
        }

        int firstAt = email.indexOf('@');
        int lastAt = email.lastIndexOf('@');
        if (firstAt < 0) {
            throw new RequestValidationException("INVALID_EMAIL", "Email ID must contain a valid @ symbol");
        }
        if (firstAt != lastAt) {
            throw new RequestValidationException("INVALID_EMAIL", "Invalid email ID format");
        }
        if (firstAt == 0) {
            throw new RequestValidationException("INVALID_EMAIL", "Email ID must contain a valid username before @");
        }
        if (firstAt == email.length() - 1) {
            throw new RequestValidationException("INVALID_EMAIL", "Email ID must contain a valid domain");
        }

        String localPart = email.substring(0, firstAt);
        String domain = email.substring(firstAt + 1);
        if (!domain.contains(".") || domain.endsWith(".")) {
            throw new RequestValidationException("INVALID_EMAIL", "Email ID must contain a valid domain extension");
        }
        if (localPart.length() > 64
                || localPart.startsWith(".")
                || localPart.endsWith(".")
                || localPart.contains("..")
                || domain.contains("..")
                || !validEmailLocalPart(localPart)
                || !validEmailDomain(domain)
                || !validDomainLabels(domain)) {
            throw new RequestValidationException("INVALID_EMAIL", "Invalid email ID format");
        }
    }

    // Validate bounded email parts without regex quantifiers/backtracking.
    private boolean validEmailLocalPart(String localPart) {
        for (int i = 0; i < localPart.length(); i++) {
            char ch = localPart.charAt(i);
            if (!isAsciiLetterOrDigit(ch) && ".!#$%&'*+/=?^_`{|}~-".indexOf(ch) < 0) {
                return false;
            }
        }
        return true;
    }

    private boolean validEmailDomain(String domain) {
        for (int i = 0; i < domain.length(); i++) {
            char ch = domain.charAt(i);
            if (!isAsciiLetterOrDigit(ch) && ch != '-' && ch != '.') {
                return false;
            }
        }
        return true;
    }

    private boolean isAsciiLetterOrDigit(char ch) {
        return (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')
                || (ch >= '0' && ch <= '9');
    }

    private boolean validDomainLabels(String domain) {
        for (String label : domain.split("\\.", -1)) {
            if (label.isEmpty() || label.length() > 63
                    || label.charAt(0) == '-' || label.charAt(label.length() - 1) == '-') {
                return false;
            }
        }
        return true;
    }

    public void validatePinCode(String pinCode) {
        if (isBlank(pinCode)) {
            throw new RequestValidationException("PIN_CODE_REQUIRED", "Pincode is required");
        }
        if (pinCode.contains(" ") || pinCode.contains("-") || !pinCode.chars().allMatch(Character::isLetterOrDigit)) {
            throw new RequestValidationException(
                    "INVALID_PIN_CODE", "Pincode must be a valid 6-digit Indian pincode and must not start with 0");
        }
        if (!pinCode.matches("[0-9]+")) {
            throw new RequestValidationException("INVALID_PIN_CODE", "Pincode must contain digits only");
        }
        if (pinCode.length() != 6) {
            throw new RequestValidationException("INVALID_PIN_CODE", "Pincode must contain exactly 6 digits");
        }
        if (pinCode.startsWith("0")) {
            throw new RequestValidationException("INVALID_PIN_CODE", "Pincode must not start with 0");
        }
        if (!PIN_CODE_PATTERN.matcher(pinCode).matches()) {
            throw new RequestValidationException(
                    "INVALID_PIN_CODE", "Pincode must be a valid 6-digit Indian pincode and must not start with 0");
        }
    }

    private void validateAuthenticationContext(String userId, String registeredMobile) {
        if (isBlank(userId)) {
            throw new RequestValidationException(
                    "AUTHENTICATION_REQUIRED", "Authenticated user is required");
        }
        if (isBlank(registeredMobile)) {
            throw new RequestValidationException(
                    "REGISTERED_MOBILE_REQUIRED", "Verified registered mobile is required");
        }
        if (!MOBILE_PATTERN.matcher(registeredMobile).matches()) {
            throw new RequestValidationException(
                    "INVALID_REGISTERED_MOBILE", "Verified registered mobile is invalid");
        }
    }

    private void validateName(String name, String code, String message) {
        if (isBlank(name)) {
            throw new RequestValidationException(code, message);
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new RequestValidationException("INVALID_NAME", "Name must not exceed 100 characters");
        }
    }

    private void validateGender(String gender) {
        if (isBlank(gender)) {
            throw new RequestValidationException("GENDER_REQUIRED", "Gender is required");
        }
        if (gender.length() > MAX_GENDER_LENGTH) {
            throw new RequestValidationException("INVALID_GENDER", "Invalid gender");
        }
    }

    private void validateConsent(String consentText) {
        String normalizedConsent = consentText == null ? null : consentText.trim();
        if (!GetOtpVendorMapper.CONSENT_TEXT.equals(normalizedConsent)) {
            throw new RequestValidationException(
                    "INVALID_CONSENT", "Valid consent text is required");
        }
    }

    public String normalizeIndianMobile(String mobileNumber) {
        if (mobileNumber != null && mobileNumber.startsWith("+91")) {
            return mobileNumber.substring(3);
        }
        return mobileNumber;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
