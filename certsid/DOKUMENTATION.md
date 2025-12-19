# CERTSID - Vollständige Projekt-Dokumentation

**Projekt:** IT-Sicherheit - Certificate & Identity Management  
**Team:** Team 12  
**Datum:** 9. Dezember 2025

---

## Inhaltsverzeichnis

1. [Schnellübersicht: Alle Java-Dateien](#schnellübersicht-alle-java-dateien)
2. [Projektübersicht](#projektübersicht)
3. [Architektur](#architektur)
4. [Module und Komponenten](#module-und-komponenten)
5. [Sicherheitskonzepte](#sicherheitskonzepte)
6. [Ablaufdiagramme](#ablaufdiagramme)
7. [Deployment](#deployment)
8. [Bekannte Schwachstellen](#bekannte-schwachstellen)

---

## Schnellübersicht: Alle Java-Dateien

### Server-Komponenten (laufen im WKD-Server)

| Datei | Zweck | Funktionalitäten | Verknüpfungen | Wer ruft auf |
|-------|-------|------------------|---------------|--------------|
| **WKDServer.java** | Hauptserver mit HTTP-Endpunkten | • HTTP-Server (Port 8000)<br>• WKD-Handler (Schlüssel-Auslieferung)<br>• ProtectedHandler (Auth + ACL)<br>• Cookie-Parsing<br>• Response-Handling | Nutzt: GpgService, SessionManager, ChallengeManager | JVM (main-Methode) |
| **GpgService.java** | GPG-Kommandozeilen-Wrapper | • getPublicKey(email): Exportiert PGP-Key<br>• verifySignature(): Verifiziert PGP-Signatur<br>• ProcessBuilder für GPG-Aufrufe | Ruft auf: GPG-Binary | WKDHandler, ProtectedHandler |
| **ChallengeManager.java** | Challenge-Nonce-Verwaltung | • generateChallenge(): 512-Bit SecureRandom Nonce<br>• getChallenge(): Holt gespeicherte Challenge<br>• removeChallenge(): Replay-Schutz | ConcurrentHashMap für Thread-Safety<br>Nutzt: SecureRandom, Base64 | ProtectedHandler |
| **SessionManager.java** | Session-Verwaltung | • createSession(): SHA-256 Hash-basierte Session-ID<br>• getUserFromSession(): Validierung<br>• invalidateSession(): Logout | HashMap für Session-Speicher<br>Nutzt: MessageDigest, SecureRandom | ProtectedHandler |

### Client-Tools (Standalone-Programme)

| Datei | Zweck | Funktionalitäten | Verknüpfungen | Wer ruft auf |
|-------|-------|------------------|---------------|--------------|
| **RetrieveCreds.java** | User-Key Download & Import | • downloadKey(): WKD HTTP-Request<br>• importKeyToGPG(): Import in Keyring<br>• checkGPGTrust(): Trust-Level prüfen<br>• addToAllowlist(): Allowlist-Management<br>• createUserFile(): Benutzerdatei anlegen | Ruft auf: WKD-Server (HTTP), GPG-Binary | Kommandozeile (manuell) |
| **FetchTrustCA.java** | CA Trust-Bootstrapping | • CA-Key Download via WKD<br>• Import in Keyring<br>• Fingerprint-Extraktion<br>• Trust auf ULTIMATE setzen (6) | Ruft auf: WKD-Server (HTTP), GPG-Binary<br>Feste Parameter: CA_USER="pgp-ca" | Kommandozeile (manuell, einmalig) |

### Beziehungen zwischen den Komponenten

```
Kommandozeile                    HTTP-Server (Port 8000)
     │                                   │
     ├─── FetchTrustCA.java ───┐        │
     │                          │        │
     └─── RetrieveCreds.java ──┼────────┤
                                │        │
                                ↓        ↓
                           WKD-Endpunkt  Protected-Endpunkt
                                │              │
                                ↓              ↓
                          ┌─────────────────────────┐
                          │    WKDServer.java       │
                          ├─────────────────────────┤
                          │ • WKDHandler            │
                          │ • ProtectedHandler      │
                          └───┬──────────┬──────────┘
                              │          │
              ┌───────────────┼──────────┼──────────────┐
              │               │          │              │
              ↓               ↓          ↓              ↓
      ┌───────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐
      │ GpgService│  │ Challenge  │  │  Session   │  │    GPG     │
      │   .java   │  │ Manager    │  │  Manager   │  │  Keyring   │
      │           │  │   .java    │  │   .java    │  │            │
      └─────┬─────┘  └────────────┘  └────────────┘  └────────────┘
            │
            ↓
      GPG-Binary
```

### Aufruf-Hierarchie

**Server-Seite:**
```
main()
 └─> HttpServer.start()
      ├─> WKDHandler.handle()
      │    └─> GpgService.getPublicKey()
      │         └─> ProcessBuilder("gpg --export")
      │
      └─> ProtectedHandler.handle()
           ├─> SessionManager.getUserFromSession()
           ├─> ChallengeManager.generateChallenge()
           ├─> ChallengeManager.getChallenge()
           ├─> GpgService.verifySignature()
           │    └─> ProcessBuilder("gpg --verify")
           ├─> ChallengeManager.removeChallenge()
           └─> SessionManager.createSession()
```

**Client-Seite:**
```
FetchTrustCA.main()
 ├─> HttpURLConnection.connect() → WKD-Server
 ├─> Files.copy() → Temp-Datei
 ├─> ProcessBuilder("gpg --import")
 ├─> ProcessBuilder("gpg --with-colons --show-keys") → Fingerprint
 └─> ProcessBuilder("gpg --import-ownertrust") → Trust setzen

RetrieveCreds.main()
 ├─> downloadKey() → HTTP GET zu WKD-Server
 ├─> importKeyToGPG() → ProcessBuilder("gpg --import")
 ├─> checkGPGTrust() → ProcessBuilder("gpg --list-keys")
 ├─> addToAllowlist() → Datei-I/O
 └─> createUserFile() → Datei-I/O
```

---

---

## 1. Projektübersicht

### Zweck
Das CERTSID-Projekt implementiert:
- **Web Key Directory (WKD) Server**: RFC-konformer Server zur Auslieferung öffentlicher PGP-Schlüssel
- **Challenge-Response-Authentifizierung**: Sichere Authentifizierung mit PGP-Signaturen
- **Session-Management**: Cookie-basierte Sessions mit kryptographisch sicheren IDs
- **Access Control List (ACL)**: Rollenbasierte Zugriffskontrolle (Owner/Admin)

### Technologien
- **Sprache:** Java 11+
- **HTTP-Server:** `com.sun.net.httpserver.HttpServer` (JDK-integriert)
- **Kryptographie:** GPG (GNU Privacy Guard)
- **Datenformat:** PGP/OpenPGP (RFC 4880)

---

## 2. Architektur

### Komponentendiagramm

```
┌─────────────────────────────────────────────────────────────┐
│                        WKDServer                            │
│  (Hauptserver, Port 8000)                                   │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────────┐    ┌──────────────────────────┐   │
│  │   WKDHandler        │    │   ProtectedHandler       │   │
│  │ /well-known/...     │    │   /labornutzer/          │   │
│  │                     │    │                          │   │
│  │ • Liefert Public    │    │ • Challenge-Response     │   │
│  │   Keys aus GPG      │    │ • Session-Management     │   │
│  │   Keyring           │    │ • ACL (Owner/Admin)      │   │
│  └─────────────────────┘    └──────────────────────────┘   │
│           │                            │                    │
│           ├────────────────────────────┤                    │
│           ↓                            ↓                    │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Service-Layer                           │   │
│  ├─────────────────────────────────────────────────────┤   │
│  │                                                      │   │
│  │  GpgService          ChallengeManager  SessionMgr   │   │
│  │  • getPublicKey()    • generate()      • create()   │   │
│  │  • verifySignature() • getChallenge()  • getUser()  │   │
│  │                      • remove()        • invalidate│   │
│  │                                                      │   │
│  └─────────────────────────────────────────────────────┘   │
│                          │                                  │
│                          ↓                                  │
│                   ┌────────────┐                            │
│                   │    GPG     │                            │
│                   │  Keyring   │                            │
│                   └────────────┘                            │
└─────────────────────────────────────────────────────────────┘
```

### Datenfluss

#### WKD-Anfrage (Schlüssel abrufen)
```
Client → GET /well-known/openpgpkey/{domain}/hu/{hash}?l={user}
       → WKDHandler
       → GpgService.getPublicKey(email)
       → GPG: "gpg --export email"
       → Binärdaten (Public Key)
       → Client (200 OK + Key Data)
```

#### Authentifizierung (Challenge-Response)
```
1) Client → GET /labornutzer/lars.fischer
   Server → 401 + WWW-Authenticate: Challenge={nonce_base64}

2) Client → Signiert Nonce mit privatem Schlüssel
   Client → GET /labornutzer/lars.fischer
            Authorization: GPGSig {signature_base64}

3) Server → ChallengeManager.getChallenge()
   Server → GpgService.verifySignature(nonce, signature, email)
   Server → GPG: "gpg --verify sig.asc nonce.txt"
   Server → SessionManager.createSession(user)
   Server → 200 OK + Set-Cookie: ITS25-SID={session_id}

4) Client → GET /labornutzer/alice.mueller
            Cookie: ITS25-SID={session_id}
   Server → SessionManager.getUserFromSession()
   Server → ACL-Check (isOwner || isAdmin)
   Server → 200 OK + Dateiinhalt
```

---

## 3. Module und Komponenten

### 3.1 WKDServer.java

**Hauptklasse:** Startet HTTP-Server und registriert Handler

**Funktionalitäten:**
- HTTP-Server auf Port 8000
- Zwei Endpunkte: WKD und geschützter Bereich
- Cookie-Parsing und Response-Handling

**Aufgerufen durch:** JVM (main-Methode)

**Ruft auf:**
- `WKDHandler.handle()`
- `ProtectedHandler.handle()`
- `GpgService.*`
- `SessionManager.*`
- `ChallengeManager.*`

**Wichtige Konstanten:**
```java
PORT = 8000
VALID_DOMAINS = ["smail.hs-bremerhaven.de", ...]
WKD_CONTEXT = "/well-known/openpgpkey/"
LABOR_CONTEXT = "/labornutzer/"
```

---

### 3.2 WKDHandler (innere Klasse)

**Zweck:** Beantwortet WKD-Anfragen nach öffentlichen Schlüsseln

**Funktionalitäten:**
- Parst WKD-konforme URLs
- Extrahiert Domain und lokalen Teil der E-Mail
- Ruft GPG auf zum Export des öffentlichen Schlüssels
- Sendet Schlüssel als `application/octet-stream`

**URL-Format:**
```
/well-known/openpgpkey/{domain}/hu/{hash}?l={localpart}
Beispiel: /well-known/openpgpkey/hs-bremerhaven.de/hu/000...?l=kevin
```

**Ablauf:**
1. Prüfe HTTP-Methode (nur GET erlaubt)
2. Validiere Pfadstruktur (3 Teile: domain/hu/hash)
3. Parse Query-Parameter `l=` für lokalen Teil
4. Konstruiere E-Mail: `{localpart}@{domain}`
5. Hole Schlüssel: `gpgService.getPublicKey(email)`
6. Sende Schlüssel oder 404

**Verknüpfungen:**
- Aufgerufen durch: `HttpServer` bei Requests auf `/well-known/openpgpkey/*`
- Ruft auf: `GpgService.getPublicKey()`

---

### 3.3 ProtectedHandler (innere Klasse)

**Zweck:** Schützt Ressourcen mit Challenge-Response-Auth und ACL

**Funktionalitäten:**
- Session-Verwaltung (Cookie-basiert)
- Challenge-Response-Authentifizierung (PGP-Signatur)
- Access Control List (Owner/Admin-Rechte)
- Datei-Auslieferung bei Berechtigung

**URL-Format:**
```
/labornutzer/{username}
Beispiel: /labornutzer/lars.fischer
```

**Ablauf:**

**Fall 1: Mit gültiger Session**
```
1. Cookie parsen → sessionId
2. SessionManager.getUserFromSession(sessionId) → sessionUser
3. ACL-Prüfung:
   - isOwner = (sessionUser == requestedResource)
   - isAdmin = (sessionUser == "lars.fischer")
4. Bei Erlaubnis: Datei ausliefern (200 OK)
   Bei Ablehnung: 403 Forbidden
```

**Fall 2: Mit Authorization-Header (Login-Versuch)**
```
1. Header "Authorization: GPGSig {base64}" parsen
2. Challenge holen: challengeManager.getChallenge(clientIP:resource)
3. Signatur dekodieren: Base64.decode(base64)
4. Für jede Domain in VALID_DOMAINS:
   - candidateEmail = resource + "@" + domain
   - gpgService.verifySignature(nonce, signature, candidateEmail)
5. Bei Erfolg:
   - Challenge entfernen (Replay-Schutz)
   - Session erstellen: sessionManager.createSession(resource)
   - Cookie setzen + Datei senden (200 OK)
6. Bei Fehler: 403 Forbidden
```

**Fall 3: Ohne Session und ohne Auth-Header (Fallback)**
```
1. Neue Challenge generieren: challengeManager.generateChallenge(clientIP:resource)
2. WWW-Authenticate Header setzen: "Challenge={nonce_base64}"
3. 401 Unauthorized senden
```

**Verknüpfungen:**
- Aufgerufen durch: `HttpServer` bei Requests auf `/labornutzer/*`
- Ruft auf: 
  - `SessionManager.getUserFromSession()`
  - `SessionManager.createSession()`
  - `ChallengeManager.generateChallenge()`
  - `ChallengeManager.getChallenge()`
  - `ChallengeManager.removeChallenge()`
  - `GpgService.verifySignature()`

---

### 3.4 GpgService.java

**Zweck:** Wrapper für GPG-Kommandozeilen-Operationen

**Funktionalitäten:**
1. **getPublicKey(String email)**: Exportiert öffentlichen Schlüssel
2. **verifySignature(byte[] nonce, byte[] signature, String expectedEmail)**: Verifiziert PGP-Signatur

**Methode: getPublicKey()**
```java
Zweck: Exportiert Public Key aus GPG-Keyring
Eingabe: E-Mail-Adresse (z.B. "alice@example.org")
Ausgabe: byte[] mit Schlüsseldaten oder null

Ablauf:
1. ProcessBuilder: "gpg --export {email}"
2. InputStream lesen (1024 Byte Buffer)
3. ByteArrayOutputStream sammelt alle Bytes
4. Warten auf Prozess-Ende
5. Bei Exit-Code 0 und Daten > 0: Schlüssel zurückgeben
6. Sonst: null
```

**Methode: verifySignature()**
```java
Zweck: Verifiziert PGP-Signatur gegen Nonce
Eingabe: 
  - nonce: Originaldaten (Challenge)
  - signature: PGP-Signatur
  - expectedEmail: Erwartete Signer-Email
Ausgabe: boolean (true = gültig, false = ungültig)

Ablauf:
1. Erstelle temp Dateien: nonceFile, sigFile
2. Schreibe nonce → nonceFile
3. Schreibe signature → sigFile
4. ProcessBuilder: "gpg --batch --ignore-time-conflict --verify sigFile nonceFile"
5. redirectErrorStream(true) - GPG schreibt nach stderr
6. Lese stdout/stderr, suche nach:
   - "Good signature" → signatureValid = true
   - expectedEmail in Ausgabe → userMatch = true
7. Prüfe: Exit-Code == 0 && signatureValid && userMatch
8. Finally: Lösche temp Dateien
9. Return boolean
```

**Verknüpfungen:**
- Aufgerufen durch: `WKDHandler`, `ProtectedHandler`
- Ruft auf: GPG-Binary via `ProcessBuilder`

**Externe Abhängigkeiten:**
- GPG muss installiert sein (`gpg` command)
- Keyring muss öffentliche Schlüssel enthalten

---

### 3.5 ChallengeManager.java

**Zweck:** Verwaltung von Challenge-Nonces für Challenge-Response-Auth

**Funktionalitäten:**
1. **generateChallenge(String identifier)**: Erzeugt 512-Bit Nonce
2. **getChallenge(String identifier)**: Holt gespeicherte Challenge
3. **removeChallenge(String identifier)**: Löscht Challenge (Replay-Schutz)

**Datenstruktur:**
```java
challengeStore: ConcurrentHashMap<String, byte[]>
Key:   "ClientIP:Ressource" (z.B. "192.168.1.100:lars.fischer")
Value: byte[64] (512-Bit Nonce)
```

**Methode: generateChallenge()**
```java
Eingabe: identifier (String)
Ausgabe: Base64-kodierte Nonce (String)

Ablauf:
1. Erstelle byte[64] (= 512 Bit)
2. SecureRandom.nextBytes(nonce) - kryptographisch sicher
3. Speichere in challengeStore: put(identifier, nonce)
4. Base64-kodiere: Base64.getEncoder().encodeToString(nonce)
5. Return Base64-String (für WWW-Authenticate Header)
```

**Sicherheitsaspekte:**
- **SecureRandom**: Kryptographisch sicherer RNG (nicht `Random`!)
- **512 Bit**: Ausreichend groß gegen Brute-Force
- **Pro Client/Ressource**: Verhindert Cross-Resource Replay
- **Überschreiben**: Alte Challenge wird überschrieben → nur eine aktiv

**Verknüpfungen:**
- Aufgerufen durch: `ProtectedHandler`
- Ruft auf: `java.security.SecureRandom`, `java.util.Base64`

---

### 3.6 SessionManager.java

**Zweck:** Verwaltung von Benutzersessions nach erfolgreicher Authentifizierung

**Funktionalitäten:**
1. **createSession(String userId)**: Erstellt neue Session mit kryptographischer ID
2. **getUserFromSession(String sessionId)**: Validiert Session, gibt User-ID zurück
3. **invalidateSession(String sessionId)**: Löscht Session (Logout)

**Datenstruktur:**
```java
sessionStore: HashMap<String, String>
Key:   Session-ID (Base64-kodierter SHA-256 Hash)
Value: User-ID (Benutzername, z.B. "lars.fischer")
```

**Methode: createSession()**
```java
Eingabe: userId (String, z.B. "lars.fischer")
Ausgabe: Session-ID (String, Base64-kodiert)

Algorithmus:
1. Generiere 16-Byte Nonce (SecureRandom)
2. Base64-kodiere Nonce → randomStr
3. Hole aktuellen Zeitstempel → dateStr (ISO-8601)
4. Konkateniere: input = randomStr + dateStr + userId + TEAM_NAME
5. Berechne SHA-256 Hash: digest.digest(input.getBytes())
6. Base64-kodiere Hash → sessionId (44 Zeichen)
7. Speichere: sessionStore.put(sessionId, userId)
8. Return sessionId

Beispiel Session-ID:
"5f3e9a7b2c1d8e4f0a6b9c2e3d7f8g1h2i3j4k5l6m7n8o9p0q=="
```

**Sicherheitsaspekte:**
- **SHA-256**: Kryptographischer Hash, nicht umkehrbar
- **Zufälligkeit**: 128-Bit Nonce verhindert Vorhersagbarkeit
- **Zeitstempel**: Jede Session ist zeitlich einzigartig
- **Team-Name**: Zusätzliche Entropie

**Verknüpfungen:**
- Aufgerufen durch: `ProtectedHandler`
- Ruft auf: `java.security.MessageDigest`, `java.security.SecureRandom`

---

### 3.7 RetrieveCreds.java (Optional, Client-Tool)

**Zweck:** Client-Programm zum Download und Import von PGP-Schlüsseln via WKD

**Funktionalitäten:**
1. Lädt öffentlichen Schlüssel vom WKD-Server
2. Importiert Schlüssel in lokalen GPG-Keyring
3. Prüft Vertrauenswürdigkeit (Trust-Level)
4. Fügt User zur Allowlist hinzu
5. Erstellt Benutzerdatei (für Modul 4)

**Ablauf:**
```
1. Kommandozeilen-Argumente parsen:
   - Server-IP, Port, Domain, User
   
2. downloadKey():
   - Konstruiere WKD-URL
   - HTTP GET Request
   - Speichere Key in temp Datei
   
3. importKeyToGPG():
   - "gpg --import {keyFile}"
   - Warte auf Exit-Code 0
   
4. checkGPGTrust():
   - "gpg --list-keys --with-colons {email}"
   - Parse Ausgabe, suche "pub:" Zeile
   - Trust-Level im 2. Feld (f/u = vertrauenswürdig)
   
5. Bei Erfolg:
   - addToAllowlist(user)
   - createUserFile(user)
```

**Verknüpfungen:**
- Aufgerufen durch: Kommandozeile (Standalone-Tool)
- Ruft auf: WKD-Server (HTTP), GPG-Binary

---

### 3.8 FetchTrustCA.java (Trust-Bootstrapping-Tool)

**Zweck:** Trust-Bootstrapping für Certificate Authority (CA) - lädt CA-Public-Key herunter und setzt Trust-Level auf ULTIMATE

**Funktionalitäten:**
1. **CA-Key Download**: Lädt öffentlichen Schlüssel der CA vom WKD-Server
2. **Import in Keyring**: Importiert CA-Key in lokales GPG-Keyring
3. **Fingerprint-Extraktion**: Extrahiert Fingerprint aus importiertem Schlüssel
4. **Trust-Level setzen**: Setzt Trust auf ULTIMATE (6) via `gpg --import-ownertrust`

**Kommandozeilen-Verwendung:**
```bash
java FetchTrustCA <IP> <Port> <Domain>
Beispiel: java FetchTrustCA 10.42.1.50 8000 team-12.example.org
```

**Ablauf im Detail:**
```
1. Argument-Validierung:
   - Prüfe ob IP, Port und Domain übergeben wurden
   
2. URL-Konstruktion:
   - Format: http://{IP}:{Port}/well-known/openpgpkey/{Domain}/hu/{WKD_ID}?l=pgp-ca
   - WKD_ID: Fester Hash "00000000000000000000000000000000"
   - CA_USER: Fester Username "pgp-ca"
   
3. HTTP-Download:
   - HttpURLConnection.openConnection()
   - Prüfe HTTP 200 OK
   - Speichere Schlüssel in temporärer Datei (ca-key-*.asc)
   
4. GPG-Import:
   - Kommando: "gpg --import {tempKeyFile}"
   - Warte auf Prozess-Ende (waitFor())
   
5. Fingerprint-Extraktion:
   - Kommando: "gpg --with-colons --show-keys {tempKeyFile}"
   - Parse Ausgabe nach "fpr:" Zeile
   - Extrahiere Feld 9 (Fingerprint)
   
6. Trust-Level setzen:
   - Kommando: "gpg --import-ownertrust"
   - Schreibe auf stdin: "{FINGERPRINT}:6:\n"
   - Trust-Level 6 = ULTIMATE (vollständig vertrauenswürdig)
   - Alle von dieser CA signierten Keys werden automatisch vertrauenswürdig
   
7. Cleanup:
   - Lösche temporäre Key-Datei
```

**Sicherheitskonzept:**
```
Trust-Chain:
┌──────────────┐
│   CA-Key     │ Trust: ULTIMATE (6)
│  (pgp-ca)    │
└──────┬───────┘
       │ signiert
       ↓
┌──────────────┐
│  User-Key    │ Trust: FULL (automatisch)
│ (alice.m...) │
└──────────────┘
```

**Hardcoded Sicherheitsparameter:**
```java
private static final String CA_USER = "pgp-ca";
private static final String WKD_ID = "00000000000000000000000000000000";
```
- **CA_USER**: Fest kodiert, verhindert dass beliebige User als CA verwendet werden
- **WKD_ID**: SHA-256 Hash von "pgp-ca" in z-base-32 Kodierung (WKD-Standard)

**Trust-Level Erklärung:**
```
GPG Trust-Levels:
1 = Unknown      (unbekannt)
2 = Never        (niemals vertrauen)
3 = Marginal     (teilweise vertrauenswürdig)
4 = Full         (vollständig vertrauenswürdig)
5 = Ultimate     (absolut vertrauenswürdig, wie eigener Key)
6 = Ultimate     (für Owner-Trust, wie 5)
```

**Fehlerbehandlung:**
- HTTP-Status != 200: Fehlermeldung + Abbruch
- Fingerprint nicht extrahierbar: Warnung + kein Trust gesetzt
- Temporäre Dateien werden auch im Fehlerfall gelöscht (finally-Block)

**Verknüpfungen:**
- Aufgerufen durch: Kommandozeile (Standalone-Tool)
- Ruft auf: 
  - WKD-Server via HTTP (WKDHandler)
  - GPG-Binary via ProcessBuilder
- Beziehung zu WKDServer: Nutzt dieselbe WKD-URL-Struktur
- Beziehung zu RetrieveCreds: RetrieveCreds importiert User-Keys, FetchTrustCA importiert CA-Key

**Verwendungskontext:**
- **Einmalige Ausführung** pro Team/Domain
- **Vor** dem Import von User-Keys (RetrieveCreds)
- **Trust-Bootstrapping**: CA muss vertrauenswürdig sein, damit User-Keys (die von CA signiert wurden) automatisch vertrauenswürdig werden
- **Nicht für jeden User**: Nur für CA-Schlüssel verwenden!

---

## 4. Sicherheitskonzepte

### 4.1 Challenge-Response-Authentifizierung

**Prinzip:**
```
Server → Client: "Beweise, dass du den privaten Schlüssel hast"
Client → Server: "Hier ist die Signatur der Challenge"
Server:          "Signatur gültig → Du bist authentifiziert"
```

**Vorteile:**
- ✅ Kein Passwort über Netzwerk
- ✅ Replay-Angriffe unmöglich (Nonce ist einmalig)
- ✅ Kryptographische Sicherheit (asymmetrische Krypto)

**Ablauf im Detail:**
```
1. Server generiert Nonce (512-Bit Zufallszahl)
2. Client erhält Nonce
3. Client berechnet: Signatur = Encrypt(Hash(Nonce), PrivateKey)
4. Server erhält Signatur
5. Server berechnet: DecryptedHash = Decrypt(Signatur, PublicKey)
6. Server berechnet: ActualHash = Hash(Nonce)
7. Server vergleicht: DecryptedHash == ActualHash ?
8. Bei Übereinstimmung: Authentifizierung erfolgreich
```

---

### 4.2 Session-Management

**Session-ID-Format:**
```
Base64(SHA-256(Random128Bit + Timestamp + UserID + TeamName))
Länge: 44 Zeichen (Base64 von 32 Bytes)
Beispiel: "5f3e9a7b2c1d8e4f0a6b9c2e3d7f8g1h2i3j4k5l6m7n8o9p0=="
```

**Cookie-Attribute:**
```http
Set-Cookie: ITS25-SID=abc123...; Path=/; HttpOnly; SameSite=Strict
```

- **HttpOnly**: JavaScript kann Cookie nicht auslesen (XSS-Schutz)
- **SameSite=Strict**: Cookie nur bei Same-Site Requests (CSRF-Schutz)
- **Path=/**: Cookie gilt für gesamte Domain
- **Secure**: Fehlt (nur für HTTPS, localhost hat kein TLS)

---

### 4.3 Access Control List (ACL)

**Regeln:**

| User | Zugriff auf eigene Datei | Zugriff auf fremde Dateien | Admin-Rechte |
|------|-------------------------|---------------------------|--------------|
| **lars.fischer** | ✅ Ja | ✅ Ja (alle Dateien) | ✅ Ja |
| **alice.mueller** | ✅ Ja | ❌ Nein | ❌ Nein |
| **bob.schmidt** | ✅ Ja | ❌ Nein | ❌ Nein |

**Implementierung:**
```java
boolean isOwner = sessionUser.equals(requestedResource);
boolean isAdmin = "lars.fischer".equals(sessionUser);

if (isOwner || isAdmin) {
    // Zugriff erlaubt
} else {
    // 403 Forbidden
}
```

---

### 4.4 Replay-Schutz

**Problem:** Angreifer könnte alte Signatur wiederverwenden

**Lösung:**
```java
1. Challenge wird pro (Client-IP + Ressource) gespeichert
2. Nach erfolgreicher Verifikation: challengeManager.removeChallenge()
3. Challenge ist verbraucht und ungültig
4. Neue Anfrage benötigt neue Challenge
```

**Identifier-Format:**
```
"192.168.1.100:lars.fischer"
 ↑              ↑
 Client-IP      Ressource
```

---

## 5. Ablaufdiagramme

### 5.1 WKD-Schlüssel-Abruf

```
┌────────┐                                 ┌────────────┐
│ Client │                                 │ WKD-Server │
└───┬────┘                                 └──────┬─────┘
    │                                             │
    │  GET /well-known/openpgpkey/.../hu/...?l=  │
    │ ──────────────────────────────────────────> │
    │                                             │
    │                                     ┌───────▼────────┐
    │                                     │  Parse URL     │
    │                                     │  Extract email │
    │                                     └───────┬────────┘
    │                                             │
    │                                     ┌───────▼────────┐
    │                                     │  GPG Export    │
    │                                     │  --export email│
    │                                     └───────┬────────┘
    │                                             │
    │         200 OK + Binary Key Data            │
    │ <────────────────────────────────────────── │
    │                                             │
    │  GPG Import                                 │
    │ ─────────>                                  │
    │                                             │
```

---

### 5.2 Challenge-Response-Authentifizierung

```
┌────────┐                                          ┌────────────┐
│ Client │                                          │   Server   │
└───┬────┘                                          └──────┬─────┘
    │                                                      │
    │  1. GET /labornutzer/lars.fischer                   │
    │ ──────────────────────────────────────────────────> │
    │                                                      │
    │                                              ┌───────▼────────┐
    │                                              │ No Session?    │
    │                                              │ Generate Nonce │
    │                                              └───────┬────────┘
    │                                                      │
    │  2. 401 + WWW-Authenticate: Challenge={nonce}       │
    │ <────────────────────────────────────────────────── │
    │                                                      │
    │                                                      │
┌───▼─────────────┐                                       │
│ Client signiert │                                       │
│ Nonce mit       │                                       │
│ privatem Key    │                                       │
└───┬─────────────┘                                       │
    │                                                      │
    │  3. GET /labornutzer/lars.fischer                   │
    │     Authorization: GPGSig {signature_base64}        │
    │ ──────────────────────────────────────────────────> │
    │                                                      │
    │                                              ┌───────▼────────┐
    │                                              │ Verify Signatur│
    │                                              │ GPG --verify   │
    │                                              └───────┬────────┘
    │                                                      │
    │                                              ┌───────▼────────┐
    │                                              │ Create Session │
    │                                              │ Remove Nonce   │
    │                                              └───────┬────────┘
    │                                                      │
    │  4. 200 OK + Set-Cookie: ITS25-SID={sid}           │
    │     + Dateiinhalt                                   │
    │ <────────────────────────────────────────────────── │
    │                                                      │
```

---

### 5.3 Session-basierter Zugriff (mit Cookie)

```
┌────────┐                                          ┌────────────┐
│ Client │                                          │   Server   │
└───┬────┘                                          └──────┬─────┘
    │                                                      │
    │  GET /labornutzer/alice.mueller                     │
    │  Cookie: ITS25-SID=abc123...                        │
    │ ──────────────────────────────────────────────────> │
    │                                                      │
    │                                              ┌───────▼────────┐
    │                                              │ Parse Cookie   │
    │                                              │ Get SessionID  │
    │                                              └───────┬────────┘
    │                                                      │
    │                                              ┌───────▼────────┐
    │                                              │ SessionManager │
    │                                              │ getUserFrom... │
    │                                              └───────┬────────┘
    │                                                      │
    │                                              ┌───────▼────────┐
    │                                              │ ACL Check      │
    │                                              │ isOwner?       │
    │                                              │ isAdmin?       │
    │                                              └───────┬────────┘
    │                                                      │
    │  200 OK + Dateiinhalt                               │
    │  (oder 403 Forbidden bei Ablehnung)                 │
    │ <────────────────────────────────────────────────── │
    │                                                      │
```

---

## 6. Deployment

### 6.1 Voraussetzungen

**Software:**
- Java JDK 11 oder höher
- GPG (GNU Privacy Guard) installiert
- Maven (optional, für Build)

**GPG Keyring Setup:**
```bash
# 1. Key generieren (falls noch nicht vorhanden)
gpg --quick-gen-key "Lars Fischer <lars.fischer@hs-bremerhaven.de>" ed25519 sign 2y

# 2. Public Key exportieren
gpg --armor --export lars.fischer@hs-bremerhaven.de > lars_public.asc

# 3. Key von anderem Team importieren
gpg --import team_b_public.asc

# 4. Key vertrauen (wichtig!)
gpg --edit-key team_b_ca@example.org
> trust
> 5 (ultimate trust)
> quit
```

---

### 6.2 Kompilieren und Starten

**Variante 1: Direkt mit javac**
```bash
# Navigiere zum Projekt-Root
cd /home/nspace/Desktop/Projekt/IT-Sicherheit

# Kompiliere alle Java-Dateien
javac -d bin certsid/wkdServer/src/*.java

# Starte Server
java -cp bin certsid.wkdServer.src.WKDServer
```

**Variante 2: Mit Maven**
```bash
# Kompiliere
mvn compile

# Starte Server
mvn exec:java -Dexec.mainClass="certsid.wkdServer.src.WKDServer"
```

**Ausgabe bei Start:**
```
WKD Server läuft auf Port 8000
```

---

### 6.3 Testen

**Test 1: WKD-Endpunkt**
```bash
# Key abrufen
curl "http://localhost:8000/well-known/openpgpkey/hs-bremerhaven.de/hu/00000000000000000000000000000000?l=lars.fischer"

# Sollte binäre Key-Daten zurückgeben (oder 404 wenn Key nicht im Keyring)
```

**Test 2: Challenge-Response**
```bash
# 1. Challenge abholen
curl -v "http://localhost:8000/labornutzer/lars.fischer"

# Ausgabe:
# HTTP/1.1 401 Unauthorized
# WWW-Authenticate: Challenge=SGFsbG8gV2VsdAo=

# 2. Nonce dekodieren und signieren
echo "SGFsbG8gV2VsdAo=" | base64 -d > nonce.txt
gpg --detach-sign --armor --local-user lars.fischer@hs-bremerhaven.de nonce.txt
SIGNATURE=$(base64 -w0 nonce.txt.asc)

# 3. Mit Signatur authentifizieren
curl -v -H "Authorization: GPGSig $SIGNATURE" \
     "http://localhost:8000/labornutzer/lars.fischer"

# Sollte 200 OK + Set-Cookie + Dateiinhalt zurückgeben
```

**Test 3: Session-basierter Zugriff**
```bash
# Mit Cookie aus vorherigem Request
curl -v --cookie "ITS25-SID=abc123..." \
     "http://localhost:8000/labornutzer/alice.mueller"

# Wenn lars.fischer eingeloggt: 200 OK (Admin-Rechte)
# Wenn alice.mueller eingeloggt: 403 Forbidden (nicht Owner von alice.mueller)
```

---

## 7. Bekannte Schwachstellen und Verbesserungen

### 7.1 Kritische Sicherheitslücke: Fehlende Signer-Identitätsprüfung

**Problem:**
```java
// AKTUELL (UNSICHER):
String newSid = sessionManager.createSession(requestedResource);
//                                            ↑
//                                    Kommt aus URL, NICHT aus Signatur!
```

**Angriff:**
```bash
# Angreifer importiert eigenen Key
gpg --import angreifer_key.asc

# Angreifer fragt nach "lars.fischer"
curl http://localhost:8000/labornutzer/lars.fischer
# → 401 + Challenge

# Angreifer signiert mit EIGENEM Key
gpg --sign --local-user angreifer@evil.com nonce.txt

# Server akzeptiert Signatur (wenn Exit-Code 0)
# → Session wird für "lars.fischer" erstellt
# → Angreifer hat Admin-Rechte!
```

**Behebung:**
```java
// Nach erfolgreicher Verifikation:
if (isValid) {
    // Extrahiere Signer-Email aus Signatur
    String signerEmail = gpgService.getSignerEmail(signatureBytes);
    
    // Prüfe ob Signer == erwarteter User
    String expectedEmail = requestedResource + "@hs-bremerhaven.de";
    if (!signerEmail.equals(expectedEmail)) {
        sendResponse(exchange, 403, "Signatur von falschem Schlüssel!", null);
        return;
    }
    
    // Jetzt sicher: Session erstellen
    String newSid = sessionManager.createSession(requestedResource);
}
```

**Empfohlene Implementierung:**
```java
// In GpgService.java ergänzen:
public String getSignerEmail(byte[] signature) {
    // Parse GPG-Ausgabe nach "Good signature from ..."
    // Extrahiere Email zwischen <...>
    // Return Email oder null
}
```

---

### 7.2 Trust-Level wird nicht geprüft

**Problem:**
```bash
# GPG gibt Exit-Code 0 auch bei "unknown" Trust
gpg --verify signature.asc data.txt
# gpg: Good signature from "Angreifer <angreifer@evil.com>" [unknown]
# Exit-Code: 0
```

**Behebung:**
```java
// In GpgService.verifySignature():
// Parse GPG-Ausgabe nach Trust-Level:
if (line.contains("[unknown]") || line.contains("[marginal]")) {
    System.err.println("WARNUNG: Schlüssel ist nicht vertrauenswürdig!");
    // Optional: Abbruch oder separate Warnung
}
```

---

### 7.3 Keine Challenge-Timeouts

**Problem:**
- Challenges verfallen nie
- ChallengeManager speichert unbegrenzt viele Challenges
- Memory Leak bei vielen Requests

**Behebung:**
```java
// In ChallengeManager.java:
private static final Map<String, ChallengeEntry> challengeStore = new ConcurrentHashMap<>();

static class ChallengeEntry {
    byte[] nonce;
    long timestamp;
    
    boolean isExpired() {
        return System.currentTimeMillis() - timestamp > 300_000; // 5 Minuten
    }
}

public byte[] getChallenge(String identifier) {
    ChallengeEntry entry = challengeStore.get(identifier);
    if (entry != null && !entry.isExpired()) {
        return entry.nonce;
    }
    challengeStore.remove(identifier);
    return null;
}
```

---

### 7.4 Keine Session-Timeouts

**Problem:**
- Sessions verfallen nie
- Einmal eingeloggt = für immer eingeloggt

**Behebung:**
```java
// Analog zu ChallengeManager:
static class SessionEntry {
    String userId;
    long lastAccess;
    
    boolean isExpired() {
        return System.currentTimeMillis() - lastAccess > 1800_000; // 30 Minuten
    }
}
```

---

### 7.5 HashMap ist nicht Thread-Safe

**Problem:**
```java
// In SessionManager.java:
private static final Map<String, String> sessionStore = new HashMap<>();
```

**Behebung:**
```java
// Verwende ConcurrentHashMap wie in ChallengeManager:
private static final Map<String, String> sessionStore = new ConcurrentHashMap<>();
```

---

### 7.6 Fehlende Rate-Limiting

**Problem:**
- Angreifer kann beliebig viele Challenge-Anfragen stellen
- Brute-Force-Angriffe möglich

**Behebung:**
```java
// Rate-Limiting pro Client-IP:
private static final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

static class RateLimiter {
    int requests;
    long windowStart;
    
    boolean isAllowed() {
        long now = System.currentTimeMillis();
        if (now - windowStart > 60_000) { // 1 Minute
            windowStart = now;
            requests = 0;
        }
        return ++requests <= 10; // Max 10 Requests/Minute
    }
}
```

---

### 7.7 Logging sensibler Daten

**Problem:**
```java
System.out.println("[SESSION] Neue Session erstellt: " + sessionId);
// Session-ID sollte nicht geloggt werden!
```

**Behebung:**
```java
// Nur User-ID loggen, nicht Session-ID:
System.out.println("[SESSION] Neue Session erstellt für User: " + userId);
```

---

## 8. Weiterführende Themen

### 8.1 HTTPS / TLS

**Aktuell:** HTTP (unverschlüsselt)

**Empfehlung:**
- Verwende `HttpsServer` statt `HttpServer`
- Generiere Self-Signed Certificate für Test
- Für Produktion: Let's Encrypt Zertifikat

```java
HttpsServer server = HttpsServer.create(new InetSocketAddress(8443), 0);
SSLContext sslContext = SSLContext.getInstance("TLS");
sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
server.setHttpsConfigurator(new HttpsConfigurator(sslContext));
```

---

### 8.2 Datenbank-Speicherung

**Aktuell:** In-Memory Maps (bei Server-Neustart verloren)

**Empfehlung:**
- SQLite für persistente Sessions/Challenges
- Oder: Redis für verteilte Systeme

---

### 8.3 Unit-Tests

**Fehlend:** Keine automatisierten Tests

**Empfehlung:**
```java
// JUnit 5 Tests
@Test
void testChallengeGeneration() {
    String nonce = challengeManager.generateChallenge("test-id");
    assertNotNull(nonce);
    assertEquals(88, nonce.length()); // Base64 von 64 Bytes
}

@Test
void testSessionCreation() {
    String sid = sessionManager.createSession("testuser");
    assertEquals("testuser", sessionManager.getUserFromSession(sid));
}
```

---

## 9. Referenzen

- **WKD-Spezifikation:** [draft-koch-openpgp-webkey-service](https://tools.ietf.org/html/draft-koch-openpgp-webkey-service)
- **PGP/OpenPGP:** [RFC 4880](https://tools.ietf.org/html/rfc4880)
- **GPG Manual:** [https://gnupg.org/documentation/manuals/gnupg/](https://gnupg.org/documentation/manuals/gnupg/)
- **Java HttpServer:** [Oracle Docs](https://docs.oracle.com/javase/8/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/package-summary.html)

---

**Ende der Dokumentation**  
**Letzte Aktualisierung:** 9. Dezember 2025
