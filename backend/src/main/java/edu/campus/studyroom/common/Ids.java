package edu.campus.studyroom.common;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;

/**
 * ID and hash helpers. Order numbers are unique random strings;
 * request hashes are SHA-256 over canonical request content.
 */
public final class Ids {

    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private Ids() {
    }

    public static String newOrderNo(LocalDateTime businessNow) {
        return "SO" + businessNow.format(ORDER_NO_TIME) + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public static String newRequestKey() {
        return UUID.randomUUID().toString();
    }

    public static String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Canonical hash of a request body; field order is normalized by the caller's record toString. */
    public static String requestHash(Object request) {
        return sha256Hex(String.valueOf(request));
    }
}
