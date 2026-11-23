
"""Kuke (hooks) za zap-baseline.py – izvršavaju se UNUTAR ZAP Docker kontejnera.

ZAP baseline je pasivan: spider + pasivna pravila, bez napada.
Neautenticiran spider vidi samo /, /prijava i /api/health, pa ovdje dodajemo:
  1. prijavu testnog korisnika (POST /api/auth/login) i Authorization zaglavlje
     za daljnje zahtjeve (ZAP Replacer),
  2. posjet putanjama iz dast-putanje.txt,
  3. ciljanu provjeru preusmjeravanja nakon prijave s vanjskim returnUrl,
     uz dohvat i slanje CSRF tokena,
  4. ciljanu CORS provjeru.

Lozinka i token se nikad ne ispisuju.
"""
import html
import json
import os
import re
import urllib.parse

ZLI_ORIGIN = "https://zlo.example"
ZLI_URL = "https://zlo.example/dast-provjera"
WRK = "/zap/wrk"


def _posalji(zap, metoda, url, zaglavlja=None, tijelo=""):
    """Šalje zahtjev kroz ZAP, bez praćenja preusmjeravanja."""
    host = urllib.parse.urlsplit(url).netloc
    redci = [f"{metoda} {url} HTTP/1.1", f"Host: {host}"]

    for k, v in (zaglavlja or {}).items():
        redci.append(f"{k}: {v}")

    if tijelo:
        redci.append(f"Content-Length: {len(tijelo.encode('utf-8'))}")

    zahtjev = "\r\n".join(redci) + "\r\n\r\n" + tijelo
    poruke = zap.core.send_request(request=zahtjev, followredirects="false")

    return poruke[0] if isinstance(poruke, list) and poruke else poruke


def _zaglavlje(odgovor_zaglavlja, ime):
    for redak in odgovor_zaglavlja.splitlines():
        if ":" in redak and redak.split(":", 1)[0].strip().lower() == ime.lower():
            return redak.split(":", 1)[1].strip()
    return None


def _izvuci_csrf_token(html_obrazac):
    """Izvlači CSRF token iz skrivenog HTML polja."""
    obrazac = (
        r'<input\b'
        r'(?=[^>]*\bname=["\']csrfToken["\'])'
        r'(?=[^>]*\bvalue=["\']([^"\']+)["\'])'
        r'[^>]*>'
    )

    matcher = re.search(obrazac, html_obrazac, re.IGNORECASE)

    if matcher:
        return html.unescape(matcher.group(1))

    return None


def _dodatni_nalaz(zap, poruka, naslov, opis, dokaz, cwe, rjesenje):
    """Dodaje upozorenje u ZAP ili ga zapisuje u dast-provjere.json."""
    try:
        zap.alert.add_alert(
            messageid=poruka["id"],
            name=naslov,
            riskid="3",
            confidenceid="3",
            description=opis,
            param="Origin",
            attack=ZLI_ORIGIN,
            evidence=dokaz,
            solution=rjesenje,
            cweid=str(cwe),
            wascid="14"
        )
        print(f"[dast] dodano upozorenje: {naslov}")

    except Exception as e:  # noqa: BLE001
        print(f"[dast] alert API nije dostupan ({e}); zapisujem u dast-provjere.json")

        putanja = os.path.join(WRK, "dast-provjere.json")
        nalazi = json.load(open(putanja, encoding="utf-8")) if os.path.exists(putanja) else []

        nalazi.append({
            "naslov": naslov,
            "rizik": 3,
            "url": poruka.get("requestHeader", "").split(" ")[1],
            "dokaz": dokaz,
            "cwe": cwe
        })

        with open(putanja, "w", encoding="utf-8") as datoteka:
            json.dump(nalazi, datoteka, ensure_ascii=False, indent=2)


def zap_started(zap, target):
    baza = target.rstrip("/")
    korisnik = os.environ.get("DAST_KORISNIK", "")
    lozinka = os.environ.get("DAST_LOZINKA", "")
    token = None

    # 1. Prijava putem JSON API-ja -> Bearer token
    try:
        tijelo = json.dumps({
            "username": korisnik,
            "password": lozinka
        })

        odg = _posalji(
            zap,
            "POST",
            baza + "/api/auth/login",
            {"Content-Type": "application/json"},
            tijelo
        )

        token = json.loads(odg.get("responseBody") or "{}").get("token")
        print("[dast] prijava testnog korisnika:", "uspjela" if token else "NIJE uspjela")

    except Exception as e:  # noqa: BLE001
        print(f"[dast] prijava nije uspjela: {e}")

    if token:
        try:
            zap.replacer.add_rule(
                description="dast-autorizacija",
                enabled="true",
                matchtype="REQ_HEADER",
                matchregex="false",
                matchstring="Authorization",
                replacement="Bearer " + token
            )
        except Exception as e:  # noqa: BLE001
            print(f"[dast] Replacer nije dostupan: {e}")

    # 2. Dodatne API putanje za pasivna pravila
    try:
        with open(os.path.join(WRK, "dast-putanje.txt"), encoding="utf-8") as datoteka:
            for redak in datoteka:
                p = redak.split("#", 1)[0].strip()
                if p:
                    zap.core.access_url(
                        url=baza + p,
                        followredirects="false"
                    )

    except Exception as e:  # noqa: BLE001
        print(f"[dast] putanje: {e}")

  # 3. Provjera open redirecta uz CSRF zaštitu
    try:
        forma_odgovor = _posalji(
            zap,
            "GET",
            baza + "/prijava"
        )

        html_obrazac = forma_odgovor.get("responseBody") or ""

        print("[dast] GET /prijava status:",
              (forma_odgovor.get("responseHeader") or "").split("\r\n")[0])
        print("[dast] GET /prijava Content-Type:",
              _zaglavlje(forma_odgovor.get("responseHeader") or "", "Content-Type"))
        print("[dast] HTML sadrži csrfToken:",
              "csrfToken" in html_obrazac)
        print("[dast] HTML početak:", repr(html_obrazac[:1000]))

        csrf_token = _izvuci_csrf_token(html_obrazac)

        if not csrf_token:
            print("[dast] nije pronađen CSRF token u obrascu prijave")
        else:
            forma = urllib.parse.urlencode({
                "username": korisnik,
                "password": lozinka,
                "returnUrl": ZLI_URL,
                "csrfToken": csrf_token
            })

            odg = _posalji(
                zap,
                "POST",
                baza + "/prijava",
                {"Content-Type": "application/x-www-form-urlencoded"},
                forma
            )

            status = (odg.get("responseHeader") or "").split("\r\n")[0]
            lokacija = _zaglavlje(
                odg.get("responseHeader") or "",
                "Location"
            )

            print(
                f"[dast] /prijava s vanjskim returnUrl -> {status}, "
                f"Location: {lokacija}"
            )

    except Exception as e:  # noqa: BLE001
        print(f"[dast] provjera preusmjeravanja: {e}")

    # 4. CORS: reflektira li poslužitelj proizvoljan Origin uz vjerodajnice?
    if token:
        for metoda, dodatno in (
            ("GET", {}),
            ("OPTIONS", {"Access-Control-Request-Method": "GET"})
        ):
            try:
                zag = {
                    "Origin": ZLI_ORIGIN,
                    "Authorization": "Bearer " + token,
                    **dodatno
                }

                odg = _posalji(
                    zap,
                    metoda,
                    baza + "/api/ja",
                    zag
                )

                rz = odg.get("responseHeader") or ""
                acao = _zaglavlje(rz, "Access-Control-Allow-Origin")
                acac = (
                    _zaglavlje(rz, "Access-Control-Allow-Credentials") or ""
                ).lower()

                print(
                    f"[dast] CORS {metoda} /api/ja: "
                    f"ACAO={acao} ACAC={acac or '-'}"
                )

                if acao in (ZLI_ORIGIN, "*") and (acac == "true" or acao == "*"):
                    _dodatni_nalaz(
                        zap,
                        odg,
                        "CORS: dopušten proizvoljan Origin uz vjerodajnice",
                        "Poslužitelj vraća Access-Control-Allow-Origin s originom "
                        "napadača (i Access-Control-Allow-Credentials: true), "
                        "pa zlonamjerna stranica može čitati odgovore API-ja "
                        "u ime prijavljenog korisnika.",
                        f"Access-Control-Allow-Origin: {acao}; "
                        f"Access-Control-Allow-Credentials: {acac}",
                        942,
                        "Dopustite samo origine s popisa iz konfiguracije."
                    )
                    break

            except Exception as e:  # noqa: BLE001
                print(f"[dast] CORS provjera ({metoda}): {e}")
