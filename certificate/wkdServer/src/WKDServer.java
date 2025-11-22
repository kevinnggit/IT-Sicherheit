package certificate.wkdServer.src;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;

public class WKDServer {
    // Konfig
    private static final int PORT = 8000;
    private static final String TEAM_DOMAIN = "localhost";  // team-12.example.org
    private static final String HS_DOMAIN = "hs-bremerhaven.de";

    // Pfad für WKD
    private static final String WKD_CONTEXT = "/well-known/openpgpkey/";

    private static final GpgService gpgService = new GpgService();

    public static void main (String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        server.createContext(WKD_CONTEXT, new WKDHandler());

        server.setExecutor(null); // Default Executor
        System.out.println("WKD Server läuft auf Port " + PORT);
        server.start();
    }

    static class WKDHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Nur GET-Anfragen verarbeiten
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            // URI analysieren
            URI uri = exchange.getRequestURI();
            String path = uri.getPath();
            // Pfad: /well-known/openpgpkey/hs-bremerhaven.de/hu/00...00
            // ntfernen den fixen Prefix, um den Rest zu prüfen
            String subPath = path.substring(WKD_CONTEXT.length());
            String[] parts = subPath.split("/");

            // wir erwarten 3 Teile: domain, hu, keyid
            if (parts.length != 3) {
                sendResponse(exchange, 400, "Bad Request: Ungültige Pfadstruktur");
                return;
            }

            String domain = parts[0];
            String type = parts[1];     // sollte "hu" sein
            String id = parts[2];         // muss 32 nullen sein

            if (!"hu".equals(type)) {
                sendResponse(exchange, 400, "Bad Request: Ungültiger Typ (muss 'hu' sein)");
                return;
            }

            boolean isHsDomain = domain.endsWith(HS_DOMAIN);
            boolean isTeamDomain = domain.equals(TEAM_DOMAIN);

            if (!isHsDomain && !isTeamDomain) {
                sendResponse(exchange, 404, "Not Found: Unbekannte Domain");
                return;
            }

            if (!id.equals("00000000000000000000000000000000")) {
                sendResponse(exchange, 404, "Not Found: Ungültige ID");
                return;
            }

            String query = uri.getQuery();
            String localPart = null;

            if (query != null && query.startsWith("l=")) {
                localPart = query.substring(2);
            }

            if (localPart == null || localPart.isEmpty()) {
                sendResponse(exchange, 400, "Bad Request: Fehlender lokaler Teil (l) der E-Mail");
                return;
            }

            System.out.println("Anfrage validiert. Suche Schlüssel für " + localPart + "@" + domain);

            // Schlüssel suchen
            String searchEmail = localPart + "@" + domain;

            // Falls jemand nach CA-Key des Servers fragt
            byte[] keyData = gpgService.getPublicKey(searchEmail);

            if (keyData != null) {
                // Schlüssel gefunden, senden als binary
                exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                exchange.sendResponseHeaders(200, keyData.length);
                OutputStream os = exchange.getResponseBody();
                os.write(keyData);
                os.close();
                System.out.println("Schlüssel für " + searchEmail + " gesendet.");
            } else {
                // Schlüssel nicht gefunden
                sendResponse(exchange, 404, "---> Not Found: Schlüssel nicht gefunden");
                System.out.println("---> Kein Schlüssel für " + searchEmail + " gefunden.");
            }
        }

        private void sendResponse(HttpExchange exchange, int code, String text) throws IOException {
            exchange.sendResponseHeaders(code, text.length());
            OutputStream os = exchange.getResponseBody();
            os.write(text.getBytes());
            os.close();
        }
    }
}
