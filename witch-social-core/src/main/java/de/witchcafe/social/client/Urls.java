package de.witchcafe.social.client;

/**
 * Helfer für Server-Adressen föderierter Netzwerke (Mastodon-Instanzen, Bluesky-PDS).
 */
public final class Urls {

    private Urls() {
    }

    /** "mastodon.social", "@name@chaos.social" oder "https://x.y/" → "https://x.y". */
    public static String normalizeInstance(String input) {
        String s = input.trim();
        if (s.contains("@") && !s.startsWith("http")) {
            s = s.substring(s.lastIndexOf('@') + 1);
        }
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            s = "https://" + s;
        }
        return s.replaceAll("/+$", "");
    }
}
