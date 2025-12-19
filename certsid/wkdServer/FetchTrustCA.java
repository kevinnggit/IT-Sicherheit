package certsid.wkdServer;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

/**
 * ============================================================================================
 * KLASSE: FetchTrustCA
 * ============================================================================================
 * 
 * ZWECK:
 * Diese Klasse implementiert das Trust-Bootstrapping für eine Certificate Authority (CA).
 * Sie lädt den öffentlichen PGP-Schlüssel der CA über das WKD-Protokoll herunter, importiert
 * ihn in den lokalen GPG-Keyring und setzt den Trust-Level auf "ULTIMATE" (6).
 * 
 * FUNKTIONALITÄTEN:
 * 1. Lädt CA-Public-Key über WKD-URL herunter
 * 2. Importiert Schlüssel in lokales GPG-Keyring
 * 3. Extrahiert Fingerprint aus dem importierten Schlüssel
 * 4. Setzt Trust-Level auf ULTIMATE (6) via gpg --import-ownertrust
 * 
 * VERKNÜPFUNGEN:
 * - Nutzt WKD-Protokoll-URL-Schema (siehe WKDServer.java)
 * - Ruft GPG-CLI via ProcessBuilder auf
 * - Arbeitet mit festem CA-User "pgp-ca" und Domain-Parameter
 * 
 * WER RUFT AUF:
 * - Wird manuell als Standalone-Tool via Kommandozeile ausgeführt
 * - Vorbereitung für Trust-Chains: CA muss vertrauenswürdig sein, damit
 *   von ihr signierte User-Schlüssel automatisch vertrauenswürdig werden
 * 
 * VERWENDUNG:
 * java FetchTrustCA <IP> <Port> <Domain>
 * Beispiel: java FetchTrustCA 10.42.1.50 8000 team-12.example.org
 * 
 * SICHERHEITSHINWEIS:
 * Der Trust-Level ULTIMATE (6) bedeutet, dass dieser Schlüssel als vollständig vertrauenswürdig
 * gilt und alle von ihm signierten Schlüssel ebenfalls als vertrauenswürdig eingestuft werden.
 * Nur bei CA-Schlüsseln verwenden!
 * ============================================================================================
 */
public class FetchTrustCA {

    // FESTGELEGTER CA-USER: Nur dieser User darf als CA fungieren
    // Dieser Username ist fest kodiert aus Sicherheitsgründen - verhindert,
    // dass beliebige User als CA verwendet werden können
    private static final String CA_USER = "pgp-ca";
    
    // WKD-HASH für CA-User: SHA-256 Hash von "pgp-ca" in z-base-32 Kodierung
    // Dieser Hash wird in der WKD-URL verwendet, um den Schlüssel zu identifizieren
    // Format: /hu/<HASH>?l=<localpart>
    private static final String WKD_ID = "00000000000000000000000000000000";

    /**
     * MAIN-METHODE: Einstiegspunkt für Trust-Bootstrapping
     * 
     * PARAMETER:
     * - args[0]: IP-Adresse des WKD-Servers (z.B. "10.42.1.50")
     * - args[1]: Port des WKD-Servers (z.B. "8000")
     * - args[2]: Domain der CA (z.B. "team-12.example.org")
     * 
     * ABLAUF:
     * 1. Validierung der Kommandozeilenargumente
     * 2. URL-Konstruktion nach WKD-RFC-Standard
     * 3. HTTP-Download des CA-Public-Keys
     * 4. Import in lokales GPG-Keyring
     * 5. Fingerprint-Extraktion
     * 6. Trust-Level auf ULTIMATE setzen
     * 
     * FEHLERBEHANDLUNG:
     * - Wirft Exception bei Netzwerkproblemen oder GPG-Fehlern
     * - Gibt Fehlermeldungen auf stderr aus
     * - Löscht temporäre Dateien im Fehlerfall
     */
    public static void main(String[] args) throws Exception {
        // ARGUMENT-VALIDIERUNG: Prüfe ob genau 3 Parameter übergeben wurden
        if (args.length != 3) {
            // Gebe Hilfetext aus bei falscher Anzahl an Parametern
            System.out.println("Nutzung: java FetchTrustCA <IP> <Port> <Domain>");
            System.out.println("Beispiel: java FetchTrustCA 10.42.1.50 8000 team-12.example.org");
            return; // Beende Programm ohne Fehlercode
        }

        // PARAMETER-EXTRAKTION: Hole IP, Port und Domain aus den Argumenten
        String ip = args[0];        // IP-Adresse des WKD-Servers
        String port = args[1];      // Port-Nummer des WKD-Servers
        String domain = args[2];    // Domain für die CA (z.B. team-12.example.org)

        // AUSGABE: Informiere User über den Start des Bootstrapping-Prozesses
        System.out.println("--- Trust-Bootstrapping für Domain: " + domain + " ---");

        // SCHRITT 1: URL-KONSTRUKTION nach WKD-RFC-Standard
        // Format: http://<server>/well-known/openpgpkey/<domain>/hu/<hash>?l=<localpart>
        // - <server>: IP:Port des WKD-Servers
        // - <domain>: Domain der CA
        // - <hash>: WKD_ID (SHA-256 Hash des Usernamens in z-base-32)
        // - <localpart>: CA_USER (hier "pgp-ca")
        String urlStr = String.format("http://%s:%s/well-known/openpgpkey/%s/hu/%s?l=%s",
                ip, port, domain, WKD_ID, CA_USER);
        
        // AUSGABE: Zeige die konstruierte URL für Debugging-Zwecke
        System.out.println("Lade CA-Key von: " + urlStr);

        // SCHRITT 2: DOWNLOAD des CA-Public-Keys
        
        // Erstelle temporäre Datei zum Speichern des heruntergeladenen Schlüssels
        // Präfix: "ca-key-", Suffix: ".asc" (ASCII-armored PGP key)
        // Die Datei wird im System-Temp-Verzeichnis erstellt
        Path tempKeyFile = Files.createTempFile("ca-key-", ".asc");
        
        // Konvertiere URL-String zu URL-Objekt via URI (für moderne Java-Versionen)
        URL url = URI.create(urlStr).toURL();
        
        // Öffne HTTP-Verbindung zur WKD-URL
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        // FEHLERBEHANDLUNG: Prüfe HTTP-Statuscode
        if (conn.getResponseCode() != 200) {
            // Bei Nicht-200-Status: Gebe Fehlermeldung aus
            System.err.println("[FEHLER] Download gescheitert. HTTP " + conn.getResponseCode());
            System.err.println("Gibt es den User '" + CA_USER + "@" + domain + "' auf dem Server?");
            
            // Lösche temporäre Datei im Fehlerfall
            Files.deleteIfExists(tempKeyFile);
            return; // Beende Programm
        }

        // DOWNLOAD-OPERATION: Kopiere Schlüssel-Daten in temporäre Datei
        // try-with-resources: InputStream wird automatisch geschlossen
        try (InputStream in = conn.getInputStream()) {
            // Kopiere alle Bytes vom InputStream in die temporäre Datei
            // REPLACE_EXISTING: Überschreibe Datei falls sie bereits existiert
            Files.copy(in, tempKeyFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        // SCHRITT 3: IMPORT des Schlüssels in lokales GPG-Keyring
        
        System.out.println("Importiere Schlüssel...");
        
        // Rufe GPG-Kommando auf: gpg --import <tempKeyFile>
        // ProcessBuilder: Erstellt neuen Prozess für externe Kommandos
        // --import: Fügt Schlüssel zum lokalen Keyring hinzu
        // .start(): Startet den Prozess
        // .waitFor(): Wartet bis Prozess beendet ist (blockierend)
        new ProcessBuilder("gpg", "--import", tempKeyFile.toString()).start().waitFor();

        // SCHRITT 4: FINGERPRINT-EXTRAKTION aus dem importierten Schlüssel
        
        String fingerprint = null; // Variable zum Speichern des Fingerprints
        
        // Rufe GPG-Kommando auf: gpg --with-colons --show-keys <tempKeyFile>
        // --with-colons: Ausgabe im maschinenlesbaren Format (Spalten getrennt durch ":")
        // --show-keys: Zeige Schlüssel-Informationen ohne Import
        // Format der Ausgabe: "fpr:::::::::FINGERPRINT::::::"
        Process p = new ProcessBuilder("gpg", "--with-colons", "--show-keys", tempKeyFile.toString()).start();
        
        // PARSING: Lese GPG-Ausgabe Zeile für Zeile
        try (Scanner s = new Scanner(p.getInputStream())) {
            while (s.hasNextLine()) {
                String line = s.nextLine(); // Lese eine Zeile
                
                // FINGERPRINT-ZEILE: Beginnt mit "fpr:"
                if (line.startsWith("fpr:")) {
                    // Splitte Zeile an ":" und hole 10. Feld (Index 9)
                    // Format: fpr:::::::::FINGERPRINT::::::
                    // Der Fingerprint steht an Position 9 (0-basiert)
                    fingerprint = line.split(":")[9];
                    break; // Stoppe Schleife nach erstem Fingerprint
                }
            }
        }

        // SCHRITT 5: TRUST-LEVEL SETZEN (nur wenn Fingerprint erfolgreich extrahiert wurde)
        
        if (fingerprint != null) {
            // TRUST-OPERATION: Setze Trust-Level auf ULTIMATE (6)
            
            System.out.println("Setze ULTIMATE Trust für CA: " + fingerprint);
            
            // Rufe GPG-Kommando auf: gpg --import-ownertrust
            // --import-ownertrust: Importiert Trust-Levels aus stdin
            // Das Kommando erwartet Eingaben im Format: FINGERPRINT:TRUSTLEVEL:
            Process trustProcess = new ProcessBuilder("gpg", "--import-ownertrust").start();
            
            // TRUST-EINGABE: Schreibe Trust-Level in stdin des Prozesses
            try (OutputStream os = trustProcess.getOutputStream()) {
                // Format: FINGERPRINT:6:
                // - FINGERPRINT: Der extrahierte 40-Zeichen-Fingerprint
                // - 6: Trust-Level ULTIMATE (höchster Trust-Level)
                //   Trust-Levels: 1=unknown, 2=never, 3=marginal, 4=full, 5=ultimate, 6=ultimate (eigener Key)
                // - \n: Newline zum Abschluss der Eingabe
                os.write((fingerprint + ":6:\n").getBytes(StandardCharsets.UTF_8));
            } // OutputStream wird automatisch geschlossen und geflusht
            
            // Warte auf Beendigung des Trust-Prozesses
            trustProcess.waitFor();
            
            // ERFOLGS-MELDUNG: CA ist jetzt installiert und vollständig vertrauenswürdig
            System.out.println("[ERFOLG] CA-Schlüssel installiert und vertraut!");
            
        } else {
            // FEHLERFALL: Fingerprint konnte nicht extrahiert werden
            System.err.println("[FEHLER] Konnte Fingerprint nicht lesen.");
            // Programm läuft weiter zum Cleanup, setzt aber keinen Trust
        }

        // CLEANUP: Lösche temporäre Datei mit dem heruntergeladenen Schlüssel
        // Datei wird nicht mehr benötigt, da Schlüssel bereits im Keyring ist
        Files.deleteIfExists(tempKeyFile);
    }
}