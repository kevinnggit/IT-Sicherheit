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

    // Standard-Port, falls kein Argument angegeben wird
    private static final int DEFAULT_ANNOUNCE_PORT = 3033;
    private static final int BLOCK_SIZE_BYTES = 64; 

    public static void main(String[] args) {
        
        //IP und Port aus Argumenten lesen
        String listenIP = "0.0.0.0";
        int listenPort = DEFAULT_ANNOUNCE_PORT;

        if (args.length > 0) {
            listenIP = args[0];
            System.out.println("[Info] Verwende Listen-IP: " + listenIP);
        }
        if (args.length > 1) {
            listenPort = Integer.parseInt(args[1]);
            System.out.println("[Info] Verwende Listen-Port: " + listenPort);
        }
        
        Scanner userInput = new Scanner(System.in);
        String etpServerIP = "";
        int etpServerPort = -1;

        try {
            //ROLLE 1: ANKÜNDIGUNGS-SERVER
            //übergebe die IP und den Port an die Methode
            Object[] serverInfo = waitForAnnouncement(userInput, listenIP, listenPort);
            
            etpServerIP = (String) serverInfo[0];
            etpServerPort = (int) serverInfo[1];

            if (etpServerPort == -1) {
                System.out.println("[FEHLER] Ankündigung fehlgeschlagen. Beende.");
                return;
            }

            Thread.sleep(1000); 

            //ROLLE 2: INTERAKTIVER ETP-CLIENT
            //übergebe die (jetzt echte) IP und den Port des Servers
            interactWithEtpServer(userInput, etpServerIP, etpServerPort);

        } catch (IOException | InterruptedException e) {
            System.err.println("Ein Fehler ist aufgetreten: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Fehler beim Parsen der Server-Infos: " + e.getMessage());
        } finally {
            userInput.close();
        }
    }

    /*
      ROLLE 1: Öffnet den ServerSocket auf der spezifizierten IP und Port.
      return Ein Object-Array: {String serverIP, int serverPort} oder {null, -1}
     */
    private static Object[] waitForAnnouncement(Scanner userInput, String listenIP, int listenPort) throws IOException {
        System.out.println("[Rolle 1] Starte Ankündigungs-Server auf " + listenIP + ":" + listenPort + "...");
        System.out.println(">>> BITTE JETZT DEN ETPSERVER STARTEN (./run.sh) <<<");
        System.out.println("(Stelle sicher, dass ETPServer.java auf diese IP/Port zeigt)");

        // Erzeuge die IP-Adresse
        InetAddress bindAddr = InetAddress.getByName(listenIP);
        
        // Binde den ServerSocket an die spezifische IP und Port
        try (
            ServerSocket announceSocket = new ServerSocket(listenPort, 50, bindAddr);
            Socket etpServerSocket = announceSocket.accept();
            BufferedReader in = new BufferedReader(new InputStreamReader(etpServerSocket.getInputStream()));
            PrintWriter out = new PrintWriter(etpServerSocket.getOutputStream(), true)
        ) {
            // WICHTIG: Erfasse die IP, von der sich der Server meldet
            String serverIP = etpServerSocket.getInetAddress().getHostAddress();
            
            System.out.println("[Rolle 1] ETPServer von IP " + serverIP + " hat sich verbunden!");
            
            // Portnummer lesen
            String portStr = in.readLine();
            int port = Integer.parseInt(portStr);
            System.out.println("[Rolle 1] ETPServer lauscht auf Port: " + port);

            // "ok" senden
            out.println("ok");
            System.out.println("[Rolle 1] 'ok' gesendet. Rolle 1 beendet.");
            
            // Gebe BEIDE Infos zurück
            return new Object[]{serverIP, port};
            
        } catch (Exception e) {
            System.err.println("[Rolle 1] Fehler: " + e.getMessage());
            return new Object[]{null, -1};
        }
    }

    /*
     ROLLE 2: Verbindet sich mit der ECHTEN IP und Port des ETPServers.
     */
    private static void interactWithEtpServer(Scanner userInput, String serverIP, int serverPort) throws IOException {
        System.out.println("\n--- [Rolle 2] ---");
        System.out.println("Verbinde mit ETPServer auf " + serverIP + ":" + serverPort + "...");

        // Verbinde mit der erfassten IP und dem Port
        try (
            Socket etpSocket = new Socket(serverIP, serverPort);
            PrintWriter out = new PrintWriter(etpSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(etpSocket.getInputStream()))
        ) {
            System.out.println("[Rolle 2] Verbunden!");

            //SCHRITT 1: ANFRAGE SENDEN
            System.out.print("\nWas soll ich senden? (z.B. 'GET pubkey ETP/2025'):\n> ");
            String request = userInput.nextLine();
            out.println(request);

            //SCHRITT 2: SCHLÜSSEL EMPFANGEN
            System.out.println("\nWarte auf Schlüssel vom Server...");
            String pubLine = in.readLine();
            String nLine = in.readLine();

            System.out.println("Empfangen:\n" + pubLine + "\n" + nLine);

            if (pubLine == null || nLine == null || !pubLine.startsWith("pub:") || !nLine.startsWith("N:")) {
                System.out.println("[FEHLER] Unerwartete Antwort vom Server. (Hat der Server einen Fehler gesendet?)");
                return;
            }

            BigInteger e = new BigInteger(pubLine.split(" ")[1], 16);
            BigInteger N = new BigInteger(nLine.split(" ")[1], 16);
            System.out.println("\nSchlüssel erfolgreich geparst.");

            // --- SCHRITT 3: NACHRICHT VERSCHLÜSSELN ---
            System.out.print("\nWelche Nachricht soll ich verschlüsseln und senden?\n> ");
            String message = userInput.nextLine();

            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
            byte[] paddedMessage = new byte[BLOCK_SIZE_BYTES];
            System.arraycopy(messageBytes, 0, paddedMessage, 0, Math.min(messageBytes.length, BLOCK_SIZE_BYTES));
            
            BigInteger M = new BigInteger(1, paddedMessage);

            System.out.println("Verschlüssele Nachricht...");
            BigInteger C = M.modPow(e, N);
            String ciphertextHex = C.toString(16);

            //SCHRITT 4: GEHEIMTEXT SENDEN
            System.out.println("Sende Geheimtext:\n" + ciphertextHex);
            out.println(ciphertextHex);

            System.out.println("\n[Rolle 2] Test abgeschlossen.");
            System.out.println(">>> ÜBERPRÜFE JETZT DIE KONSOLE DEINES ETPSERVERS! <<<");

        } catch (Exception e) {
            System.err.println("[Rolle 2] Fehler: " + e.getMessage());
        }
    }
}