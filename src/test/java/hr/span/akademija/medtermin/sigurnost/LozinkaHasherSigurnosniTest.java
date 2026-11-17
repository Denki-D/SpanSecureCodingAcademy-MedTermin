package hr.span.akademija.medtermin.sigurnost;

import static org.assertj.core.api.Assertions.assertThat;

import hr.span.akademija.medtermin.platforma.LozinkaHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("sigurnost")
class LozinkaHasherSigurnosniTest {

    private final LozinkaHasher hasher = new LozinkaHasher();

    @Test
    @DisplayName("Ista lozinka mora dobiti različite hash vrijednosti")
    void istaLozinkaMoraImatiRazliciteHashVrijednosti() {

        String lozinka = "TestLozinka123!";

        String hash1 = hasher.hash(lozinka);
        String hash2 = hasher.hash(lozinka);

        // Svaki hash mora imati vlastitu nasumičnu sol.
        assertThat(hash1).isNotEqualTo(hash2);

        // Oba hasha moraju biti valjana za provjeru iste lozinke.
        assertThat(hasher.provjeri(lozinka, hash1)).isTrue();
        assertThat(hasher.provjeri(lozinka, hash2)).isTrue();

        // Pogrešna lozinka mora biti odbijena.
        assertThat(hasher.provjeri("PogresnaLozinka123!", hash1)).isFalse();
    }
}