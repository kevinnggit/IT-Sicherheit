package handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import services.ChallengeManager;
import services.GpgService;
import services.SessionManager;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;

public class FileHandler implements HttpHandler {

    private static final String LABOR_CONTEXT = "/labornutzer/";
    private static final String[] VALID_DOMAINS = {
            "smail.hs-bremerhaven.de",
            "student.hs-bremerhaven.de",
            "hs-bremerhaven.de"
    };

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        //
        SessionManager sessionManager = SessionManager.getInstance();
        ChallengeManager challengeManager = ChallengeManager.getInstance();
        GpgService gpgService = new GpgService();

        // Schauen wir, welche Datei der Nutzer haben will
        String path = exchange.getRequestURI().getPath();
        if (!path.startsWith(LABOR_CONTEXT)) {
            sendResponse(exchange, 404, "Not Found", null);
            return;
        }

        String requestedResource = path.substring(LABOR_CONTEXT.length());
        if (requestedResource.contains("?")) requestedResource = requestedResource.split("\\?")[0];

        // Will jemand aus dem Ordner ausbrechen ("..")?
        if (requestedResource.isEmpty() || requestedResource.contains("/") || requestedResource.contains("..")) {
            sendResponse(exchange, 400, "Bad Request", null);
            return;
        }

        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
        String authIdentifier = clientIp + ":" + requestedResource;

        // Prüfen: Ist der Nutzer vielleicht schon eingeloggt?
        String sessionId = getSessionCookie(exchange);
        String sessionUser = null;
        if (sessionId != null) {
            sessionUser = sessionManager.getUserFromSession(sessionId);
        }

        // Wer darf hier was?
        if (sessionUser != null) {
            //
            boolean isOwner = sessionUser.equals(requestedResource);
            boolean isAdmin = "lars.fischer".equals(sessionUser);

            if (isOwner || isAdmin) {
                Path filePath = Paths.get(requestedResource);
                if (Files.exists(filePath)) {
                    String content = new String(Files.readAllBytes(filePath));
                    sendResponse(exchange, 200, content, null);
                } else {
                    sendResponse(exchange, 404, "Datei existiert (noch) nicht.", null);
                }
            } else {
                sendResponse(exchange, 403, "Forbidden: Kein Zugriff auf fremde Datei.", null);
            }
            return;
        }

        // Der Nutzer ist nicht eingeloggt, versucht es
        List<String> authHeaders = exchange.getRequestHeaders().get("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String authHeader = authHeaders.get(0);
            if (authHeader.startsWith("GPGSig ")) {

                // Ist das die Antwort auf unsere Challenge (Zufallszahl)?
                byte[] originalNonce = challengeManager.getChallenge(authIdentifier);
                if (originalNonce == null) {
                    sendResponse(exchange, 400, "Challenge abgelaufen oder nicht gefunden.", null);
                    return;
                }

                try {
                    String base64Sig = authHeader.substring("GPGSig ".length());
                    byte[] signatureBytes = Base64.getDecoder().decode(base64Sig);
                    boolean isValid = false;
                    String successEmail = null;

                    // Wir testen durch: Passt die Unterschrift zu irgendeiner unserer bekannten Domains?
                    for (String domain : VALID_DOMAINS) {
                        String candidateEmail = requestedResource + "@" + domain;
                        if (gpgService.verifySignature(originalNonce, signatureBytes, candidateEmail)) {
                            isValid = true;
                            successEmail = candidateEmail;
                            break;
                        }
                    }

                    if (isValid) {
                        // Sicherheitsmaßnahme: Die Challenge wurde benutzt, also weg damit
                        challengeManager.removeChallenge(authIdentifier);
                        // Session erstellen wir speichern nur den Namen, ohne Domain
                        String newSid = sessionManager.createSession(requestedResource);

                        Path filePath = Paths.get(requestedResource);
                        String content = "Login erfolgreich als " + successEmail;
                        if (Files.exists(filePath)) content = new String(Files.readAllBytes(filePath));

                        sendResponse(exchange, 200, content, newSid);
                        return;
                    } else {
                        sendResponse(exchange, 403, "Forbidden: Signatur ungültig.", null);
                        return;
                    }
                } catch (IllegalArgumentException e) {
                    sendResponse(exchange, 400, "Bad Request: Base64 Fehler", null);
                    return;
                }
            }
        }

        // Wenn gar nichts hilft: Wir schicken dem Nutzer eine Aufgabe
        String nonceBase64 = challengeManager.generateChallenge(authIdentifier);
        exchange.getResponseHeaders().add("WWW-Authenticate", "Challenge=" + nonceBase64);
        sendResponse(exchange, 401, "Unauthorized: Challenge angefordert.\n", null);
    }

    private String getSessionCookie(HttpExchange exchange) {
        List<String> cookies = exchange.getRequestHeaders().get("Cookie");
        if (cookies != null) {
            for (String cookieLine : cookies) {
                String[] parts = cookieLine.split(";");
                for (String part : parts) {
                    part = part.trim();
                    if (part.startsWith("ITS25-SID=")) {
                        return part.substring("ITS25-SID=".length());
                    }
                }
            }
        }
        return null;
    }

    private void sendResponse(HttpExchange exchange, int code, String text, String newSessionId) throws IOException {
        if (newSessionId != null) {
            String cookieHeader = String.format("ITS25-SID=%s; Path=/; HttpOnly; SameSite=Strict", newSessionId);
            exchange.getResponseHeaders().add("Set-Cookie", cookieHeader);
        }
        byte[] body = text.getBytes();
        exchange.sendResponseHeaders(code, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
