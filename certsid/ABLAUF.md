# IT-Sicherheit Aufgabe 03: Authentifizierter Datendienst

## Projektziel
Das Ziel ist es, den bestehenden WKD-Server aus Aufgabe 02 zu einem sicheren, authentifizierten Dienst auszubauen. Statt Passwörtern nutzen wir eine rein kryptographische Authentifizierung mittels OpenPGP-Signaturen (Challenge-Response) und verwalten Sitzungen über sichere Cookies.

---

## Modul 1: Identifikatoren (Der "Schlüssel-Sammler")
Um Nutzer authentifizieren zu können, müssen wir ihre öffentlichen Schlüssel kennen und ihnen vertrauen.

**Aufgabe:**
Entwicklung eines Skripts/Moduls `retrieve_creds(srv, id)`, das folgende Schritte automatisiert:
1.  **Abfrage:** Es verbindet sich zum WKD-Server eines anderen Teams (`srv`) und fragt nach dem Public Key eines Nutzers (`id`).
2.  **Trust-Check:** Es prüft, ob der empfangene Schlüssel von einer vertrauenswürdigen Instanz (CA) signiert wurde.
    * *Voraussetzung:* Wir müssen den CA-Schlüssel des anderen Teams vorher importiert und das Trust-Level angepasst haben.
3.  **Import:** Nur vertrauenswürdige Schlüssel werden in unseren lokalen GPG-Keyring importiert.
4.  **Registrierung:** Die ID des Nutzers wird in einer lokalen Liste ("Allowlist") gespeichert.

---

## Modul 2: Session Management (HTTP & Cookies)
Der Server muss echtes HTTP/1.1 sprechen und Sitzungen verwalten.

**Aufgabe:**
1.  **HTTP-Konformität:** Der Server muss korrekte HTTP-Statuscodes (200, 401, 403) und Header senden.
2.  **Session-ID:**
    * Name des Cookies: `ITS25-SID`
    * Wert: Ein kryptographischer Hash (Base64 kodiert) aus `Zufallszahl + Datum + UserID + TeamName`.
    * *Sicherheit:* Die ID darf nicht vorhersagbar sein (Session Hijacking Schutz).
3.  **Cookie-Handling:**
    * Setzen des Cookies nach erfolgreichem Login.
    * Prüfen des Cookies bei jedem Zugriff auf geschützte Bereiche.
    * Entscheidung für "Stateless" (alles im Cookie signiert) oder "Stateful" (ID in Datenbank/Map) Session-Verwaltung.

---

## Modul 3: Authentifizierung (Challenge-Response)
Wir ersetzen den klassischen Passwort-Login durch ein kryptographisches Verfahren.

**Ablauf (Ressource: `/labornutzer/<userid>`):**
1.  **Request:** Nutzer fragt geschützte Ressource an.
2.  **Challenge (401):** Server lehnt ab und sendet Header `WWW-Authenticate` mit einer `Challenge=<Nonce>` (512 Bit Zufall).
3.  **Signatur:** Der Client signiert diese Nonce mit seinem privaten PGP-Schlüssel.
4.  **Response:** Client sendet die Signatur im Header `Authorization: GPGSig <Signatur>`.
5.  **Verifikation:** Server prüft die Signatur mit dem gespeicherten Public Key (aus Modul 1).
    * **Erfolg:** Server setzt Session-Cookie und erlaubt Zugriff.
    * **Fehler:** Server sendet 403 Forbidden.

---

## Modul 4: Testfall (Zugriffskontrolle / ACL)
Wir implementieren eine minimalistische Zugriffskontrolle (Access Control List).

**Die Ressource:**
Eine Datei für jeden registrierten Nutzer (Inhalt: Teamname + Datum).

**Die Regeln:**
1.  **Owner-Zugriff:** Der Nutzer `<userid>` darf nur seine *eigene* Datei lesen.
2.  **Admin-Zugriff:** Der Nutzer `lars.fischer` (Kursleitung) darf *alle* Dateien lesen.
3.  **Deny-All:** Jeder andere Zugriff wird verweigert.