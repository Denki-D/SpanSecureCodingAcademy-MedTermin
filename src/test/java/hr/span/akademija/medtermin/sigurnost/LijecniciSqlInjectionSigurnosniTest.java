package hr.span.akademija.medtermin.sigurnost;

import static org.assertj.core.api.Assertions.assertThat;

import hr.span.akademija.medtermin.ApiTestBaza;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("sigurnost")
class LijecniciSqlInjectionSigurnosniTest extends ApiTestBaza {

    @Test
    @DisplayName("Proizvoljan SQL izraz ne smije biti dopušten za sortiranje")
    void proizvoljniSqlIzrazZaSortiranjeSeOdbija() {

        String token = prijava("ana", "Ana12345!");

        String izraz = enc("CASE WHEN 1=1 THEN ime ELSE prezime END");

        Odgovor odgovor = get(
                "/api/lijecnici?sort=" + izraz + "&smjer=asc",
                token
        );

        assertThat(odgovor.status()).isEqualTo(400);
    }
}