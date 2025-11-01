# ANLEITUNG: ETP-RSA-Server

Dieses Projekt implementiert einen Server für das "Example Transmission Protocol" (ETP), der RSA-Kryptographie verwendet, um eine geheime Nachricht von einem Client zu empfangen.

##  Komponenten

Das Projekt besteht aus 3 Java-Klassen und 3 Shell-Skripten.

### 1. Java-Klassen (in `src/`)

* **`Krypto.java` (Das Gehirn)**
    * **Zweck:** Diese Klasse ist für die gesamte RSA-Kryptographie verantwortlich.
    * **Funktion:** Beim Start (`new Krypto(512)`) generiert sie ein 512-Bit-RSA-Schlüsselpaar.
    1.  Sie wählt einen festen öffentlichen Exponenten $e = 65537$.
    2.  Sie generiert zwei 256-Bit-Primzahlen $p$ und $q$.
    3.  Sie berechnet den Modul $N = p \cdot q$.
    4.  Sie berechnet $\phi(N) = (p-1)(q-1)$.
    5.  Sie stellt sicher, dass $\text{ggT}(e, \phi(N)) = 1$ ist (sonst werden neue $p, q$ generiert).
    6.  Sie berechnet den privaten Exponenten $d$ als $d \equiv e^{-1} \pmod{\phi(N)}$.
    * Sie stellt Methoden bereit, um die öffentlichen Schlüssel (`getModuleHex`, `getPubkeyHex`) abzurufen und Daten zu entschlüsseln (`decrypt`).

* **`ETPServer.java` (Der Manager)**
    * **Zweck:** Dies ist die Hauptklasse, die das Programm startet und den Server-Socket verwaltet.
    * **Funktion:**
    1.  **Schlüssel:** Erstellt *eine einzige* `Krypto`-Instanz.
    2.  **Server-Start:** Startet einen `ServerSocket` auf einem zufälligen, freien Port (via `new ServerSocket(0)`).
    3.  **Server-Thread:** Startet einen neuen Thread (`serverThread`), der in einer Endlosschleife auf neue Verbindungen wartet (`serverSocket.accept()`).
    4.  **Delegation:** Für jede neue Verbindung startet er einen neuen `ClientHandler`-Thread.
    5.  **Ankündigung:** Ruft `announceToServer()` auf, um dem zentralen Übungsserver (`10.42.1.23:3033`) mitzuteilen, auf welchem Port der ETP-Server lauscht.

* **`ClientHandler.java` (Der Arbeiter)**
    * **Zweck:** Diese Klasse (als Thread) kümmert sich um die gesamte ETP-Protokoll-Kommunikation mit *einem einzigen* Client.
    * **Funktion:** Sie arbeitet das 3-Schritte-Protokoll ab:
    1.  **C -> S (Empfangen):** Wartet auf die Anfrage `GET pubkey ETP/2025`.
        * **Fehlerbehandlung:** Prüft jeden Teil der Anfrage (`GET`, `pubkey`, `ETP/2025`) und sendet spezifische Fehlermeldungen (z.B. `400 Bad Request`, `404 Not Found`), falls die Anfrage abweicht.
    2.  **S -> C (Senden):** Wenn die Anfrage korrekt ist, ruft sie `krypto.getPubkeyHex()` und `krypto.getModuleHex()` auf und sendet die öffentlichen Schlüssel im Format `pub: ...\nN: ...\n` an den Client.
    3.  **C -> S (Empfangen):** Wartet auf den `<ciphertext>` (als Hex-String).
        * **Entschlüsselung:** Konvertiert den Hex-String in ein `BigInteger`.
        * Ruft `krypto.decrypt(...)` auf, um den Klartext-`BigInteger` zu erhalten.
        * Konvertiert das Ergebnis in ein `byte[]`.
        * **Padding-Entfernung:** Entfernt die `0x00`-Padding-Bytes vom Ende.
        * **Ausgabe:** Gibt die finale UTF-8-Nachricht auf `stdout` aus.

### 2. Shell-Skripte (im Root-Ordner)

* **`build.sh` (Der Baumeister)**
    * Kompiliert alle `.java`-Dateien aus `src/` und legt die `.class`-Dateien im `bin/`-Ordner ab.

* **`run.sh` (Der Starter)**
    * Führt die kompilierte Hauptklasse (`ETPServer`) aus. Der Classpath (`-cp bin`) sagt Java, dass es die `.class`-Dateien im `bin/`-Ordner suchen soll.

* **`install.sh` (Der Paketdienst)**
    * Wird für die Abgabe benötigt. Es erstellt ein `tar.gz`-Archiv (z.B. `team12-etp.tar.gz`), das die für die Abgabe erforderliche Ordnerstruktur (`team12/...`) enthält, und kopiert dieses Archiv via `scp` auf den Zielhost (den Übungsserver).

## Gesamtablauf

1.  Du führst `./build.sh` aus. (Code wird kompiliert)
2.  Du führst `./run.sh` aus.
3.  `ETPServer.main()` startet.
4.  `Krypto` generiert die 512-Bit-Schlüssel (dauert kurz).
5.  `ETPServer` startet den `ServerSocket` auf einem freien Port (z.B. `45678`) im Hintergrund (`serverThread`).
6.  `ETPServer` ruft `announceToServer(45678)` auf.
7.  Dein Programm verbindet sich als Client mit `10.42.1.23:3033` und sendet die Nachricht "45678".
8.  Der Übungsserver empfängt "45678" und antwortet (hoffentlich) "ok".
9.  Der Übungsserver (jetzt als Client) verbindet sich mit deinem Server auf Port `45678`.
10. Dein `serverThread` (`serverSocket.accept()`) nimmt die Verbindung an.
11. Ein neuer `ClientHandler`-Thread wird für den Übungsserver gestartet.
12. Der `ClientHandler` empfängt `GET pubkey ETP/2025`.
13. Der `ClientHandler` antwortet mit `pub: ...\nN: ...\n`.
14. Der `ClientHandler` empfängt den `<ciphertext>`.
15. Der `ClientHandler` entschlüsselt ihn, gibt die geheime Nachricht auf `stdout` aus und beendet die Verbindung.