package com.boltpe.creditscore.util;

import static org.junit.jupiter.api.Assertions.*;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

class SensitiveDataEncryptionServiceTest {

 private String generateTestKey() {
  byte[] keyBytes = new byte[16];
  new SecureRandom().nextBytes(keyBytes);
  return HexFormat.of().formatHex(keyBytes);
 }

 private SensitiveDataEncryptionService encryption() {
  return new SensitiveDataEncryptionService(generateTestKey());
 }

 @Test
 void encryptsAndDecrypts() {

  SensitiveDataEncryptionService service = encryption();

  String original = "PAN=ABCDE1234F";

  String encrypted = service.encrypt(original);

  assertTrue(encrypted.startsWith("enc:v1:"));
  assertFalse(encrypted.contains("ABCDE1234F"));
  assertEquals(original, service.decrypt(encrypted));
 }

 @Test
 void supportsPlaintextRowsForMigration() {

  SensitiveDataEncryptionService service = encryption();

  assertEquals("legacy", service.decrypt("legacy"));
 }

 @Test
 void rejectsMissingKeyOnEncryption() {

  SensitiveDataEncryptionService service =
          new SensitiveDataEncryptionService("");

  assertThrows(
          IllegalStateException.class,
          () -> service.encrypt("x")
  );
 }

 @Test
 void rejectsWrongKeyLength() {

  byte[] invalidKeyBytes = new byte[8];
  new SecureRandom().nextBytes(invalidKeyBytes);

  String invalidKey = HexFormat.of().formatHex(invalidKeyBytes);

  assertThrows(
          IllegalArgumentException.class,
          () -> new SensitiveDataEncryptionService(invalidKey)
  );
 }

 @Test
 void rejectsNonHexadecimalKey() {

  String invalidKey = "ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ";

  assertThrows(
          IllegalArgumentException.class,
          () -> new SensitiveDataEncryptionService(invalidKey)
  );
 }

 @Test
 void encryptsSamePlaintextDifferently() {

  SensitiveDataEncryptionService service = encryption();

  String first = service.encrypt("Sensitive Data");
  String second = service.encrypt("Sensitive Data");

  assertNotEquals(first, second);
  assertEquals("Sensitive Data", service.decrypt(first));
  assertEquals("Sensitive Data", service.decrypt(second));
 }

 @Test
 void returnsNullForNullInput() {

  SensitiveDataEncryptionService service = encryption();

  assertNull(service.encrypt(null));
  assertNull(service.decrypt(null));
 }

 @Test
 void rejectsTamperedEncryptedData() {

  SensitiveDataEncryptionService service = encryption();

  String encrypted = service.encrypt("Sensitive Data");

  String payload = encrypted.substring("enc:v1:".length());

  byte[] decoded = Base64.getDecoder().decode(payload);

  decoded[decoded.length - 1] ^= 1;

  String tampered = "enc:v1:" +
          Base64.getEncoder().encodeToString(decoded);

  assertThrows(
          IllegalStateException.class,
          () -> service.decrypt(tampered)
  );
 }

 @Test
 void rejectsInvalidEncryptedPayload() {

  SensitiveDataEncryptionService service = encryption();

  String invalidPayload = "enc:v1:invalid-base64!";

  assertThrows(
          IllegalStateException.class,
          () -> service.decrypt(invalidPayload)
  );
 }
}