
package hr.span.akademija.medtermin.termini;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class UvozTerminaTest {

    private final UvozTermina uvozTermina = new UvozTermina();

    @Test
    void odbijaXmlSVanjskimEntitetom() {
        String xml = """
                <?xml version="1.0"?>
                <!DOCTYPE termini [
                    <!ENTITY xxe SYSTEM "file:///etc/passwd">
                ]>
                <termini>
                    <termin>
                        <lijecnikId>&xxe;</lijecnikId>
                        <pocetak>2026-12-01T09:00</pocetak>
                        <kapacitet>1</kapacitet>
                    </termin>
                </termini>
                """;

        assertThrows(
                RuntimeException.class,
                () -> uvozTermina.procitaj(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        );
    }

    @Test
void uspjesnoCitaIspravanXml() {
    String xml = """
            <?xml version="1.0"?>
            <termini>
                <termin>
                    <lijecnikId>1</lijecnikId>
                    <pocetak>2026-12-01T09:00</pocetak>
                    <kapacitet>2</kapacitet>
                    <napomena>Jutarnji termin</napomena>
                </termin>
            </termini>
            """;

    var rezultat = uvozTermina.procitaj(
            xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));

    org.junit.jupiter.api.Assertions.assertEquals(1, rezultat.size());
    org.junit.jupiter.api.Assertions.assertEquals(1, rezultat.get(0).lijecnikId());
    org.junit.jupiter.api.Assertions.assertEquals(2, rezultat.get(0).kapacitet());
}
}
