# 1. Das Herzstück: Die RSA-Kryptographie

Bevor wir auch nur an Sockets denken, brauchen wir die Mathematik. Wir erstellen die Logik, um Schlüssel zu generieren und Nachrichten zu ver- und entschlüsseln. 
5-Punkte-Kernanforderung.

## 1.1. Schlüsselerzeugung (512-Bit)
- Wir brauchen zwei 256-Bit Primzahlen, `p` und `q`. Wir nutzen Javas `BigInteger.probablePrime()`.
- Wir berechnen den Modul `N = p * q`. (Das ist unser 512-Bit-Modul).
- Wir berechnen `ϕ(N) = (p-1) * (q-1)`.
- Wir wählen einen öffentlichen Exponenten `e` (genannt `pubkey` in der Aufgabe). Wir nehmen den Standardwert `65537` (Hex: `0x10001`), da er "sicher" ist (er ist prim und klein, was die Verschlüsselung schnell macht). Wir müssen prüfen, ob `ggT(e, ϕ(N)) = 1` ist.
- Wir berechnen den privaten Exponenten `d` als `d ≡ e^{-1} (mod ϕ(N))`. Wir nutzen `e.modInverse(phi)`.

## 1.2. Krypto-Operationen (Ver- und Entschlüsselung)
- Verschlüsselung: `C ≡ M^e (mod N)`. Das ist `M.modPow(e, N)`.
- Entschlüsselung: `M ≡ C^d (mod N)`. Das ist `C.modPow(d, N)`.

## 1.3. Bonus: Eigene Krypto-Funktionen (3 Punkte)
- Wir implementieren `customModPow(base, exponent, modulus)` für den 1-Punkt-Bonus.
- Wir implementieren `customGcd(a, b)` und `customModInverse(a, m)` (basierend auf dem Erweiterten Euklidischen Algorithmus) für den 2-Punkte-Bonus.

# 2. Der ETP-Server (Protokoll & Netzwerk)

Jetzt bauen wir den Server, der auf Anfragen wartet und das ETP-Protokoll spricht. Das sind die 3 Punkte für ETP + 1 Punkt für Fehlerbehandlung.

## 2.1. Server-Socket
- Wir erstellen einen `ServerSocket`, der auf einem bestimmten Port lauscht.
- Wir warten in einer Schleife auf eingehende Verbindungen (`serverSocket.accept()`).
- Jede Verbindung wird in einem eigenen Thread (`ClientHandler`) behandelt, damit der Server mehrere Anfragen parallel annehmen kann (wichtig für Robustheit).

## 2.2. ETP-Protokollablauf (im ClientHandler)
- Startup: Der Server generiert einmalig beim Start sein 512-Bit RSA-Schlüsselpaar (aus Schritt 1).
- Warten auf C1: Lies die erste Zeile vom Client.
- Prüfen C1: Ist die Zeile exakt `GET pubkey ETP/2025`?

Fehlerbehandlung (1 Punkt):
- Wenn nein: Sende einen spezifischen Fehler (z.B. `400 Bad Request: Invalid Command`) und schließe die Verbindung.
- Niemals den privaten Schlüssel senden!

Senden S2: Wenn C1 korrekt war, sende die Antwort.
- Formatiere `e` und `N` als Hexadezimal-Strings (Big-Endian).
- Sende:
    ```
    pub: <e_hex>
    N: <N_hex>
    ```
    an den Client.

Warten auf C3: Lies die nächste Zeile vom Client. Das ist der `<ciphertext>` (als Hex-String).

Entschlüsseln:
- Konvertiere den Hex-String in ein `BigInteger`.
- Entschlüssele es mit unserem privaten Schlüssel `d` (aus Schritt 1.2).
- Konvertiere das Ergebnis (wieder ein `BigInteger`) in ein `byte[]`.
- Padding entfernen: Die Nachricht ist mit Null-Bytes (`\0`) aufgefüllt. Wir müssen diese entfernen, um den reinen UTF-8-Text zu erhalten.
- Ausgabe: Gib den entschlüsselten UTF-8-Text auf stdout aus (`System.out.println(...)`).
- Schließe die Verbindung.

# 3. Der Ankündigungs-Client (Start & Verbindung)

Dieser Teil ist der "Starter" des Programms. Er meldet unseren Server beim Übungsserver an.

## 3.1. ETP-Server starten
- Wir müssen zuerst unseren ETP-Server (aus Schritt 2) auf einem freien Port starten.
- Trick: `new ServerSocket(0)` bindet an einen zufälligen freien Port. Wir holen uns die Portnummer mit `getLocalPort()`.
- Wir starten den ETP-Server in einem neuen Thread, damit das Hauptprogramm weitermachen kann.

## 3.2. Ankündigung senden
- Das Hauptprogramm verbindet sich jetzt als Client mit dem Übungsserver: `10.42.1.23:3033`.
- Wir senden die Portnummer unseres ETP-Servers (die wir in 3.1 bekommen haben) als ASCII-String.
- Wir warten auf die Antwort `Ok`.
- Wenn `Ok` kommt, ist alles gut. Das Hauptprogramm wartet dann einfach, bis der Server-Thread (aus 3.1) beendet wird. Wenn nicht `Ok` kommt, geben wir einen Fehler aus.

# 4. Packaging (Die Abgabe)

Zum Schluss erstellen wir die geforderten Shell-Skripte für eine saubere Abgabe.

- `src/`: Hier kommt unser ganzer Java-Code rein.
- `build.sh`: Ein einfaches Skript, das `javac` aufruft, um alles zu kompilieren (z.B. `mkdir -p bin && javac -d bin src/*.java`).
- `run.sh`: Ein Skript, das das kompilierte Programm startet (z.B. `java -cp bin ETPServer`).
- `install.sh`: Ein Skript, das (wie in der Aufgabe beschrieben) den Code auf einen Zielhost kopiert (wahrscheinlich via `scp`).


IT-Sicherheit/
├── install.sh       (Kopiert das Projekt auf den Zielhost)
├── build.sh         (Kompiliert den Java-Code von src/ nach bin/)
├── run.sh           (Startet den ETPServer)
├── bin/             (Wird von build.sh erstellt, enthält .class Dateien)
└── src/
    ├── ETPServer.java     (Hauptklasse: Startet Server, meldet Port an)
    ├── ClientHandler.java (Bearbeitet eine ETP-Client-Anfrage)
    └── Krypto.java        (RSA-Logik: Schlüsselerzeugung, modPow, etc.)