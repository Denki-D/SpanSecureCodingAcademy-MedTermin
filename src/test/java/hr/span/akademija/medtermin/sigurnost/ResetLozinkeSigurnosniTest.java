package hr.span.akademija.medtermin.sigurnost;

import static org.assertj.core.api.Assertions.assertThat;

import hr.span.akademija.medtermin.ApiTestBaza;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("sigurnost")
class ResetLozinkeSigurnosniTest extends ApiTestBaza {

    @Test
    @DisplayName("Predvidivi token ne smije omogućiti resetiranje tuđe lozinke")
    void predvidiviTokenNeOmogucujeResetLozinke() throws NoSuchAlgorithmException {

        String username = noviKorisnik("reset", "Stara12345!");

        // Zahtijevamo izdavanje tokena za resetiranje.
        Odgovor zahtjev = postJson(
                "/api/auth/reset-zahtjev",
                null,
                Map.of("username", username)
        );

        assertThat(zahtjev.status()).isEqualTo(202);

        // Napadač računa token samo na temelju korisničkog imena i datuma.
        String ulaz = username + LocalDate.now();

        MessageDigest md = MessageDigest.getInstance("MD5");

        String predvidiviToken = HexFormat.of().formatHex(
                md.digest(ulaz.getBytes(StandardCharsets.UTF_8))
        );

        // Pokušaj promjene lozinke pomoću izračunatog tokena.
        Odgovor reset = postJson(
                "/api/auth/reset",
                null,
                Map.of(
                        "token", predvidiviToken,
                        "novaLozinka", "Nova12345!"
                )
        );

        // Sigurna aplikacija mora odbiti predvidivi token.
        assertThat(reset.status()).isEqualTo(400);
    }
}