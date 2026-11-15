package de.witchcafe.social.domain;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.witchcafe.social.SocialProperties;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Verschlüsselt Zugangstoken (AES-256-GCM), bevor sie in der Datenbank landen.
 * Ohne {@code social.crypto-key} werden sie mit Warnung im Klartext gespeichert.
 */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final Logger log = LoggerFactory.getLogger(EncryptedStringConverter.class);
    private static final String ENCRYPTED = "enc:";
    private static final String PLAIN = "plain:";
    private static final int IV_LENGTH = 12;

    private final @Nullable SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public EncryptedStringConverter(SocialProperties properties) {
        String configured = properties.cryptoKey();
        if (configured == null || configured.isBlank()) {
            log.warn("social.crypto-key ist nicht gesetzt - Zugangstoken werden unverschluesselt gespeichert");
            this.key = null;
        } else {
            byte[] raw = Base64.getDecoder().decode(configured.trim());
            if (raw.length != 32) {
                throw new IllegalStateException("social.crypto-key muss 32 Bytes (Base64) lang sein");
            }
            this.key = new SecretKeySpec(raw, "AES");
        }
    }

    @Override
    public @Nullable String convertToDatabaseColumn(@Nullable String value) {
        if (value == null) {
            return null;
        }
        if (key == null) {
            return PLAIN + value;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return ENCRYPTED + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Token konnte nicht verschluesselt werden", e);
        }
    }

    @Override
    public @Nullable String convertToEntityAttribute(@Nullable String stored) {
        if (stored == null) {
            return null;
        }
        if (stored.startsWith(PLAIN)) {
            return stored.substring(PLAIN.length());
        }
        if (!stored.startsWith(ENCRYPTED)) {
            return stored;
        }
        if (key == null) {
            throw new IllegalStateException("Verschluesselte Token gefunden, aber social.crypto-key fehlt");
        }
        try {
            byte[] in = Base64.getDecoder().decode(stored.substring(ENCRYPTED.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, in, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(in, IV_LENGTH, in.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Token konnte nicht entschluesselt werden (falscher social.crypto-key?)", e);
        }
    }
}
