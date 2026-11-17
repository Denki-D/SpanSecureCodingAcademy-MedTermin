package hr.span.akademija.medtermin.sigurnost;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import hr.span.akademija.medtermin.ApiTestBaza;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("sigurnost")
class IspisStoredXssSigurnosniTest extends ApiTestBaza {

    @Test
    @DisplayName("Napomena rezervacije mora biti HTML-escaped")
    void napomenaNeSmijeSadrzavatiIzvrsiviHtml() {

        String token = prijava("ana", "Ana12345!");

        long terminId = 0;

        for (JsonNode termin : get("/api/termini", token).json()) {
            if (termin.get("id").asLong() != 7
                    && termin.get("slobodno").asInt() > 0) {
                terminId = termin.get("id").asLong();
                break;
            }
        }

        assertThat(terminId).isPositive();

        String napad = "<script>alert(1)</script>";

        Odgovor kreiranje = postJson(
                "/api/rezervacije",
                token,
                Map.of(
                        "terminId", terminId,
                        "napomena", napad,
                        "oib", "48291736504"
                )
        );

        assertThat(kreiranje.status()).isEqualTo(201);

        long id = kreiranje.json().get("id").asLong();

        Odgovor ispis = get("/ispis/rezervacije/" + id, token);

        assertThat(ispis.status()).isEqualTo(200);
        assertThat(ispis.tijelo())
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(ispis.tijelo())
                .doesNotContain(napad);
    }
}