#!/usr/bin/env bash
# Tajne u kodu i povijesti (gitleaks). U git repozitoriju skenira SVE commitove
# (na CI-ju zato checkout s fetch-depth: 0) – tajna obrisana u novijem commitu i dalje je procurila.
source "$(dirname "$0")/zajednicko.sh"

GL="$ALATI/gitleaks-$GITLEAKS_VERZIJA"
base="https://github.com/gitleaks/gitleaks/releases/download/v$GITLEAKS_VERZIJA"

OS="$(uname -s)"

if [[ "$OS" == MINGW* || "$OS" == MSYS* ]]; then
  arhiva="gitleaks_${GITLEAKS_VERZIJA}_windows_x64.zip"
  BIN="$GL/gitleaks.exe"
  TIP="windows"
elif [[ "$OS" == Linux* ]]; then
  arhiva="gitleaks_${GITLEAKS_VERZIJA}_linux_x64.tar.gz"
  BIN="$GL/gitleaks"
  TIP="linux"
else
  echo "::error::Nepodržani OS: $OS"
  exit 1
fi

if [ ! -x "$BIN" ]; then
  mkdir -p "$GL"
  preuzmi "$base/$arhiva" "$GL/$arhiva" "$base/gitleaks_${GITLEAKS_VERZIJA}_checksums.txt"

  if [ "$TIP" = windows ]; then
    unzip -o "$GL/$arhiva" gitleaks.exe -d "$GL"
  else
    tar -xzf "$GL/$arhiva" -C "$GL" gitleaks
  fi
fi

# --redact: tajne se ne ispisuju u log/izvještaj; --exit-code 0: odluku donosi gate.
# Konfiguracija: .gitleaks.toml (zadana pravila + iznimke), .gitleaksignore (otisci prihvaćenih nalaza).
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  nacin=git
else
  nacin=dir
fi
"$BIN" "$nacin" --no-banner --redact --exit-code 0 \
  --report-format sarif --report-path "$IZVJESTAJI/gitleaks.sarif" .
echo "gitleaks ($nacin): izvještaj u izvjestaji/gitleaks.sarif"
