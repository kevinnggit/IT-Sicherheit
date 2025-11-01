

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

public class ETPServer {
    private static String labor_host = "10.42.1.23";
    private static int labor_port = 3033;
    private static final int RSA_BIT_LENGTH = 512;


    public static void main(String[] args) {

        System.out.println("Starte ETP Server...");

        try {
            Krypto crypto = new Krypto(RSA_BIT_LENGTH);

            ServerSocket serverSocket = new ServerSocket(0);
            int serverPort = serverSocket.getLocalPort();

            Thread serverThread = new Thread(()-> {
                System.out.println("ETP Server läuft auf Port: " + serverPort);
                try {
                    while (true) {
                        Socket clientSocket = serverSocket.accept();
                        new ClientHandler(clientSocket, crypto).start();
                    }
                } catch (IOException e) {
                    System.err.println("[ETP-Server] Fehler im Server-Loop: " + e.getMessage());
                } finally {
                    try {
                        serverSocket.close();
                    } catch (IOException e) {
                        //--------
                    }
                }
            });

            serverThread.start();
            announceToServer(serverPort);
            System.out.println("ETP Server ist bereit.");
        } catch (IOException e) {
            System.err.println("[ETP-Server] Fehler beim Starten des Servers: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("[ETP-Server] Fehler bei Krypto-Initialisierung: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void announceToServer(int Port) {
        System.out.println("[Announce] Ankündigung an Laborserver...");
        
        try (
            Socket socket = new Socket(labor_host, labor_port);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            ) {
                //Sende Portnummer als ASCII-Text
                String portMessage = String.valueOf(Port);
                out.println(portMessage);

                //Warte auf "OK" Antwort
                String response = in.readLine();

                if ("ok".equals(response)) {
                    System.out.println("[Announce] Erfolgreich angemeldet.");
                } else {
                    System.err.println("[Announce] Fehler bei der Anmeldung: " + response);
                }
            } catch (UnknownHostException e) {
                System.err.println("[Announce] Unbekannter Host: " + labor_host);
            } catch (IOException e) {
                System.err.println("[Announce] I/O Fehler bei der Verbindung zum Laborserver: " + e.getMessage());
            }
    }
}