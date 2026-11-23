
package hr.span.akademija.medtermin.lijecnici;

import hr.span.akademija.medtermin.platforma.ApiException;
import hr.span.akademija.medtermin.platforma.PrijavljeniKorisnik;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lijecnici")
public class LijecnikController {

    public record Lijecnik(long id, String ime, String prezime, String specijalizacija, String ordinacija) {
    }

    private static final RowMapper<Lijecnik> MAPPER = (rs, i) -> new Lijecnik(
            rs.getLong("id"),
            rs.getString("ime"),
            rs.getString("prezime"),
            rs.getString("specijalizacija"),
            rs.getString("ordinacija")
    );

    private final JdbcTemplate jdbc;

    public LijecnikController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Popis liječnika, npr. GET /api/lijecnici?sort=prezime&smjer=asc. */
    
@GetMapping
public List<Lijecnik> popis(
        @RequestParam(defaultValue = "prezime") String sort,
        @RequestParam(defaultValue = "asc") String smjer,
        PrijavljeniKorisnik korisnik) {

    String sql = switch (sort.trim().toLowerCase(Locale.ROOT)) {
        case "ime" -> switch (smjer.trim().toLowerCase(Locale.ROOT)) {
            case "asc" -> "SELECT id, ime, prezime, specijalizacija, ordinacija FROM lijecnici ORDER BY ime ASC";
            case "desc" -> "SELECT id, ime, prezime, specijalizacija, ordinacija FROM lijecnici ORDER BY ime DESC";
            default -> throw ApiException.neispravno("Nedopušteni smjer sortiranja.");
        };
        case "prezime" -> switch (smjer.trim().toLowerCase(Locale.ROOT)) {
            case "asc" -> "SELECT id, ime, prezime, specijalizacija, ordinacija FROM lijecnici ORDER BY prezime ASC";
            case "desc" -> "SELECT id, ime, prezime, specijalizacija, ordinacija FROM lijecnici ORDER BY prezime DESC";
            default -> throw ApiException.neispravno("Nedopušteni smjer sortiranja.");
        };
        case "specijalizacija" -> switch (smjer.trim().toLowerCase(Locale.ROOT)) {
            case "asc" -> "SELECT id, ime, prezime, specijalizacija, ordinacija FROM lijecnici ORDER BY specijalizacija ASC";
            case "desc" -> "SELECT id, ime, prezime, specijalizacija, ordinacija FROM lijecnici ORDER BY specijalizacija DESC";
            default -> throw ApiException.neispravno("Nedopušteni smjer sortiranja.");
        };
        default -> throw ApiException.neispravno("Nedopušteno polje za sortiranje.");
    };

    return jdbc.query(sql, MAPPER);
}

}
