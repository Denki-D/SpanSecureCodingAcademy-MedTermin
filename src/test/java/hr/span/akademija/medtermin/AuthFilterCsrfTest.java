
package hr.span.akademija.medtermin;

import static org.assertj.core.api.Assertions.assertThat;

import hr.span.akademija.medtermin.platforma.PrijavljeniKorisnik;
import hr.span.akademija.medtermin.platforma.TokenStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AuthFilterCsrfTest extends ApiTestBaza {

    @Autowired
    private TokenStore tokeni;

    private String novaSesija() {
        return tokeni.izdaj(
                new PrijavljeniKorisnik(1L, "csrf-test", "KORISNIK"));
    }

    @Test
    void sessionBezCsrfTokenaJeOdbijen() {
        String session = novaSesija();

        Odgovor odgovor = posalji(
                "POST", "/api/health", null, null, null,
                "Cookie", "SESSION=" + session);

        assertThat(odgovor.status()).isEqualTo(403);
    }

    @Test
    void sessionSValjanimCsrfTokenomProlaziFilter() {
        String session = novaSesija();
        String csrf = tokeni.izdajCsrf(session);

        Odgovor odgovor = posalji(
                "POST", "/api/health", null, null, null,
                "Cookie", "SESSION=" + session,
                "X-XSRF-TOKEN", csrf);

        assertThat(odgovor.status()).isNotEqualTo(403);
    }

    @Test
    void bearerTokenNeZahtijevaCsrfToken() {
        String bearer = tokeni.izdaj(
                new PrijavljeniKorisnik(2L, "bearer-test", "KORISNIK"));

        Odgovor odgovor = posalji(
                "POST", "/api/health", bearer, null, null);

        assertThat(odgovor.status()).isNotEqualTo(403);
    }
}
