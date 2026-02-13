package services;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class GpgService {

    public byte[] getPublicKey(String email) {
        ProcessBuilder processBuilder = new ProcessBuilder();
        // Hier ggf. den Pfad zu gpg anpassen, falls ihr woanders installiert habt.
        // Wir rufen gpg auf der Kommandozeile auf, um den Schlüssel zu exportieren.
        processBuilder.command("gpg", "--export", email);
        try {
            Process process = processBuilder.start();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            InputStream input = process.getInputStream();
            byte[] data = new byte[1024];
            int nRead;
            while ((nRead = input.read(data, 0, data.length)) != -1) {
                output.write(data, 0, nRead);
            }
            if (process.waitFor() == 0 && output.size() > 0) return output.toByteArray();
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    public boolean verifySignature(byte[] nonce, byte[] signature, String expectedUserEmail) {
        Path nonceFile = null;
        Path sigFile = null;

        try {
            // Wir erstellen temporäre Dateien für die Prüfung – gpg arbeitet am liebsten mit Dateien.
            nonceFile = Files.createTempFile("auth-nonce-", ".dat");
            sigFile = Files.createTempFile("auth-sig-", ".asc");

            Files.write(nonceFile, nonce);
            Files.write(sigFile, signature);

            ProcessBuilder pbVerify = new ProcessBuilder(
                    "gpg",
                    "--batch",
                    "--ignore-time-conflict",
                    "--verify",
                    sigFile.toString(),
                    nonceFile.toString()
            );

            pbVerify.redirectErrorStream(true);
            Process p = pbVerify.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;

            boolean signatureValid = false;
            boolean userMatch = false;

            // Output Parsing: Wir lesen, was gpg uns sagt.
            while ((line = reader.readLine()) != null) {
                // Debug Ausgabe kann hier reduziert werden
                // System.out.println("[GPG] " + line);

                // Hat gpg "Good signature" gesagt?
                if (line.contains("Good signature")) {
                    signatureValid = true;
                }
                // Wurde mit dem Schlüssel unterschrieben, den wir erwarten?
                if (line.contains(expectedUserEmail)) {
                    userMatch = true;
                }
            }

            int exitCode = p.waitFor();

            if (exitCode == 0 && signatureValid && userMatch) {
                return true;
            }
            return false;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (nonceFile != null) Files.deleteIfExists(nonceFile);
                if (sigFile != null) Files.deleteIfExists(sigFile);
            } catch (IOException e) { }
        }
    }



    /**
     * Importiert einen ASCII-Armored Public Key in den Keyring.
     */
    public boolean importKey(String keyContent) {
        Path tempKeyFile = null;
        try {
            // Key in temporäre Datei schreiben
            tempKeyFile = Files.createTempFile("import-key-", ".asc");
            Files.write(tempKeyFile, keyContent.getBytes());

            ProcessBuilder pb = new ProcessBuilder("gpg", "--batch", "--import", tempKeyFile.toAbsolutePath().toString());
            // pb.inheritIO(); // Zum Debuggen einkommentieren
            Process p = pb.start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            try { if(tempKeyFile != null) Files.deleteIfExists(tempKeyFile); } catch(IOException e){}
        }
    }



    /**
     * Signiert den Schlüssel.
     * KORREKTUR: Sucht erst den Fingerprint, da --quick-sign-key diesen zwingend braucht.
     */
    public boolean signKey(String email) {
        try {
            // 1. Fingerprint ermitteln
            String fingerprint = getFingerprint(email);

            if (fingerprint == null) {
                System.err.println("[GPG] Fehler: Kein Fingerprint für " + email + " gefunden (Import fehlgeschlagen?).");
                return false;
            }

            System.out.println("[GPG] Signiere Fingerprint: " + fingerprint + " (" + email + ")");


	    // 2. Signieren mit Fingerprint
            //
            ProcessBuilder pb = new ProcessBuilder(
                "gpg",
                "--batch",
                "--yes",
                "--pinentry-mode", "loopback",
                "--quick-sign-key",
                fingerprint,
                email
            );

            // Debugging (stderr lesen)
            pb.redirectErrorStream(true);
            Process p = pb.start();

            // Output lesen (wichtig, falls Buffer voll läuft)
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
               System.out.println("[GPG-LOG] " + line); // Bei Bedarf einkommentieren
            }

            int code = p.waitFor();
            if (code == 0) {
                System.out.println("[GPG] Signatur erfolgreich.");
                return true;
            } else {
                System.err.println("[GPG] Fehler beim Signieren. Exit Code: " + code);
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * NEU: Holt den Fingerprint für eine E-Mail/User-ID.
     */
    private String getFingerprint(String email) {
        try {
            // --with-colons gibt maschinenlesbaren Output
            ProcessBuilder pb = new ProcessBuilder("gpg", "--list-keys", "--with-colons", email);
            Process p = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            String foundFpr = null;

            while ((line = reader.readLine()) != null) {
                // Sucht nach der Zeile, die mit "fpr" beginnt
                if (line.startsWith("fpr:")) {
                    String[] parts = line.split(":");
                    // Bei 'fpr' steht der Fingerprint an Index 9
                    if (parts.length > 9) {
                        foundFpr = parts[9];
                        break; // Den ersten nehmen
                    }
                }
            }
            p.waitFor();
            return foundFpr;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
