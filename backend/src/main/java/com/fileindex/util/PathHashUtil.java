package com.fileindex.util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class PathHashUtil {

    private PathHashUtil() {
    }

    /** Deterministic document id for a file: SHA-256 of its normalized absolute path. */
    public static String hash(Path path) {
        String absolute = path.toAbsolutePath().normalize().toString();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(absolute.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
