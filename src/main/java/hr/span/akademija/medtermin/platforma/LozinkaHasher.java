
package hr.span.akademija.medtermin.platforma;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Component;

/**
 * Sigurno hashiranje i provjera lozinki.
 * Koristi PBKDF2-HMAC-SHA256 s jedinstvenom nasumičnom soli.
 */
@Component
public class LozinkaHasher {

    private static final String ALGORITAM = "pbkdf2-sha256";
    private static final int ITERACIJE = 600_000;
    private static final int MIN_ITERACIJE = 100_000;
    private static final int MAX_ITERACIJE = 1_000_000;
    private static final int DULJINA_SOLI = 16;
    private static final int DULJINA_HASHA = 256;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Base64.Encoder ENCODER =
            Base64.getUrlEncoder().withoutPadding();

    private static final Base64.Decoder DECODER =
            Base64.getUrlDecoder();

    public String hash(String lozinka) {
        if (lozinka == null) {
            throw new IllegalArgumentException("Lozinka ne smije biti null.");
        }

        byte[] sol = new byte[DULJINA_SOLI];
        RANDOM.nextBytes(sol);

        byte[] izvedeniHash = izvedi(lozinka, sol, ITERACIJE);

        try {
            return ALGORITAM + "$" + ITERACIJE + "$"
                    + ENCODER.encodeToString(sol) + "$"
                    + ENCODER.encodeToString(izvedeniHash);
        } finally {
            Arrays.fill(sol, (byte) 0);
            Arrays.fill(izvedeniHash, (byte) 0);
        }
    }

    public boolean provjeri(String lozinka, String spremljeniHash) {
        if (lozinka == null || spremljeniHash == null) {
            return false;
        }

        // Kompatibilnost sa starim SHA-256 hashovima.
        if (!spremljeniHash.startsWith(ALGORITAM + "$")) {
            return provjeriStariSha256(lozinka, spremljeniHash);
        }

        String[] dijelovi = spremljeniHash.split("\\$", -1);

        if (dijelovi.length != 4 || !ALGORITAM.equals(dijelovi[0])) {
            return false;
        }

        byte[] sol = null;
        byte[] ocekivaniHash = null;
        byte[] stvarniHash = null;

        try {
            int iteracije = Integer.parseInt(dijelovi[1]);

            // Ograničavamo trošak provjere i za neispravne zapise u bazi.
            if (iteracije < MIN_ITERACIJE || iteracije > MAX_ITERACIJE) {
                return false;
            }

            sol = DECODER.decode(dijelovi[2]);
            ocekivaniHash = DECODER.decode(dijelovi[3]);

            if (sol.length != DULJINA_SOLI
                    || ocekivaniHash.length != DULJINA_HASHA / 8) {
                return false;
            }

            stvarniHash = izvedi(lozinka, sol, iteracije);

            return MessageDigest.isEqual(stvarniHash, ocekivaniHash);

        } catch (IllegalArgumentException e) {
            return false;
        } finally {
            if (sol != null) {
                Arrays.fill(sol, (byte) 0);
            }
            if (ocekivaniHash != null) {
                Arrays.fill(ocekivaniHash, (byte) 0);
            }
            if (stvarniHash != null) {
                Arrays.fill(stvarniHash, (byte) 0);
            }
        }
    }

    /**
     * Provjerava treba li postojeći hash nadograditi na aktualni format.
     */
    public boolean trebaRehash(String spremljeniHash) {
        return spremljeniHash == null
                || !spremljeniHash.startsWith(
                ALGORITAM + "$" + ITERACIJE + "$");
    }

    private static byte[] izvedi(
            String lozinka, byte[] sol, int iteracije) {

        char[] znakovi = lozinka.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(
                znakovi, sol, iteracije, DULJINA_HASHA);

        Arrays.fill(znakovi, '\0');

        try {
            SecretKeyFactory factory =
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

            return factory.generateSecret(spec).getEncoded();

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "PBKDF2-HMAC-SHA256 nije dostupan.", e);
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * Provjera starih SHA-256 zapisa radi postupne migracije.
     */
    private static boolean provjeriStariSha256(
            String lozinka, String stariHash) {

        if (!stariHash.matches("(?i)[0-9a-f]{64}")) {
            return false;
        }

        try {
            byte[] izracunati = MessageDigest.getInstance("SHA-256")
                    .digest(lozinka.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            byte[] spremljeni = HexFormat.of().parseHex(stariHash);

            try {
                return MessageDigest.isEqual(izracunati, spremljeni);
            } finally {
                Arrays.fill(izracunati, (byte) 0);
                Arrays.fill(spremljeni, (byte) 0);
            }

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 nije dostupan.", e);
        }
    }
}
