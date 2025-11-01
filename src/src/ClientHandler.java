package src;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import java.math.BigInteger;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class ClientHandler extends Thread{
    private Socket clientSocket;
    private Krypto crypto;

    public ClientHandler(Socket socket, Krypto crypto) {
        this.clientSocket = socket;
        this.crypto = crypto;
    }

    @Override
    public void run () {
        try (
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            BufferedReader in =  new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        ) {
            String clientIP = clientSocket.getInetAddress().getHostAddress();
            System.out.println("Neue Verbindung von : " + clientIP);

            //C --> S: "Get Pubkey ETP/2025"
            String requestLine = in.readLine();

            if (requestLine == null) {
                System.out.println("Client " + clientIP + " hat die Verbindung vorzeitig geschlossen.");
                return;
            }

            //------------------------------------------------------------------
            //Protokoll Fehlerbehandlung
            //------------------------------------------------------------------
            
            String[] parts = requestLine.split(" ");

            if (parts.length != 3) {
                System.out.println("Ungültige Anfrage von " + clientIP + ": " + "Falsches Kommando");
                out.println("400 Bad Request: Falsches Kommando");
                return;
            }

            String command = parts[0], resource = parts[1], protocol = parts[2];

            if (!command.equals("GET")) {
                System.out.println("Fehler von " + clientIP + ": " + "Unbekanntes Kommando '" + command + "'.");
                out.println("400 Bad Request: Unbekanntes Kommando");
                return;
            }

            if (!resource.equals("Pubkey")) {
                System.out.println("Fehler von " + clientIP + ": " + "Unbekannte Ressource '" + resource + "'.");
                out.println("404 Not Found: Unbekannte Ressource");
                return;
            }

            if (!protocol.equals("ETP/2025")) {
                System.out.println("Fehler von " + clientIP + ": " + "Unbekanntes Protokoll '" + protocol + "'.");
                out.println("400 Bad Request: Unbekanntes Protokoll");
                return;
            }

            System.out.println("Anfrage von " + clientIP + " korrekt.");

            //S --> C: "pub: <pubkey>\nN: <modul>\n"
            String pubkeyHex = crypto.getPubkeyHex();
            String modulHex = crypto.getModuleHex();

            out.printf("pub: %s\n", pubkeyHex);
            out.printf("N: %s\n", modulHex);

            System.out.println("Öffentlichen Schlüssel an " + clientIP + " gesendet.");

            //C --> S: "<ciphertext>"
            String cipherTextHex = in.readLine();

            if (cipherTextHex == null || cipherTextHex.isEmpty()) {
                System.out.println("Fehler von " + clientIP + ": " + "Client hat keinen <ciphertext> gesendet.");
                return;
            }

            try {
                //Konvertieren von Hex-String zu BigInteger
                BigInteger ciphertext = new BigInteger(cipherTextHex, 16);

                //Entschlüsseln mit privatem Schlüssel (M = C^d mod N)
                BigInteger message = crypto.decrypt(ciphertext);

                //Konvertieren der Nachricht zu Byte-Array
                byte[] messageBytes = message.toByteArray();

                int paddingStartIndex = -1;
                for (int i = 0; i < messageBytes.length; i++) {
                    if (messageBytes[i] == 0x00) {
                        //paddingStartIndex = i;
                        break;
                    }
                }

                int textLength = messageBytes.length;

                for (int i = 0; i < messageBytes.length; i++) {
                    if (messageBytes[i] == 0x00) {
                        textLength = i;
                        break;
                    }
                }

                //Extrahieren der eigentlichen Nachricht ohne Padding
                byte[] normal_messageBytes = Arrays.copyOf(messageBytes, textLength);

                //Nachricht in String umwandeln
                String messageStr = new String(normal_messageBytes, StandardCharsets.UTF_8);

                //Nachricht ausgeben
                System.out.println("------------------------------------------------");
                System.out.println("Nachricht von " + clientIP + ": ");
                System.out.println(messageStr);
                System.out.println("------------------------------------------------");
            } catch (NumberFormatException nfe) {
                //--------
                System.out.println("Fehler von " + clientIP + ": " + "Ungültiger <ciphertext>.");
                out.println("400 Bad Request: Ungültiger <ciphertext> (nicht Hex).");
            } catch (Exception e) {
                //--------
                System.out.println("Fehler bei der Entschlüsselung der Nachricht von " + clientIP + ": " + e.getMessage());
                out.println("500 Internal Server Error: Fehler bei der Entschlüsselung.");
                e.printStackTrace();
            }

            System.out.println("Verbindung zu " + clientIP + " wird geschlossen.");

        } catch (Exception e) {
            System.out.println("Socket-Fehler beim ClientHandler: " + e.getMessage());
        }






    }

}
