package bio.anode.ai.support;

import java.security.SecureRandom;

/** Generates OpenAI-style resource ids: {@code <prefix>-<24 base62 chars>}. */
public final class Ids {

    private static final char[] ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {
    }

    public static String generate(String prefix) {
        var sb = new StringBuilder(prefix).append('-');
        for (int i = 0; i < 24; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
