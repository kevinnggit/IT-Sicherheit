package IT_S.manual_tester.src;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Scanner;

public class TesterHost {

    private static final int DEFAULT_ANNOUNCE_PORT = 3033;
    private static final int BLOCK_SIZE_BYTES = 64; 

    public static void main(String[] args) {
        
        String listenIP = "0.0.0.0";
        int listenPort = DEFAULT_ANNOUNCE_PORT;

        if (args.length > 0) listenIP = args[0];
        if (args.length > 1) listenPort = Integer.parseInt(args[1]);
        
        Scanner userInput = new Scanner(System.in);
        String etpServerIP = "";
        int etpServerPort = -1;

        try {
            // --- ROLLE 1: ANKÜNDIGUNGS-SERVER ---
            Object[] serverInfo = waitForAnnouncement(userInput, listenIP, listenPort);
            
            etpServerIP = (String) serverInfo[0];
            etpServerPort = (int) serverInfo[1];

            if (etpServerPort == -1) {
                System.out.println("[FEHLER] Ankündigung fehlgeschlagen. Beende.");
                return;
            }

            Thread.sleep(1000); 

            // --- ROLLE 2: INTERAKTIVER ETP-CLIENT ---
            interactWithEtpServer(userInput, etpServerIP, etpServerPort);

        } catch (Exception e) {
            System.err.println("Ein globaler Fehler ist aufgetreten: " + e.getMessage());
        } finally {
            userInput.close();
            System.out.println("Tester wird beendet.");
        }
    }

    private static Object[] waitForAnnouncement(Scanner userInput, String listenIP, int listenPort) throws IOException {
        System.out.println("[Rolle 1] Starte Ankündigungs-Server auf " + listenIP + ":" + listenPort + "...");
        System.out.println(">>> BITTE JETZT DEINEN ETPSERVER STARTEN (./run.sh) <<<");

        InetAddress bindAddr = InetAddress.getByName(listenIP);
        
        try (
            ServerSocket announceSocket = new ServerSocket(listenPort, 50, bindAddr);
            Socket etpServerSocket = announceSocket.accept();
            BufferedReader in = new BufferedReader(new InputStreamReader(etpServerSocket.getInputStream()));
            PrintWriter out = new PrintWriter(etpServerSocket.getOutputStream(), true)
        ) {
            String serverIP = etpServerSocket.getInetAddress().getHostAddress();
            System.out.println("[Rolle 1] ETPServer von IP " + serverIP + " hat sich verbunden!");
            
            String portStr = in.readLine();
            int port = Integer.parseInt(portStr);
            System.out.println("[Rolle 1] ETPServer lauscht auf Port: " + port);

            out.println("ok");
            System.out.println("[Rolle 1] 'ok' gesendet. Rolle 1 beendet.");
            return new Object[]{serverIP, port};
            
        } catch (Exception e) {
            System.err.println("[Rolle 1] Fehler: " + e.getMessage());
            return new Object[]{null, -1};
        }
    }

    /**
     * ROLLE 2: Verbindet sich mit dem ETPServer und führt
     * das ETP-Protokoll interaktiv aus.
     */
    private static void interactWithEtpServer(Scanner userInput, String serverIP, int serverPort) throws IOException, InterruptedException {
        System.out.println("\n--- [Rolle 2] ---");
        System.out.println("Verbinde mit ETPServer auf " + serverIP + ":" + serverPort + "...");

        try (
            Socket etpSocket = new Socket(serverIP, serverPort);
            PrintWriter out = new PrintWriter(etpSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(etpSocket.getInputStream()))
        ) {
            System.out.println("[Rolle 2] Verbunden!");

            BigInteger e = null;
            BigInteger N = null;

            // --- SCHRITT 1 & 2: SCHLÜSSEL ABFRAGEN (MIT WIEDERHOLUNGSSCHLEIFE) ---
            while (true) {
                System.out.print("\nWas soll ich senden? (z.B. 'GET pubkey ETP/2025'):\n> ");
                String request = userInput.nextLine();
                out.println(request);

                System.out.println("\nWarte auf Schlüssel vom Server...");
                String pubLine = in.readLine();
                String nLine = in.readLine();

                System.out.println("Empfangen:\n" + pubLine + (nLine != null ? "\n" + nLine : ""));

                // Prüfe auf Erfolg
                if (pubLine != null && nLine != null && pubLine.startsWith("pub:") && nLine.startsWith("N:")) {
                    // ERFOLG!
                    e = new BigInteger(pubLine.split(" ")[1], 16);
                    N = new BigInteger(nLine.split(" ")[1], 16);
                    System.out.println("\nSchlüssel erfolgreich geparst.");
                    break; // Verlasse die GET-Schleife
                } else {
                    // FEHLER!
                    System.out.println("[FEHLER] Unerwartete Antwort vom Server.");
                    System.out.println("Warte 2 Sekunden und versuche es erneut...");
                    Thread.sleep(2000);
                    // Die Schleife (while(true)) beginnt von vorn
                }
            }

            // --- SCHRITT 3 & 4: NACHRICHT SENDEN (KEINE SCHLEIFE) ---
            // Wir sind hier, weil die Schlüsselabfrage erfolgreich war.
            // Wir können jetzt EINE Nachricht senden, dann beendet der Server
            // (korrekterweise) die Verbindung.
            
            System.out.print("\nWelche Nachricht soll ich verschlüsseln und senden? (max 64 Bytes)\n> ");
            String message = userInput.nextLine();

            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
            
            // Warnung, wenn die Nachricht zu lang ist (wie wir besprochen haben)
            if (messageBytes.length > BLOCK_SIZE_BYTES) {
                System.out.println("[WARNUNG] Nachricht ist > 64 Bytes. Sie wird abgeschnitten!");
            }

            byte[] paddedMessage = new byte[BLOCK_SIZE_BYTES];
            System.arraycopy(messageBytes, 0, paddedMessage, 0, Math.min(messageBytes.length, BLOCK_SIZE_BYTES));
            
            BigInteger M = new BigInteger(1, paddedMessage);

            System.out.println("Verschlüssele Nachricht...");
            BigInteger C = M.modPow(e, N);
            String ciphertextHex = C.toString(16);

            System.out.println("Sende Geheimtext:\n" + ciphertextHex);
            out.println(ciphertextHex);

            System.out.println("\n[Rolle 2] Test abgeschlossen.");
            System.out.println(">>> ÜBERPRÜFE JETZT DIE KONSOLE DEINES ETPSERVERS! <<<");
            
            // Die Verbindung wird hier enden, da der Server sie schließt.
            // Ein erneutes Senden ist nicht möglich (und nicht vorgesehen).

        } catch (Exception e) {
            System.err.println("[Rolle 2] Fehler: " + e.getMessage());
        }
    }
}
