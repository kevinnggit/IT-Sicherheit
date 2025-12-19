package certsid.wkdServer;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Scanner;

public class RetrieveCreds {

    // Pfad für die Allowlist (registrierte Nutzer)
    private static final String ALLOWLIST_FILE = "allowlist.txt";
    // Fixe ID für diese Aufgabe (32 Nullen)
    private static final String WKD_ID = "00000000000000000000000000000000";
    // Team Name für die Datei
    private static final String TEAM_NAME = "Team 12";

    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Usage: java RetrieveCreds <Server-IP> <Port> <Domain> <User>");
            System.exit(1);
        }

        String serverIp = args[0];
        String port = args[1];
        String domain = args[2];
        String user = args[3]; // local part, z.B. "alice"

        System.out.println("--- Starte RetrieveCreds für User: " + user + " ---");

        try {
            // 1. URL bauen und Key herunterladen
            Path keyFile = downloadKey(serverIp, port, domain, user);
            System.out.println("[OK] Schlüssel heruntergeladen: " + keyFile);

            // 2. In GPG importieren
            boolean importSuccess = importKeyToGPG(keyFile);
            if (!importSuccess) {
                System.err.println("[FEHLER] GPG Import fehlgeschlagen.");
                return;
            }
            System.out.println("[OK] Schlüssel in GPG importiert.");

            // 3. Trust-Check durchführen
            String email = user + "@" + domain;
            boolean isTrusted = checkGPGTrust(email);

            if (isTrusted) {
                System.out.println("[ERFOLG] Schlüssel ist vertrauenswürdig (validiert durch CA).");
                
                // 4. In Allowlist aufnehmen
                addToAllowlist(user);
                
                // 5. NEU: Benutzerdatei anlegen (Modul 4)
                createUserFile(user);
                
            } else {
                System.err.println("[WARNUNG] Schlüssel importiert, aber NICHT vertrauenswürdig!");
                System.err.println("          Haben Sie den CA-Key des anderen Teams importiert und getrustet?");
            }

            // Aufräumen
            Files.deleteIfExists(keyFile);

        } catch (Exception e) {
            System.err.println("[EXCEPTION] " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * NEU: Erstellt die Datei für den Nutzer (Modul 4 Anforderung).
     * Inhalt: Team Name + Datum.
     */
    private static void createUserFile(String user) throws IOException {
        String content = TEAM_NAME + " - Registriert am: " + Instant.now().toString() + "\n";
        Path filePath = Paths.get(user); // Datei heißt einfach wie der User (z.B. "lars.fischer")
        
        Files.write(filePath, content.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        System.out.println("[INFO] Datei '" + user + "' wurde angelegt.");
    }

    private static Path downloadKey(String ip, String port, String domain, String user) throws IOException {
        String urlStr = String.format("http://%s:%s/well-known/openpgpkey/%s/hu/%s?l=%s",
                ip, port, domain, WKD_ID, user);
        
        System.out.println("Abfrage URL: " + urlStr);
        URL url = URI.create(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(3000);

        if (conn.getResponseCode() != 200) {
            throw new IOException("Server antwortete mit HTTP " + conn.getResponseCode());
        }

        Path tempFile = Files.createTempFile("wkd-key-", ".asc");
        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile;
    }

    private static boolean importKeyToGPG(Path keyFile) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("gpg", "--import", keyFile.toAbsolutePath().toString());
        Process p = pb.start();
        return p.waitFor() == 0;
    }

    private static boolean checkGPGTrust(String email) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("gpg", "--list-keys", "--with-colons", email);
        Process p = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("pub:")) {
                    String[] parts = line.split(":");
                    if (parts.length > 1) {
                        String trust = parts[1];
                        if (trust.equals("f") || trust.equals("u")) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static void addToAllowlist(String user) throws IOException {
        File f = new File(ALLOWLIST_FILE);
        if (!f.exists()) {
            f.createNewFile();
        }
        boolean exists = Files.lines(f.toPath()).anyMatch(line -> line.trim().equals(user));
        if (!exists) {
            Files.write(f.toPath(), (user + "\n").getBytes(), StandardOpenOption.APPEND);
            System.out.println("[INFO] User '" + user + "' zur Allowlist hinzugefügt.");
        } else {
            System.out.println("[INFO] User '" + user + "' war bereits in der Allowlist.");
        }
    }
}