
package hr.span.akademija.medtermin.platforma;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import java.util.Set;

/** Tokeni prijave i CSRF tokeni čuvaju se u memoriji. */
@Component
public class TokenStore {

    private final SecureRandom random = new SecureRandom();

    private final ConcurrentHashMap<String, PrijavljeniKorisnik> tokeni = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> csrfTokeni = new ConcurrentHashMap<>();
    private final Set<String> tokeniPrijave = ConcurrentHashMap.newKeySet();

    public String izdaj(PrijavljeniKorisnik korisnik) {
        String token = noviToken();
        tokeni.put(token, korisnik);
        return token;
    }

    public PrijavljeniKorisnik korisnik(String token) {
        return token == null ? null : tokeni.get(token);
    }

    public String izdajCsrf(String tokenSesije) {
        if (!tokeni.containsKey(tokenSesije)) {
            throw new IllegalArgumentException("Nevaljana sesija.");
        }

        String csrf = noviToken();
        csrfTokeni.put(tokenSesije, csrf);
        return csrf;
    }

    public boolean provjeriCsrf(String tokenSesije, String csrf) {
        if (tokenSesije == null || csrf == null) {
            return false;
        }

        String ocekivani = csrfTokeni.get(tokenSesije);
        return ocekivani != null
                && java.security.MessageDigest.isEqual(
                        ocekivani.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        csrf.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String noviToken() {
        byte[] bajtovi = new byte[32];
        random.nextBytes(bajtovi);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bajtovi);
    }

    public String izdajTokenPrijave() {
    String token = noviToken();
    tokeniPrijave.add(token);
    return token;
}

public boolean provjeriTokenPrijave(String token) {
    return token != null && tokeniPrijave.remove(token);
}
}
