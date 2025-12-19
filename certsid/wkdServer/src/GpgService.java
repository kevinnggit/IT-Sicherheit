package certsid.wkdServer.src;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class GpgService {

    // Modul 2: Public Key holen (Bleibt gleich)
    public byte[] getPublicKey(String email) {
        ProcessBuilder processBuilder = new ProcessBuilder();
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

    /**
     * TEXT-BASIERTE VERIFIKATION (Ohne Keyring-Isolation)
     * 1. Prüft Signatur gegen ALLES, was im Server-Keyring ist.
     * 2. Liest den Text-Output und prüft, ob die erwartete E-Mail darin vorkommt.
     */
    public boolean verifySignature(byte[] nonce, byte[] signature, String expectedUserEmail) {
        Path nonceFile = null;
        Path sigFile = null;

        try {
            nonceFile = Files.createTempFile("auth-nonce-", ".dat");
            sigFile = Files.createTempFile("auth-sig-", ".asc");

            Files.write(nonceFile, nonce);
            Files.write(sigFile, signature);

            // Der einfache Befehl: Prüfe gegen den normalen Keyring
            ProcessBuilder pbVerify = new ProcessBuilder(
                "gpg", 
                "--batch",
                "--ignore-time-conflict", // WICHTIG für Labor
                "--verify", 
                sigFile.toString(), 
                nonceFile.toString()
            );
            
            // Wir müssen stderr (Fehlerausgabe) lesen, da GPG dort die Infos ausgibt
            pbVerify.redirectErrorStream(true);
            
            Process p = pbVerify.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            
            boolean signatureValid = false;
            boolean userMatch = false;
            
            // Wir "scannen" den Output von GPG
            System.out.println("--- GPG CHECK FÜR: " + expectedUserEmail + " ---");
            while ((line = reader.readLine()) != null) {
                // Zeige GPG Output im Server-Log an (Debug)
                System.out.println("[GPG] " + line);

                // 1. Ist die Signatur mathematisch korrekt?
                // GPG gibt aus: "Good signature from ..."
                if (line.contains("Good signature")) {
                    signatureValid = true;
                }

                // 2. Stammt sie vom richtigen User?
                // GPG gibt aus: "... from "Kevin <kevin@smail...>"
                // Wir prüfen einfach, ob die erwartete E-Mail in der Zeile vorkommt.
                if (line.contains(expectedUserEmail)) {
                    userMatch = true;
                }
            }
            System.out.println("-------------------------------------------");
            
            int exitCode = p.waitFor();

            // Damit der Login gilt, muss GPG "OK" sagen (exit 0) 
            // UND wir müssen "Good signature" gesehen haben
            // UND die E-Mail muss im Output gewesen sein.
            
            if (exitCode == 0 && signatureValid && userMatch) {
                return true;
            } else {
                if (!userMatch && signatureValid) {
                    System.out.println("[AUTH-FAIL] Signatur gültig, aber falscher User hat unterschrieben!");
                }
                return false;
            }

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
}