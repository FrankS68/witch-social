package de.witchcafe.social;

/**
 * Hochgeladenes, noch nicht gespeichertes Bild aus dem Beitragsformular.
 */
public record ImageUpload(String fileName, String contentType, byte[] data) {
}
