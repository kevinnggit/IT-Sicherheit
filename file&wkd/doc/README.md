# IT-Sicherheit Projekt - Team 12
**Secure File Server & Web Key Directory (WKD)**

Dieses Projekt implementiert einen sicheren Dateiserver und einen OpenPGP Web Key Directory Service. Es wurde im Rahmen des Moduls
"IT-Sicherheit" entwickelt und demonstriert sichere Softwarearchitektur, GPG-basierte Authentifizierung und DSGVO-konforme Datenverarbeitung.

## Features

* **Modularer Aufbau:** Klare Trennung zwischen HTTP-Handlern und Business-Logik.
* **Port-Trennung:**
    * **Port 80 (Fileserver):** Registrierung, Login und Dateizugriff.
    * **Port 8080 (WKD):** Verteilung öffentlicher Schlüssel (`/well-known/openpgpkey`).
* **Sichere Authentifizierung:** Challenge-Response-Verfahren mittels GPG-Signaturen.
* **Automatisierte Registrierung:** Self-Service Portal mit automatischem Import und Signierung von User-Keys ("Trust on First Use").
* **DSGVO-Konformität:** Transparenz-Hinweise, Datenminimierung und Session-Management.

## Projektstruktur & Module

Der Quellcode wurde wie folgt refaktoriert:

team12/
├── src/
│   ├── MainServer.java           # Entry Point: Startet die HTTP-Server auf Port 80 & 8080
│   │
│   ├── handlers/                 # [Controller-Layer] Verarbeitung von HTTP-Requests
│   │   ├── FileHandler.java      # Schützt Dateizugriffe (Checkt Cookies & Auth)
│   │   ├── RegistrationHandler.java # Verarbeitet Formulardaten (/cgi/new)
│   │   ├── StaticFileHandler.java   # Liefert statische Assets (register.html)
│   │   └── WKDHandler.java       # Liefert Public Keys für WKD-Anfragen
│   │
│   └── services/                 # [Service-Layer] Geschäftslogik & Kryptographie
│       ├── GpgService.java       # Wrapper für GPG-Shell-Befehle (Import/Sign/Fingerprint)
│       ├── SessionManager.java   # Verwaltet Session-IDs (UUID) und User-Mapping
│       └── ChallengeManager.java # Erstellt und verwaltet kryptografische Nonces
│
├── doc/
│   ├── register.html             # Das Frontend-Formular für die Registrierung
│   └── ...                       # Dokumentationen (README.md, Labortagebuch.txt)
│
├── bin/                          # Kompilierte .class Dateien (durch build.sh erzeugt)
├── build.sh                      # Skript zum Kompilieren
├── install.sh                    # Skript zur Einrichtung der Umgebung
└── run.sh                        # Skript zum Starten des Servers (benötigt sudo)

## Installation & Start

Linux-Umgebung (z.B. Debian/Ubuntu)
Java JDK (Version 11 oder höher)
GnuPG (gpg) installiert

* Umgebung prüfen & installieren

Führt das Install-Skript aus, um sicherzustellen, dass die Verzeichnisstruktur korrekt ist und benötigte Dateien an Ort und Stelle liegen.
./install.sh

* Kompilieren

Kompiliert den gesamten Java-Quellcode aus src/ in den Ordner bin/.
./build.sh

* Server starten

Startet den Server. Da Port 80 verwendet wird, ist sudo erforderlich. Das Skript übernimmt den Aufruf.
./run.sh

* Endpunkte

Fileserver (Port 80)
Methode,Pfad,Beschreibung
GET,/,Zeigt das Registrierungsformular (register.html).
POST,/cgi/new,"Verarbeitet Registrierungsdaten (User, Mail, PubKey)."
GET,/labornutzer/<user>,Zugriff auf die persönliche Datei. Erfordert Auth/Cookie.

WKD-Server (Port 8080)
Methode,Pfad,Beschreibung
GET,/well-known/openpgpkey/...,Liefert den binären Public Key für E-Mail-Clients.

Sicherheitshinweise
GPG-Integration: Der Server nutzt einen eigenen Keyring. Der Private Key der CA muss für den Root-User verfügbar sein, damit automatisierte Signaturen funktionieren.
Session-Management: Sessions werden als HttpOnly Cookies (simuliert) verwaltet und serverseitig gegen User-Spoofing und Privilege Escalation abgesichert.


# Detaillierter Erfüllungsnachweis der Anforderungen (Team 12)

Dieses Dokument schlüsselt detailliert auf, wie jede einzelne Anforderung aus **Aufgabe 05** technisch und konzeptionell umgesetzt wurde.

---
### Anforderungen erklärt
## 1. HTML-Registrierung

> **Text:** "Eine HTML-Seite zur Registrierung als 'register.html' erreichbar. Die CGI-Action unter 'cgi/new' erreichbar."

### Umsetzung
* **Wo:** * Frontend: `doc/register.html`
    * Backend: `src/handlers/StaticFileHandler.java` (für die Seite) und `src/handlers/RegistrationHandler.java` (für die Action).
* **Wie:**
    * Der `StaticFileHandler` ist auf dem Root-Pfad `/` registriert und liefert den Inhalt von `doc/register.html` aus.
    * Das HTML-Formular nutzt das Attribut `<form action="/cgi/new" method="POST">`.
    * Der `RegistrationHandler` ist in `MainServer.java` exakt auf den Pfad `/cgi/new` gebunden. Er liest die POST-Parameter (User, Email, PubKey).
* **Warum:** * Dies trennt die Darstellung (HTML) von der Logik (Java).

---

## 2. Session-ID Mechanismus

> **Text:** "Nutzt auch hier euren etablierten Mechanismus für Session-ID in den Cookies."

### Umsetzung
* **Wo:** * `src/services/SessionManager.java`
    * `src/handlers/FileHandler.java`
* **Wie:**
    * Die Registrierung ist der *Vorbereitungsschritt*. Sie hinterlegt den Public Key.
    * Der eigentliche Zugriff nutzt den etablierten Challenge-Response-Mechanismus:
        1.  Zugriff auf `/labornutzer/user` -> Server sendet Challenge (Nonce).
        2.  Client signiert Nonce mit Private Key.
        3.  Server prüft Signatur. Bei Erfolg generiert der `SessionManager` eine UUID.
        4.  Diese UUID wird als Cookie `ITS25-SID` gesetzt.
* **Warum:** * Sicherheit ("Verify then Trust"). Wir setzen kein Session-Cookie direkt nach der Registrierung, da jeder (auch ein Angreifer) das Formular ausfüllen könnte. Nur wer den Private Key besitzt, erhält eine Session.

---

## 3. Zugriffsschutz

> **Text:** "Stellt sicher, dass Angreifer weder über eure Session-Cookies, noch über das Registrierungsverfahren in der Lage sind auf die Dateien anderer Nutzer zuzugreifen."

### Teil A: Schutz bei Cookies
* **Wo:** `src/handlers/FileHandler.java` (Methode `handle`)
* **Wie:**
    * **Identity Binding:** Der Handler extrahiert den User aus dem Cookie (via `SessionManager`) UND den angeforderten User aus der URL.
    * **Check:** `if (!sessionUser.equals(requestUser)) { return 403; }`.
* **Warum:** * Verhindert, dass User A (mit gültigem Cookie) die Datei von User B liest. Ein Cookie ist kein Generalschlüssel, sondern personengebunden.

### Teil B: Schutz bei Registrierung (Account Takeover)
* **Wo:** `src/handlers/RegistrationHandler.java`
* **Wie:**
    * **No-Overwrite Policy:** `if (Files.exists(path)) { return 409 Conflict; }`.
    * **Input Validierung:** Der Benutzername wird gegen eine Whitelist (`[a-zA-Z0-9._-]+`) geprüft.
* **Warum:** * Verhindert, dass ein Angreifer eine existierende Datei überschreibt oder Pfade manipuliert (`../../etc/passwd`).

---

## 4. Port-Anpassung

> **Text:** "WKD-Server auf Port 8080, Fileserver auf Port 80."

### Umsetzung
* **Wo:** `src/MainServer.java`
* **Wie:**
    * Instanziierung von zwei `HttpServer`-Objekten:
        1.  `HttpServer.create(new InetSocketAddress(80), 0)` -> Bindet Handler für `/` und `/cgi/new`.
        2.  `HttpServer.create(new InetSocketAddress(8080), 0)` -> Bindet Handler für `/well-known/openpgpkey`.
* **Warum:** * **Separation of Concerns:** Trennung von Infrastruktur (Key-Distribution) und Applikation (Dateispeicher).

---

## 5. Modulare Struktur

> **Text:** "Restrukturiert euren Source-Code so, dass unterschiedliche Funktionalitäten in Modulen organisiert sind [...] Dokumentiert dies in einer README.md."

### Umsetzung
* **Wo:** Verzeichnis `src/`
* **Wie:**
    * **Handlers (`src/handlers/`):** Enthält nur HTTP-Logik (Request parsen, Response senden).
    * **Services (`src/services/`):** Enthält nur Geschäftslogik (GPG Befehle, Session-Verwaltung).
    * **Main (`src/MainServer.java`):** Enthält nur Startup-Logik (Wiring).
* **Warum:** * Vermeidung von "Spaghetti-Code" (Monolith). Bessere Wartbarkeit und Austauschbarkeit von Komponenten (z.B. könnte man den `GpgService` austauschen, ohne den HTTP-Code anzufassen).

---

## 6. Skripte

> **Text:** "Erstellt kurze Skripte für das Kompilieren, Installieren und Starten."

### Umsetzung
* **Wo:** Hauptverzeichnis `team12/`
* **Wie:**
    * `install.sh`: Legt Verzeichnisse (`bin`, `doc`) an und prüft Voraussetzungen.
    * `build.sh`: Führt `javac -d bin -sourcepath src ...` aus.
    * `run.sh`: Führt `java -cp bin MainServer` aus.
* **Warum:** * Reproduzierbarkeit (Infrastructure as Code im Kleinen).

---

## 7. DSGVO Umsetzung & Rechte

> **Text:** "Haltet euch an die Grundprinzipien der DSGVO [...] Setzt die Rechte der Nutzer um und dokumentiert dies."

### Umsetzung
* **Wo:** `doc/register.html` (Anzeige) und `doc/explain.md` (Dokumentation).
* **Wie:**
    * **Transparenz:** Info-Box auf der Startseite nennt Zweck (Speicher) und Rechtsgrundlage (Einwilligung).
    * **Datenminimierung:** Es werden nur User, Mail und Key gespeichert (keine unnötigen Logs).
    * **Rechte:**
        * *Auskunft:* Durch Login einsehbar.
        * *Löschung:* Kontaktweg (E-Mail an Admin) ist im HTML angegeben.
* **Warum:** * Erfüllung der gesetzlichen Vorgaben (Art. 5, Art. 12-23 DSGVO).

---
