
package hr.span.akademija.medtermin.platforma;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.HtmlUtils;

/** Jednostavne HTML stranice (početna, prijava, promjena lozinke). */
public final class Html {

    public static final MediaType TEXT_HTML_UTF8 =
            new MediaType("text", "html", StandardCharsets.UTF_8);

    private Html() {
    }

    public static String escape(String tekst) {
        return tekst == null ? "" : HtmlUtils.htmlEscape(tekst, "UTF-8");
    }

    public static String stranica(String naslov, String tijelo) {
        return """
                <!DOCTYPE html>
                <html lang="hr">
                <head>
                <meta charset="UTF-8">
                <title>%s</title>
                <style>
                body { font-family: system-ui, sans-serif; max-width: 40rem; margin: 2rem auto; padding: 0 1rem; }
                label { display: block; margin-top: .75rem; }
                .poruka { color: #b00020; }
                </style>
                </head>
                <body>
                %s
                </body>
                </html>
                """.formatted(escape(naslov), tijelo);
    }

    public static ResponseEntity<String> odgovor(String html) {
        return ResponseEntity.ok()
                .contentType(TEXT_HTML_UTF8)
                .header("X-Frame-Options", "DENY")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy",
                        "default-src 'self'; "
                        + "style-src 'self' 'unsafe-inline'; "
                        + "script-src 'self'; "
                        + "img-src 'self' data:; "
                        + "font-src 'self'; "
                        + "object-src 'none'; "
                        + "base-uri 'self'; "
                        + "frame-ancestors 'none'; "
                        + "form-action 'self'")
                .header("Permissions-Policy",
                        "camera=(), microphone=(), geolocation=()")
                .header("Cross-Origin-Resource-Policy", "same-origin")
                .body(html);
    }
}
