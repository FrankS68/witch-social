package de.witchcafe.social.domain;

public enum PostStatus {

    SCHEDULED("Geplant"),
    PUBLISHING("Wird veröffentlicht"),
    PUBLISHED("Veröffentlicht"),
    PARTIALLY_PUBLISHED("Teilweise veröffentlicht"),
    FAILED("Fehlgeschlagen"),
    CANCELLED("Abgebrochen");

    private final String label;

    PostStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
