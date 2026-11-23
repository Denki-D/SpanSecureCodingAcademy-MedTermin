
package hr.span.akademija.medtermin.funkcionalno;

import static org.assertj.core.api.Assertions.assertThat;

import hr.span.akademija.medtermin.ApiTestBaza;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("funkcionalno")
class PrijavaObrascemFunkcionalniTest extends ApiTestBaza {

    private String csrfToken() {
        Odgovor odgovor = get("/prijava", null);

        assertThat(odgovor.status()).isEqualTo(200);

        Matcher matcher = Pattern.compile(
                "name=\"csrfToken\"\\s+value=\"([^\"]+)\"")
                .matcher(odgovor.tijelo());

        assertThat(matcher.find())
                .as("HTML obrazac mora sadržavati CSRF token")
                .isTrue();

        return matcher.group(1);
    }

    private Odgovor prijava(String username, String password, String returnUrl) {
        return postForma("/prijava", Map.of(
                "username", username,
                "password", password,
                "returnUrl", returnUrl,
                "csrfToken", csrfToken()));
    }

    @Test
    @DisplayName("Početna stranica je HTML s nazivom aplikacije")
    void pocetnaStranica() {
        Odgovor odgovor = get("/", null);

        assertThat(odgovor.status()).isEqualTo(200);
        assertThat(odgovor.zaglavlje("Content-Type")).startsWith("text/html");
        assertThat(odgovor.tijelo()).contains("MedTermin").contains("/prijava");
    }

    @Test
    @DisplayName("Obrazac za prijavu sadrži polja i CSRF token")
    void obrazacZaPrijavu() {
        Odgovor odgovor = get("/prijava?returnUrl=" + enc("/moji"), null);

        assertThat(odgovor.status()).isEqualTo(200);
        assertThat(odgovor.tijelo())
                .contains("name=\"username\"")
                .contains("name=\"password\"")
                .contains("name=\"returnUrl\"")
                .contains("name=\"csrfToken\"")
                .contains("value=\"/moji\"");
    }

    @Test
    @DisplayName("Uspješna prijava obrascem preusmjerava i postavlja SESSION kolačić")
    void prijavaObrascemPreusmjerava() {
        Odgovor odgovor = prijava("ana", "Ana12345!", "/moji");

        assertThat(odgovor.status()).isEqualTo(302);
        assertThat(odgovor.zaglavlje("Location")).isEqualTo("/moji");

        String kolacic = odgovor.zaglavlje("Set-Cookie");

        assertThat(kolacic)
                .startsWith("SESSION=")
                .containsIgnoringCase("HttpOnly");
    }

    @Test
    @DisplayName("Kolačić SESSION vrijedi za API")
    void kolacicVrijediZaApi() {
        Odgovor prijava = prijava("marko", "Marko12345!", "/");

        assertThat(prijava.status()).isEqualTo(302);

        String kolacic = prijava.zaglavlje("Set-Cookie").split(";")[0];

        Odgovor ja = posalji(
                "GET", "/api/ja", null, null, null,
                "Cookie", kolacic);

        assertThat(ja.status()).isEqualTo(200);
        assertThat(ja.json().get("username").asText()).isEqualTo("marko");
    }

    @Test
    @DisplayName("Neuspjela prijava obrascem prikazuje poruku")
    void neuspjelaPrijavaObrascem() {
        Odgovor odgovor = prijava("ana", "pogresno", "/");

        assertThat(odgovor.status()).isEqualTo(200);
        assertThat(odgovor.tijelo())
                .contains("Neispravno korisničko ime ili lozinka.");
        assertThat(odgovor.tijelo()).contains("name=\"csrfToken\"");
        assertThat(odgovor.zaglavlje("Set-Cookie")).isNull();
    }

    @Test
    @DisplayName("Prijava bez CSRF tokena vraća 403")
    void prijavaBezCsrfTokenaJeOdbijena() {
        Odgovor odgovor = postForma("/prijava", Map.of(
                "username", "ana",
                "password", "Ana12345!",
                "returnUrl", "/"));

        assertThat(odgovor.status()).isEqualTo(403);
    }
}
