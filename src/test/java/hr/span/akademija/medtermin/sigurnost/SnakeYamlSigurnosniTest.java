package hr.span.akademija.medtermin.sigurnost;

import static org.assertj.core.api.Assertions.assertThat;

import hr.span.akademija.medtermin.ApiTestBaza;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("sigurnost")
class SnakeYamlSigurnosniTest extends ApiTestBaza {

    @Test
    @DisplayName("YAML parser ne smije instancirati proizvoljne Java tipove")
    void proizvoljniJavaTipoviSeOdbijaju() {

        String token = prijava("ana", "Ana12345!");

        String yaml = """
                !!java.util.LinkedHashMap
                podsjetnik: false
                satiPrije: 12
                kanal: sms
                """;

        Odgovor odgovor = posalji(
                "POST",
                "/api/postavke",
                token,
                "application/yaml",
                yaml
        );

        assertThat(odgovor.status()).isEqualTo(400);
    }
}