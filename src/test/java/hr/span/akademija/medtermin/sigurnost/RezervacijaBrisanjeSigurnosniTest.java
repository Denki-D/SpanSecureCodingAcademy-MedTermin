package hr.span.akademija.medtermin.sigurnost;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import hr.span.akademija.medtermin.ApiTestBaza;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("sigurnost")
class RezervacijaBrisanjeSigurnosniTest extends ApiTestBaza {

    @Test
    @DisplayName("Korisnik ne smije obrisati tuđu rezervaciju")
    void drugiKorisnikNeMozeObrisatiTuduRezervaciju() {

        // Ana kreira rezervaciju.
        String anaToken = prijava("ana", "Ana12345!");

        long terminId = 0;

        for (JsonNode termin : get("/api/termini", anaToken).json()) {
            if (termin.get("slobodno").asInt() > 0) {
                terminId = termin.get("id").asLong();
                break;
            }
        }

        assertThat(terminId).isPositive();

        Odgovor kreiranje = postJson(
                "/api/rezervacije",
                anaToken,
                Map.of(
                        "terminId", terminId,
                        "napomena", "Test autorizacije brisanja",
                        "oib", "48291736504"
                )
        );

        assertThat(kreiranje.status()).isEqualTo(201);

        long rezervacijaId = kreiranje.json().get("id").asLong();

        // Marko se prijavljuje i pokušava obrisati Aninu rezervaciju.
        String markoToken = prijava("marko", "Marko12345!");

        Odgovor brisanje = delete(
                "/api/rezervacije/" + rezervacijaId,
                markoToken
        );

        // Ne otkrivamo postojanje tuđe rezervacije.
        assertThat(brisanje.status()).isEqualTo(404);

        // Anina rezervacija mora ostati u sustavu.
        boolean postoji = false;

        for (JsonNode rezervacija : get("/api/rezervacije/moje", anaToken).json()) {
            if (rezervacija.get("id").asLong() == rezervacijaId) {
                postoji = true;
                break;
            }
        }

        assertThat(postoji).isTrue();
    }
}