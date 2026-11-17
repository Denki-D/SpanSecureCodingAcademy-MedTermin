package hr.span.akademija.medtermin.sigurnost;

import static org.assertj.core.api.Assertions.assertThat;

import hr.span.akademija.medtermin.ApiTestBaza;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("sigurnost")
class NalaziAutorizacijaSigurnosniTest extends ApiTestBaza {

    @Test
    @DisplayName("Neispravan format ne smije zaobići provjeru vlasništva nalaza")
    void neispravanFormatNeZaobilaziProvjeruVlasnistva() {
        String token = prijava("ana", "Ana12345!");

        Odgovor odgovor = get("/api/nalazi/2?format=xyz", token);

        assertThat(odgovor.status()).isEqualTo(404);
    }
}