package handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import services.GpgService;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

public class WKDHandler implements HttpHandler {

    private static final String WKD_CONTEXT = "/well-known/openpgpkey/";
    private final GpgService gpgService = new GpgService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }

        URI uri = exchange.getRequestURI();
        String path = uri.getPath();

        // Wir schauen kurz, ob der Pfad überhaupt lang genug ist
        if (path.length() <= WKD_CONTEXT.length()) {
            sendResponse(exchange, 404, "Not Found");
            return;
        }

        String subPath = path.substring(WKD_CONTEXT.length());
        String[] parts = subPath.split("/");

        // 
        if (parts.length != 3 || !"hu".equals(parts[1])) {
            sendResponse(exchange, 400, "Bad Request");
            return;
        }

        // Wir versuchen den Nutzernamen rauszufinden.
        // Entweder über den Parameter "l=..." (für Tests)
        String localPart = "dummy";
        if (exchange.getRequestURI().getQuery() != null && exchange.getRequestURI().getQuery().contains("l=")) {
            String[] queryParts = exchange.getRequestURI().getQuery().split("=");
            if (queryParts.length > 1) {
                localPart = queryParts[1];
            }
        }

        // Wir basteln die E-Mail-Adresse
        String email = localPart + "@" + parts[0];
        System.out.println("[WKD] Key Anfrage fr: " + email);

        byte[] keyData = gpgService.getPublicKey(email);

        if (keyData != null) {
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, keyData.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(keyData);
            }
        } else {
            sendResponse(exchange, 404, "Not Found");
        }
    }

    private void sendResponse(HttpExchange exchange, int code, String text) throws IOException {
        byte[] body = text.getBytes();
        exchange.sendResponseHeaders(code, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
