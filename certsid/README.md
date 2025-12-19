# CERTSID - Certificate & Identity Management

## Übersicht

Dieses Projekt implementiert einen **Web Key Directory (WKD) Server** mit **Challenge-Response-Authentifizierung** basierend auf PGP-Signaturen.

### Hauptfunktionen

✅ **WKD-Server** - RFC-konformer Server zur Auslieferung öffentlicher PGP-Schlüssel  
✅ **Challenge-Response-Auth** - Sichere Authentifizierung mit PGP-Signaturen (kein Passwort über Netzwerk)  
✅ **Session-Management** - Cookie-basierte Sessions mit kryptographisch sicheren IDs  
✅ **Access Control List (ACL)** - Owner-basiert und Admin-basiert  
✅ **Trust-Bootstrapping** - CA-Key Download und Trust-Level setzen

---

## Schnellstart

### Voraussetzungen

- **Java JDK 11+**
- **GPG (GNU Privacy Guard)** installiert
- GPG-Keyring mit PGP-Schlüsseln

### Server starten

```bash
# Kompilieren
javac -d bin certsid/wkdServer/src/*.java

# Starten
java -cp bin certsid.wkdServer.src.WKDServer
```

**Ausgabe:**
```
WKD Server läuft auf Port 8000
```

---

## Dateien-Übersicht

### Server-Komponenten (laufen im WKD-Server)

| Datei | Beschreibung |
|-------|--------------|
| `WKDServer.java` | Hauptserver mit HTTP-Endpunkten (WKD + Protected) |
| `GpgService.java` | GPG-Kommandozeilen-Wrapper (Key-Export, Signatur-Verifikation) |
| `ChallengeManager.java` | Challenge-Nonce-Verwaltung (512-Bit SecureRandom) |
| `SessionManager.java` | Session-Verwaltung (SHA-256 Hash-basiert) |

### Client-Tools (Standalone-Programme)

| Datei | Beschreibung |
|-------|--------------|
| `RetrieveCreds.java` | User-Key Download & Import via WKD |
| `FetchTrustCA.java` | CA Trust-Bootstrapping (Trust-Level auf ULTIMATE setzen) |

---

## Verwendung

### 1. WKD-Endpunkt (Schlüssel abrufen)

```bash
curl "http://localhost:8000/well-known/openpgpkey/hs-bremerhaven.de/hu/0000...?l=lars.fischer"
```

**Gibt zurück:** Binäre PGP-Key-Daten oder 404 (Key nicht gefunden)

---

### 2. Challenge-Response-Authentifizierung

**Schritt 1: Challenge abholen**
```bash
curl -v "http://localhost:8000/labornutzer/lars.fischer"
```

**Antwort:**
```
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Challenge=SGFsbG8gV2VsdAo=
```

**Schritt 2: Nonce signieren**
```bash
# Nonce dekodieren
echo "SGFsbG8gV2VsdAo=" | base64 -d > nonce.txt

# Mit privatem Schlüssel signieren
gpg --detach-sign --armor --local-user lars.fischer@hs-bremerhaven.de nonce.txt

# Signatur Base64-kodieren
SIGNATURE=$(base64 -w0 nonce.txt.asc)
```

**Schritt 3: Mit Signatur authentifizieren**
```bash
curl -v -H "Authorization: GPGSig $SIGNATURE" \
     "http://localhost:8000/labornutzer/lars.fischer"
```

**Antwort:**
```
HTTP/1.1 200 OK
Set-Cookie: ITS25-SID=abc123...; Path=/; HttpOnly; SameSite=Strict

[Dateiinhalt]
```

---

### 3. Session-basierter Zugriff (mit Cookie)

```bash
# Mit Cookie aus vorherigem Request
curl -v --cookie "ITS25-SID=abc123..." \
     "http://localhost:8000/labornutzer/alice.mueller"
```

**ACL-Regeln:**
- **Owner**: User kann nur eigene Datei abrufen (`/labornutzer/alice.mueller` nur von `alice.mueller`)
- **Admin**: User `lars.fischer` kann alle Dateien abrufen

---

### 4. CA Trust-Bootstrapping

```bash
# CA-Key herunterladen und Trust setzen
java FetchTrustCA 10.42.1.50 8000 team-12.example.org
```

**Was passiert:**
1. Lädt CA-Key (`pgp-ca@team-12.example.org`) via WKD
2. Importiert in lokales GPG-Keyring
3. Extrahiert Fingerprint
4. Setzt Trust-Level auf **ULTIMATE (6)**

**Trust-Chain:**
```
CA-Key (pgp-ca)
  ↓ signiert
User-Key (alice.mueller) → automatisch vertrauenswürdig
```

---

### 5. User-Key Download & Import

```bash
# User-Key herunterladen
java RetrieveCreds 10.42.1.50 8000 hs-bremerhaven.de alice.mueller
```

**Was passiert:**
1. Lädt Public Key via WKD
2. Importiert in lokales GPG-Keyring
3. Prüft Trust-Level
4. Fügt User zur Allowlist hinzu
5. Erstellt Benutzerdatei

---

## Sicherheitsaspekte

### ✅ Vorteile

- **Kein Passwort über Netzwerk**: Challenge-Response mit PGP-Signaturen
- **Replay-Schutz**: Nonce ist einmalig und wird nach Verifikation gelöscht
- **Kryptographische Session-IDs**: SHA-256(Random + Timestamp + UserID + Team)
- **HttpOnly Cookies**: JavaScript kann Cookie nicht auslesen (XSS-Schutz)
- **SameSite=Strict**: Cookie nur bei Same-Site Requests (CSRF-Schutz)

### ⚠️ Bekannte Schwachstellen

1. **Fehlende Signer-Identitätsprüfung**: Session wird für URL-Parameter erstellt, nicht für Signer
2. **Trust-Level wird nicht geprüft**: GPG Exit-Code 0 auch bei "unknown" Trust
3. **Keine Challenge-Timeouts**: Challenges verfallen nie (Memory Leak)
4. **Keine Session-Timeouts**: Sessions verfallen nie
5. **HashMap nicht Thread-Safe**: SessionManager sollte ConcurrentHashMap verwenden
6. **Fehlende Rate-Limiting**: Brute-Force-Angriffe möglich

**Siehe:** [DOKUMENTATION.md - Abschnitt 7](DOKUMENTATION.md#7-bekannte-schwachstellen-und-verbesserungen) für Details und Lösungen

---

## Dokumentation

📄 **[DOKUMENTATION.md](DOKUMENTATION.md)** - Vollständige technische Dokumentation mit:
- Detaillierte Architektur-Diagramme
- Vollständige Methodendokumentation
- Ablaufdiagramme
- Sicherheitskonzepte
- Deployment-Anleitung
- Bekannte Schwachstellen und Lösungen

📄 **[ABLAUF.md](ABLAUF.md)** - Ablauf-Beschreibung des Projekts

---

## Technologie-Stack

| Komponente | Technologie |
|------------|-------------|
| **Sprache** | Java 11+ |
| **HTTP-Server** | `com.sun.net.httpserver.HttpServer` (JDK-integriert) |
| **Kryptographie** | GPG (GNU Privacy Guard) |
| **Datenformat** | PGP/OpenPGP (RFC 4880) |
| **WKD-Protokoll** | RFC draft-koch-openpgp-webkey-service |
| **Hash-Funktion** | SHA-256 (Session-IDs) |
| **RNG** | SecureRandom (Challenge-Nonces) |

---

## Architektur-Übersicht

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
│  │                      • remove()        • invalidate │   │
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

---

## Lizenz & Team

**Projekt:** IT-Sicherheit - Certificate & Identity Management  
**Team:** Team 12  
**Hochschule:** HS Bremerhaven  
**Datum:** 9. Dezember 2025

---

## Referenzen

- **WKD-Spezifikation**: [draft-koch-openpgp-webkey-service](https://tools.ietf.org/html/draft-koch-openpgp-webkey-service)
- **PGP/OpenPGP**: [RFC 4880](https://tools.ietf.org/html/rfc4880)
- **GPG Manual**: [https://gnupg.org/documentation/manuals/gnupg/](https://gnupg.org/documentation/manuals/gnupg/)
- **Java HttpServer**: [Oracle Docs](https://docs.oracle.com/javase/8/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/package-summary.html)
