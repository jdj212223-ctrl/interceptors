package com.interceptors.compat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** The OBS WebSocket v5 login answer: base64(sha256(base64(sha256(password + salt)) + challenge)). */
public final class ObsAuth {
    private ObsAuth() {}

    public static String answer(String password, String salt, String challenge) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            String secret = Base64.getEncoder().encodeToString(sha.digest((password + salt).getBytes(StandardCharsets.UTF_8)));
            return Base64.getEncoder().encodeToString(sha.digest((secret + challenge).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
