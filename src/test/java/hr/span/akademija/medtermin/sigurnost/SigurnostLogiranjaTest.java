
package hr.span.akademija.medtermin.sigurnost;

import static org.assertj.core.api.Assertions.assertThat;

import hr.span.akademija.medtermin.ApiTestBaza;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@Tag("sigurnost")
@ExtendWith(OutputCaptureExtension.class)
class SigurnostLogiranjaTest extends ApiTestBaza {

    @Test
    @DisplayName("Lozinka se ne smije zapisivati u aplikacijske logove")
    void lozinkaSeNeSmijeZapisivatiULog(CapturedOutput output) {

        String tajnaLozinka = "TEST-TAJNA-LOZINKA-73921";

        Odgovor odgovor = postJson(
                "/api/auth/login",
                null,
                Map.of(
                        "username", "ana",
                        "password", tajnaLozinka
                )
        );

        assertThat(odgovor.status()).isEqualTo(401);

        assertThat(output.getAll())
                .doesNotContain(tajnaLozinka);
    }
}
