
package hr.span.akademija.medtermin.platforma;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Zaboravljena lozinka: token se šalje e-poštom, a njime se postavlja nova lozinka. */
@Service
public class ResetLozinkeService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BAJTOVA = 32;
    private static final long TOKEN_VAZENJE_MINUTA = 30;

    private final JdbcTemplate jdbc;
    private final KorisnikService korisnici;
    private final PostaService posta;

    private record ResetToken(long id, String username) {
    }

    public ResetLozinkeService(JdbcTemplate jdbc, KorisnikService korisnici, PostaService posta) {
        this.jdbc = jdbc;
        this.korisnici = korisnici;
        this.posta = posta;
    }

    public void zatrazi(String username) {
        Optional<KorisnikService.Korisnik> korisnik = korisnici.pronadi(username);
        if (korisnik.isEmpty()) {
            return;
        }

        // Generiramo kriptografski siguran nasumični token.
        String token = generirajToken();

        // U bazu spremamo samo SHA-256 sažetak tokena.
        String hashTokena = sazetak(token);

        // Prethodni neiskorišteni tokeni za korisnika više ne vrijede.
        jdbc.update(
                "UPDATE reset_tokeni SET iskoristen = TRUE "
                        + "WHERE username = ? AND iskoristen = FALSE",
                username
        );

        Timestamp istjece = Timestamp.valueOf(
                LocalDateTime.now().plusMinutes(TOKEN_VAZENJE_MINUTA)
        );

        jdbc.update(
                "INSERT INTO reset_tokeni (username, token, istjece, iskoristen) "
                        + "VALUES (?, ?, ?, FALSE)",
                username, hashTokena, istjece
        );

        // Korisniku šaljemo izvorni token, ne njegov hash.
        posta.posaljiUputeZaLozinku(korisnik.get(), token);
    }

    @Transactional
    public boolean postaviNovuLozinku(String token, String novaLozinka) {
        KorisnikService.provjeriLozinku(novaLozinka);

        if (token == null || token.isBlank()) {
            return false;
        }

        String hashTokena = sazetak(token.trim());

        List<ResetToken> vlasnici = jdbc.query(
                "SELECT id, username FROM reset_tokeni "
                        + "WHERE token = ? "
                        + "AND iskoristen = FALSE "
                        + "AND istjece > CURRENT_TIMESTAMP "
                        + "ORDER BY id DESC",
                (rs, redak) -> new ResetToken(
                        rs.getLong("id"),
                        rs.getString("username")
                ),
                hashTokena
        );

        if (vlasnici.isEmpty()) {
            return false;
        }

        ResetToken reset = vlasnici.get(0);

        // Atomskim ažuriranjem osiguravamo da se token ne može iskoristiti dvaput.
        int oznaceno = jdbc.update(
                "UPDATE reset_tokeni SET iskoristen = TRUE "
                        + "WHERE id = ? AND iskoristen = FALSE "
                        + "AND istjece > CURRENT_TIMESTAMP",
                reset.id()
        );

        if (oznaceno != 1) {
            return false;
        }

        korisnici.promijeniLozinku(reset.username(), novaLozinka);
        return true;
    }

    private static String generirajToken() {
        byte[] bajtovi = new byte[TOKEN_BAJTOVA];
        RANDOM.nextBytes(bajtovi);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bajtovi);
    }

    private static String sazetak(String vrijednost) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(vrijednost.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 nije dostupan", e);
        }
    }
}
