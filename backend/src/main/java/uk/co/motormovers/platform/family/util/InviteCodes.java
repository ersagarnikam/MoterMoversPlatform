package uk.co.motormovers.platform.family.util;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Invite codes: eight characters from an alphabet without look-alikes (no 0/O, 1/I/L), shown as
 * ABCD-EFGH so they are easy to read out or copy from a letter home.
 */
public final class InviteCodes {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    public static final int LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private InviteCodes() {
    }

    public static String generate() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** Accepts what a parent types: any case, with or without the dash or spaces. */
    public static String normalise(String typed) {
        return typed == null ? "" : typed.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    public static String format(String code) {
        return code.length() == LENGTH ? code.substring(0, 4) + "-" + code.substring(4) : code;
    }
}
