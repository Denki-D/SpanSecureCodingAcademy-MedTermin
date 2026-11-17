[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('ispravljeno', 'originalno')]
    [string]$Stanje,

    [string]$ProjectRoot = (Get-Location).Path,
    [string]$OutputRoot = 'C:\Users\denis\Desktop\Denis-Gascic-SPAN\alati',
    [string]$ZapReportPath,
    [string]$SastReportPath,
    [switch]$SkipTests,
    [switch]$SkipSnyk
)

$ErrorActionPreference = 'Stop'
$stageName = if ($Stanje -eq 'ispravljeno') { '01-ispravljeno' } else { '02-originalno' }
$stagePath = Join-Path $OutputRoot $stageName

if (Test-Path $stagePath) {
    throw "Odredište već postoji: $stagePath`nNe želim prebrisati ranije dokaze. Premjesti/obriši tu mapu ili odaberi drugi OutputRoot."
}

if (-not (Test-Path $ProjectRoot)) {
    throw "Projektni direktorij ne postoji: $ProjectRoot"
}
$ProjectRoot = (Resolve-Path $ProjectRoot).Path

# Omogući pozivanje iz korijena repozitorija ili iz podmape java.
$JavaRoot = $ProjectRoot
if (-not (Test-Path (Join-Path $JavaRoot 'pom.xml')) -and (Test-Path (Join-Path $ProjectRoot 'java\pom.xml'))) {
    $JavaRoot = Join-Path $ProjectRoot 'java'
}
if (-not (Test-Path (Join-Path $JavaRoot 'pom.xml'))) {
    throw "Ne mogu pronaći pom.xml. Pokreni skriptu iz java direktorija ili proslijedi -ProjectRoot koji sadrži pom.xml."
}
$JavaRoot = (Resolve-Path $JavaRoot).Path

# Pokušaj pronaći Git root.
$RepoRoot = $JavaRoot
$gitRootOutput = & git -C $JavaRoot rev-parse --show-toplevel 2>$null
if ($LASTEXITCODE -eq 0 -and $gitRootOutput) {
    $RepoRoot = (Resolve-Path ([string]$gitRootOutput)).Path
}

$dirs = @('git', 'izvorni-kod', 'maven', 'testovi', 'pokrivenost', 'snyk', 'zap', 'sast', 'dodatni-dokazi')
New-Item -ItemType Directory -Path $stagePath -Force | Out-Null
foreach ($dir in $dirs) {
    New-Item -ItemType Directory -Path (Join-Path $stagePath $dir) -Force | Out-Null
}

$manifest = New-Object System.Collections.Generic.List[string]
$manifest.Add("MedTermin – paket dokaza")
$manifest.Add("Stanje: $Stanje")
$manifest.Add("Datum: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')")
$manifest.Add("Repo root: $RepoRoot")
$manifest.Add("Java root: $JavaRoot")
$manifest.Add("Output: $stagePath")
$manifest.Add('')

# Ako je poznata instalacija Temurin JDK 21, koristi je za ovaj PowerShell proces.
$temurin21 = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot'
if (Test-Path (Join-Path $temurin21 'bin\java.exe')) {
    $env:JAVA_HOME = $temurin21
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
    $manifest.Add("JAVA_HOME postavljen na: $temurin21")
} else {
    $manifest.Add('Napomena: poznata Temurin 21 instalacija nije pronađena; koristi se trenutačni JAVA_HOME/PATH.')
}

# Verzije alata.
$toolInfo = Join-Path $stagePath 'maven\verzije-alata.txt'
"=== java -version ===" | Out-File $toolInfo -Encoding utf8
& java -version 2>&1 | Out-File $toolInfo -Append -Encoding utf8
"`r`n=== Maven Wrapper ===" | Out-File $toolInfo -Append -Encoding utf8
$mvnw = Join-Path $JavaRoot 'mvnw.cmd'
if (Test-Path $mvnw) {
    & $mvnw -version 2>&1 | Out-File $toolInfo -Append -Encoding utf8
} else {
    'mvnw.cmd nije pronađen.' | Out-File $toolInfo -Append -Encoding utf8
}
"`r`n=== Snyk CLI ===" | Out-File $toolInfo -Append -Encoding utf8
if (Get-Command snyk -ErrorAction SilentlyContinue) {
    & snyk --version 2>&1 | Out-File $toolInfo -Append -Encoding utf8
} else {
    'Snyk CLI nije pronađen u PATH-u.' | Out-File $toolInfo -Append -Encoding utf8
}

# Git identitet i diff: ključno za očuvanje trenutačnog lokalnog stanja prije checkout/reset.
$gitDir = Join-Path $stagePath 'git'
if (Get-Command git -ErrorAction SilentlyContinue) {
    $head = & git -C $RepoRoot rev-parse HEAD 2>$null
    if ($LASTEXITCODE -eq 0) { "HEAD: $head" | Out-File (Join-Path $gitDir 'commit.txt') -Encoding utf8 }
    $branch = & git -C $RepoRoot branch --show-current 2>$null
    "Branch: $branch" | Out-File (Join-Path $gitDir 'commit.txt') -Append -Encoding utf8
    & git -C $RepoRoot log -1 --decorate --oneline 2>&1 | Out-File (Join-Path $gitDir 'zadnji-commit.txt') -Encoding utf8
    & git -C $RepoRoot status --short --branch 2>&1 | Out-File (Join-Path $gitDir 'status.txt') -Encoding utf8
    & git -C $RepoRoot diff HEAD --binary 2>&1 | Out-File (Join-Path $gitDir 'sve-lokalne-promjene.patch') -Encoding utf8
    & git -C $RepoRoot diff --cached --binary 2>&1 | Out-File (Join-Path $gitDir 'staged-promjene.patch') -Encoding utf8
} else {
    'Git nije instaliran ili nije dostupan.' | Out-File (Join-Path $gitDir 'status.txt') -Encoding utf8
}

# ZIP izvornog stabla bez .git, target i IDE cachea. Time se čuva stvarno stanje koda,
# uključujući necommitane izmjene koje bi checkout/reset mogao odbaciti.
$sourceZip = Join-Path $stagePath 'izvorni-kod\projekt-kod.zip'
$excludeNames = @('.git', 'target', '.idea', '.vscode', 'node_modules')
$sourceItems = Get-ChildItem -LiteralPath $RepoRoot -Force | Where-Object { $excludeNames -notcontains $_.Name }
if ($sourceItems.Count -gt 0) {
    Compress-Archive -Path $sourceItems.FullName -DestinationPath $sourceZip -CompressionLevel Optimal
}

# Pomoćna funkcija za pokretanje naredbi i čuvanje izlaza + exit codea.
function Invoke-CapturedCommand {
    param(
        [string]$FilePath,
        [string[]]$Arguments,
        [string]$WorkingDirectory,
        [string]$OutputFile
    )
    Push-Location $WorkingDirectory
    try {
        & $FilePath @Arguments 2>&1 | Out-File -FilePath $OutputFile -Encoding utf8
        $code = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    return $code
}

$testExit = 'nije pokrenuto'
if (-not $SkipTests -and (Test-Path $mvnw)) {
    $testLog = Join-Path $stagePath 'maven\clean-test.log'
    $testExit = Invoke-CapturedCommand -FilePath $mvnw -Arguments @('clean', 'test') -WorkingDirectory $JavaRoot -OutputFile $testLog
    "mvnw clean test exit code: $testExit" | Out-File (Join-Path $stagePath 'maven\exit-codes.txt') -Encoding utf8
} else {
    'Maven testovi preskočeni ili wrapper nije pronađen.' | Out-File (Join-Path $stagePath 'maven\exit-codes.txt') -Encoding utf8
}

# Dependency tree nakon što je test build izvršen.
if (Test-Path $mvnw) {
    $depLog = Join-Path $stagePath 'maven\dependency-tree.txt'
    $depExit = Invoke-CapturedCommand -FilePath $mvnw -Arguments @('dependency:tree') -WorkingDirectory $JavaRoot -OutputFile $depLog
    "dependency:tree exit code: $depExit" | Out-File (Join-Path $stagePath 'maven\exit-codes.txt') -Append -Encoding utf8
}

# Snyk JSON i tekstualni rezultat. Snyk exit 1 zbog ranjivosti nije fatalan za ovu skriptu.
if (-not $SkipSnyk -and (Get-Command snyk -ErrorAction SilentlyContinue)) {
    $snykJson = Join-Path $stagePath 'snyk\snyk-test.json'
    $snykText = Join-Path $stagePath 'snyk\snyk-test.txt'
    Push-Location $JavaRoot
    try {
        & snyk test "--json-file-output=$snykJson" 2>&1 | Out-File -FilePath $snykText -Encoding utf8
        $snykExit = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    "snyk test exit code: $snykExit (0 = no known issues; 1 = issues found; other = execution/configuration error)" | Out-File (Join-Path $stagePath 'maven\exit-codes.txt') -Append -Encoding utf8
} else {
    'Snyk nije pokrenut (preskočen ili CLI nije dostupan).' | Out-File (Join-Path $stagePath 'snyk\NAPOMENA.txt') -Encoding utf8
}

# Kopiraj Maven/Surefire izvještaje.
$surefire = Join-Path $JavaRoot 'target\surefire-reports'
if (Test-Path $surefire) {
    Copy-Item $surefire (Join-Path $stagePath 'testovi\surefire-reports') -Recurse -Force
}

# Izračunaj sažetak testova iz Surefire XML-a.
$xmlFiles = @(Get-ChildItem -Path (Join-Path $JavaRoot 'target\surefire-reports') -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue)
if ($xmlFiles.Count -gt 0) {
    [long]$tests = 0; [long]$failures = 0; [long]$errors = 0; [long]$skipped = 0
    foreach ($file in $xmlFiles) {
        try {
            [xml]$x = Get-Content $file.FullName
            $tests += [long]$x.testsuite.tests
            $failures += [long]$x.testsuite.failures
            $errors += [long]$x.testsuite.errors
            $skipped += [long]$x.testsuite.skipped
        } catch { }
    }
    @("Test suites: $($xmlFiles.Count)", "Tests: $tests", "Failures: $failures", "Errors: $errors", "Skipped: $skipped") |
        Out-File (Join-Path $stagePath 'testovi\test-summary.txt') -Encoding utf8
}

# Kopiraj JaCoCo HTML/CSV/XML i izračunaj line coverage.
$jacoco = Join-Path $JavaRoot 'target\site\jacoco'
if (Test-Path $jacoco) {
    Copy-Item $jacoco (Join-Path $stagePath 'pokrivenost\jacoco') -Recurse -Force
    $csv = Join-Path $jacoco 'jacoco.csv'
    if (Test-Path $csv) {
        $rows = Import-Csv $csv
        [long]$missed = 0; [long]$covered = 0
        foreach ($row in $rows) {
            $missed += [long]$row.LINE_MISSED
            $covered += [long]$row.LINE_COVERED
        }
        $total = $missed + $covered
        $pct = if ($total -gt 0) { [math]::Round(($covered / $total) * 100, 2) } else { 0 }
        @("Covered lines: $covered", "Missed lines: $missed", "Total lines: $total", "Line coverage: $pct%") |
            Out-File (Join-Path $stagePath 'pokrivenost\jacoco-summary.txt') -Encoding utf8
    }
} else {
    'JaCoCo report nije pronađen. Provjeri je li Maven test build uspio i generirao target/site/jacoco.' |
        Out-File (Join-Path $stagePath 'pokrivenost\NAPOMENA.txt') -Encoding utf8
}

# Kopiraj postojeće SpotBugs/SAST rezultate ako postoje.
$spotbugsCandidates = @(
    (Join-Path $JavaRoot 'target\spotbugsXml.xml'),
    (Join-Path $JavaRoot 'target\spotbugs\spotbugs.xml'),
    (Join-Path $RepoRoot 'alati\sast\spotbugs-final.xml')
)
foreach ($candidate in $spotbugsCandidates) {
    if (Test-Path $candidate -PathType Leaf) {
        Copy-Item $candidate (Join-Path $stagePath 'sast') -Force
    }
}
if ($SastReportPath -and (Test-Path $SastReportPath)) {
    Copy-Item $SastReportPath (Join-Path $stagePath 'sast') -Force
}
if (-not (Get-ChildItem (Join-Path $stagePath 'sast') -File -ErrorAction SilentlyContinue)) {
    'SAST izvještaj nije pronađen/proslijeđen. Ova mapa ne predstavlja dokaz SAST skeniranja.' |
        Out-File (Join-Path $stagePath 'sast\NAPOMENA.txt') -Encoding utf8
}

# ZAP izvještaj mora se prethodno izvesti iz ZAP GUI-a ili API-ja.
if ($ZapReportPath -and (Test-Path $ZapReportPath)) {
    Copy-Item $ZapReportPath (Join-Path $stagePath 'zap') -Force
} else {
    'ZAP izvještaj nije proslijeđen. Izvezi HTML/JSON report iz ZAP-a pa ponovi kopiranje ručno u ovu mapu.' |
        Out-File (Join-Path $stagePath 'zap\NAPOMENA.txt') -Encoding utf8
}

# Kopiraj već postojeće dokaze/slike iz uobičajenih projektnih mapa, bez kopiranja izlazne mape.
foreach ($name in @('docs', 'dokazi', 'evidence', 'screenshots', 'snimke', 'alati')) {
    $candidate = Join-Path $RepoRoot $name
    if ((Test-Path $candidate -PathType Container) -and ((Resolve-Path $candidate).Path -ne (Resolve-Path $OutputRoot -ErrorAction SilentlyContinue).Path)) {
        $dest = Join-Path $stagePath 'dodatni-dokazi'
        $destName = $name
        if (-not (Test-Path (Join-Path $dest $destName))) {
            Copy-Item $candidate (Join-Path $dest $destName) -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
}

$manifest.Add("Maven clean test exit code: $testExit")
$manifest.Add('Tumačenje Maven exit codea: 0 = BUILD SUCCESS; različito od 0 = provjeri maven/clean-test.log.')
$manifest.Add('Napomena: Snyk rezultat nalazi se u mapi snyk. Exit code 1 uobičajeno znači da su pronađene ranjivosti, a ne da je arhiviranje neuspjelo.')
$manifest.Add('Napomena: ZAP i SAST izvještaji kopiraju se samo ako su postojali/proslijeđeni; provjeri odgovarajuće NAPOMENA.txt datoteke.')
$manifest.Add('Izvorni kod pohranjen je u izvorni-kod/projekt-kod.zip bez .git i target direktorija; Git commit/diff/status nalaze se u mapi git.')
$manifest | Out-File (Join-Path $stagePath 'MANIFEST.txt') -Encoding utf8

Write-Host ''
Write-Host "Dokazi spremljeni u: $stagePath" -ForegroundColor Green
Write-Host "Maven clean test exit code: $testExit"
Write-Host 'Provjeri MANIFEST.txt, maven/exit-codes.txt i eventualne NAPOMENA.txt datoteke.'
