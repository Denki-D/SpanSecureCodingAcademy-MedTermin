package hr.span.akademija.medtermin.sigurnost;

import static org.assertj.core.api.Assertions.assertThat;

import hr.span.akademija.medtermin.ApiTestBaza;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("sigurnost")
class CorsSigurnosniTest extends ApiTestBaza {

    @Test
    @DisplayName("CORS mora odbiti origin koji nije na allowlisti")
    void nedopusteniOriginSeOdbija() {

        Odgovor odgovor = posalji(
                "OPTIONS",
                "/api/ja",
                null,
                null,
                null,
                "Origin", "https://evil.example",
                "Access-Control-Request-Method", "GET",
                "Access-Control-Request-Headers", "authorization"
        );

        assertThat(odgovor.status()).isEqualTo(403);

        assertThat(odgovor.zaglavlje("Access-Control-Allow-Origin"))
                .isNull();
    }
}