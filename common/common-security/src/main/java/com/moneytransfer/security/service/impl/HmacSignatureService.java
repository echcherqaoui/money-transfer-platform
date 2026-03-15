package com.moneytransfer.security.service.impl;

import com.moneytransfer.security.service.ISignatureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public class HmacSignatureService implements ISignatureService {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String DELIMITER = "|";

    private final byte[] secretKeyBytes;

    public HmacSignatureService(@Value("${security.hmac.secret}") String secret) {
        this.secretKeyBytes = secret.getBytes(UTF_8);
    }

    private String computeHmac(String data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secretKeyBytes, ALGORITHM));
            byte[] hmacBytes = mac.doFinal(data.getBytes(UTF_8));

            return Base64.getUrlEncoder()
                  .withoutPadding()
                  .encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC signature", e);
        }
    }

    // MessageDigest.isEqual is constant-time and null-safe
    private boolean constantTimeEquals(String expected, String provided) {
        if (provided == null || expected == null) return false;
        return MessageDigest.isEqual(
              expected.getBytes(UTF_8),
              provided.getBytes(UTF_8)
        );
    }

    @Override
    public String sign(String eventId, String transactionId, String epochSeconds) {
        String payload = String.join(DELIMITER, eventId, transactionId, epochSeconds);
        return computeHmac(payload);
    }

    @Override
    public boolean verify(String eventId, String transactionId, String epochSeconds, String signature) {
        String expected = sign(eventId, transactionId, epochSeconds);
        return constantTimeEquals(expected, signature);
    }
}