package de.witchcafe.app.social.domain;

/**
 * Unterstützte Netzwerke mit ihren wichtigsten Einschränkungen für Beiträge.
 */
public enum Network {

    FACEBOOK("Facebook", "#1877F2", 63_206, false),
    INSTAGRAM("Instagram", "#E1306C", 2_200, true),
    PINTEREST("Pinterest", "#E60023", 500, true),
    BLUESKY("Bluesky", "#1185FE", 300, false),
    MASTODON("Mastodon", "#6364FF", 500, false);

    private final String displayName;
    private final String color;
    private final int maxLength;
    private final boolean imageRequired;

    Network(String displayName, String color, int maxLength, boolean imageRequired) {
        this.displayName = displayName;
        this.color = color;
        this.maxLength = maxLength;
        this.imageRequired = imageRequired;
    }

    public String displayName() {
        return displayName;
    }

    /** Markenfarbe für Badges in der Oberfläche. */
    public String color() {
        return color;
    }

    /** Maximale Textlänge in Zeichen (bei Bluesky: Grapheme, hier als Codepoints gezählt). */
    public int maxLength() {
        return maxLength;
    }

    /** Instagram und Pinterest akzeptieren keine reinen Textbeiträge. */
    public boolean imageRequired() {
        return imageRequired;
    }
}
