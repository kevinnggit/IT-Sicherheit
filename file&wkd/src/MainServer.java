import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import handlers.FileHandler;
import handlers.RegistrationHandler;
import handlers.StaticFileHandler;
import handlers.WKDHandler;

public class MainServer {

    private static final int PORT_FILES = 80;
    private static final int PORT_WKD = 8080;

    public static void main(String[] args) {
        System.out.println(" Starte Server ");

        // WKD starten Port 8080
        try {
            HttpServer wkdServer = HttpServer.create(new InetSocketAddress(PORT_WKD), 0);
            wkdServer.createContext("/well-known/openpgpkey/", new WKDHandler());
            wkdServer.setExecutor(Executors.newFixedThreadPool(10));
            wkdServer.start();
            System.out.println("[Main] WKD-Server läuft auf Port " + PORT_WKD);
        } catch (IOException e) {
            System.err.println("[Main] FEHLER beim Starten von WKD-Server: " + e.getMessage());
        }

        // FileServer starten für Dateien und Registrierung auf Port 80
        try {
            HttpServer fileServer = HttpServer.create(new InetSocketAddress(PORT_FILES), 0);

            // Handler registrieren
            fileServer.createContext("/labornutzer/", new FileHandler());

            fileServer.createContext("/cgi/new", new RegistrationHandler());
            fileServer.createContext("/", new StaticFileHandler());

            fileServer.setExecutor(Executors.newFixedThreadPool(10));
            fileServer.start();
            System.out.println("[Main] Fileserver läuft auf Port " + PORT_FILES);
        } catch (IOException e) {
            System.err.println("[Main] FEHLER beim Starten von Fileserver auf Port " + PORT_FILES);
            System.err.println("       (Hast du Root-Rechte? Port 80 erfordert sudo!)");
            e.printStackTrace();
        }
    }
}
