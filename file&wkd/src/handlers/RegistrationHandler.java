package handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import services.GpgService;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

public class RegistrationHandler implements HttpHandler {

    private final GpgService gpgService = new GpgService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            handlePost(exchange);
        } else {
            exchange.getResponseHeaders().set("Location", "/");
            exchange.sendResponseHeaders(302, -1);
        }
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        // Body lesen
        InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
        BufferedReader br = new BufferedReader(isr);
        StringBuilder requestBody = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            requestBody.append(line);
        }

        // Parameter parsen (x-www-form-urlencoded)
        Map<String, String> params = parseFormData(requestBody.toString());
        
        String username = params.get("user");
        String email = params.get("email");
        String pubKey = params.get("pubkey");

        // Validierung
        if (username == null || !username.matches("[a-zA-Z0-9._-]+")) {
            sendResponse(exchange, 400, "Fehler: Ungültiger Benutzername.");
            return;
        }
        if (email == null || !email.contains("@")) {
            sendResponse(exchange, 400, "Fehler: Ungültige E-Mail.");
            return;
        }
        if (pubKey == null || !pubKey.contains("BEGIN PGP PUBLIC KEY BLOCK")) {
            sendResponse(exchange, 400, "Fehler: Kein gültiger PGP Key erkannt.");
            return;
        }

        // Prüfen ob User schon existiert
        Path userFile = Paths.get(username);
        if (Files.exists(userFile)) {
            sendResponse(exchange, 409, "Fehler: Benutzername vergeben.");
            return;
        }

        System.out.println("[REG] Starte Prozess für: " + username + " (" + email + ")");

        // GPG Workflow
        // Importieren
        boolean importSuccess = gpgService.importKey(pubKey);
        if (!importSuccess) {
            sendResponse(exchange, 500, "Fehler: Key konnte nicht importiert werden (Format falsch?).");
            return;
        }

        // Signieren
        boolean signSuccess = gpgService.signKey(email);
        if (!signSuccess) {
            // Wir machen trotzdem weiter, warnen aber im Log.
            System.err.println("[WARN] Konnte Key nicht signieren.");
        }

        // 6. Datei anlegen
        try {
            String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
            String content = "----------------------------------------\n" +
                             " Benutzerakte: " + username + "\n" +
                             "----------------------------------------\n" +
                             "E-Mail:         " + email + "\n" +
                             "Registriert am: " + dateStr + "\n" +
                             "Status:         " + (signSuccess ? "Verifiziert & Signiert" : "Importiert (Unsigniert)") + "\n" +
                             "----------------------------------------\n";

            Files.write(userFile, content.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
            
            String responseHtml = "<html><body><h1>Registrierung erfolgreich!</h1>" +
                                  "<p>User: <b>" + username + "</b></p>" +
                                  "<p>Key Status: " + (signSuccess ? "<span style='color:green'>Signiert & Akzeptiert</span>" : "<span style='color:orange'>Importiert (Signatur fehlgeschlagen)</span>") + "</p>" +
                                  "<p><a href='/labornutzer/" + username + "'>Zum Login (Challenge)</a></p></body></html>";
            
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            byte[] body = responseHtml.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }

        } catch (IOException e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "Serverfehler beim Speichern.");
        }
    }

    private Map<String, String> parseFormData(String formData) {
        Map<String, String> map = new HashMap<>();
        String[] pairs = formData.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=");
            if (kv.length == 2) {
                try {
                    String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8.name());
                    String value = URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name());
                    map.put(key, value);
                } catch (UnsupportedEncodingException e) {}
            }
        }
        return map;
    }

    private void sendResponse(HttpExchange exchange, int code, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, body.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
    }
}
