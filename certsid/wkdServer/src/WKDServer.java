package certsid.wkdServer.src;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Base64;

/**
 * ============================================================================================
 * HAUPTKLASSE: WKDServer
 * ============================================================================================
 * 
 * ZWECK:
 * Diese Klasse implementiert einen HTTP-Server mit zwei Hauptfunktionen:
 * 1. Web Key Directory (WKD) Server - RFC-konformer Server zur Auslieferung öffentlicher PGP-Schlüssel
 * 2. Geschützter Bereich (/labornutzer/) - Authentifizierung via Challenge-Response mit PGP-Signaturen
 * 
 * FUNKTIONALITÄTEN:
 * • HTTP-Server auf Port 8000
 * • WKD-Endpunkt: /well-known/openpgpkey/ (öffentlicher Schlüssel-Abruf)
 * • Protected-Endpunkt: /labornutzer/ (geschützte Ressourcen mit ACL)
 * • Cookie-basiertes Session-Management (ITS25-SID)
 * • Challenge-Response-Authentifizierung mit PGP-Signaturen
 * • Access Control List (ACL): Owner-basiert und Admin-basiert
 * 
 * VERKNÜPFUNGEN:
 * - Nutzt GpgService für GPG-Operationen (Key-Export, Signatur-Verifikation)
 * - Nutzt SessionManager für Session-Verwaltung (erstellen, validieren, löschen)
 * - Nutzt ChallengeManager für Challenge-Nonce-Verwaltung (generieren, abrufen, löschen)
 * 
 * WER RUFT AUF:
 * - JVM via main-Methode (Kommandozeile: java WKDServer)
 * - HttpServer ruft Handler-Klassen bei eingehenden Requests auf
 * 
 * INNERE KLASSEN:
 * - WKDHandler: Bearbeitet WKD-Anfragen (Schlüssel-Auslieferung)
 * - ProtectedHandler: Bearbeitet Anfragen an geschützte Ressourcen (Challenge-Response-Auth + ACL)
 * 
 * SICHERHEITSASPEKTE:
 * - VALID_DOMAINS: Nur Hochschul-Domains erlaubt (verhindert Cross-Team Login)
 * - Challenge-Response: Kein Passwort über Netzwerk, nur kryptographische Signaturen
 * - Session-Cookies: HttpOnly, SameSite=Strict für XSS/CSRF-Schutz
 * - ACL: Owner kann nur eigene Dateien sehen, Admin (lars.fischer) kann alle sehen
 * ============================================================================================
 */
public class WKDServer {
    // ========================================================================================
    // KONFIGURATIONSKONSTANTEN
    // ========================================================================================
    
    /**
     * SERVER-PORT: Port auf dem der HTTP-Server läuft
     * Standard: 8000 (nicht-privilegierter Port, kein Root-Zugriff nötig)
     */
    private static final int PORT = 8000;

    /**
     * ERLAUBTE DOMAINS FÜR LOGIN-VERSUCHE
     * 
     * Nur offizielle Hochschul-E-Mail-Domains sind erlaubt.
     * Team-Domains (z.B. team-12.example.org) sind NICHT erlaubt.
     * 
     * ZWECK: Verhindert Cross-Team Login-Versuche
     * - User können sich nur mit ihrer Hochschul-E-Mail authentifizieren
     * - Team-Domains werden nur für WKD-Anfragen verwendet, nicht für Login
     * 
     * VERWENDUNG: In ProtectedHandler.handle() beim Signatur-Verifizieren
     */
    private static final String[] VALID_DOMAINS = {
        "smail.hs-bremerhaven.de",     // Studenten-E-Mail
        "student.hs-bremerhaven.de",   // Alternative Studenten-E-Mail
        "hs-bremerhaven.de"            // Mitarbeiter-E-Mail
    };

    // ========================================================================================
    // ENDPUNKT-PFADE
    // ========================================================================================
    
    /**
     * WKD-KONTEXT: URL-Präfix für Web Key Directory Anfragen
     * Format: /well-known/openpgpkey/{domain}/hu/{hash}?l={localpart}
     * RFC: draft-koch-openpgp-webkey-service
     */
    private static final String WKD_CONTEXT = "/well-known/openpgpkey/";
    
    /**
     * LABOR-KONTEXT: URL-Präfix für geschützte Ressourcen
     * Format: /labornutzer/{username}
     * Beispiel: /labornutzer/lars.fischer
     */
    private static final String LABOR_CONTEXT = "/labornutzer/";

    // ========================================================================================
    // SERVICE-INSTANZEN (Singleton-Pattern)
    // ========================================================================================
    
    /**
     * GPG-SERVICE: Wrapper für GPG-Kommandozeilen-Operationen
     * - getPublicKey(email): Exportiert Public Key aus GPG-Keyring
     * - verifySignature(nonce, sig): Verifiziert PGP-Signatur
     */
    private static final GpgService gpgService = new GpgService();
    
    /**
     * SESSION-MANAGER: Verwaltung von Benutzersessions
     * - createSession(userId): Erstellt neue Session mit kryptographischer ID
     * - getUserFromSession(sessionId): Validiert Session, gibt User-ID zurück
     * - invalidateSession(sessionId): Löscht Session (Logout)
     */
    public static final SessionManager sessionManager = new SessionManager();
    
    /**
     * CHALLENGE-MANAGER: Verwaltung von Challenge-Nonces
     * - generateChallenge(identifier): Erzeugt 512-Bit SecureRandom Nonce
     * - getChallenge(identifier): Holt gespeicherte Challenge
     * - removeChallenge(identifier): Löscht Challenge (Replay-Schutz)
     */
    public static final ChallengeManager challengeManager = new ChallengeManager();

    /**
     * ========================================================================================
     * MAIN-METHODE: Einstiegspunkt des Servers
     * ========================================================================================
     * 
     * ABLAUF:
     * 1. Erstelle HttpServer-Instanz auf PORT 8000
     * 2. Registriere WKDHandler für /well-known/openpgpkey/
     * 3. Registriere ProtectedHandler für /labornutzer/
     * 4. Starte Server
     * 
     * FEHLERBEHANDLUNG:
     * - IOException wird geworfen bei Port-Konflikten oder Netzwerkproblemen
     */
    public static void main (String[] args) throws IOException {
        // HTTP-SERVER ERSTELLEN
        // InetSocketAddress: Bindet Server an alle Netzwerk-Interfaces (0.0.0.0) auf PORT 8000
        // Backlog 0: Standard-Warteschlangengröße für eingehende Verbindungen
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // HANDLER REGISTRIEREN
        // Jeder Context ist ein URL-Präfix, der an einen HttpHandler gebunden wird
        server.createContext(WKD_CONTEXT, new WKDHandler());      // /well-known/openpgpkey/*
        server.createContext(LABOR_CONTEXT, new ProtectedHandler()); // /labornutzer/*

        // EXECUTOR KONFIGURIEREN
        // null = Standard-Executor (ein Thread pro Request, einfach aber ausreichend)
        server.setExecutor(null); 
        
        // SERVER STARTEN
        System.out.println("WKD Server läuft auf Port " + PORT);
        server.start(); // Nicht-blockierend, Server läuft in eigenem Thread
    }

    // ========================================================================================
    // HILFSMETHODEN
    // ========================================================================================

    private static String getSessionCookie(HttpExchange exchange) {
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

    private static void sendResponse(HttpExchange exchange, int code, String text, String newSessionId) throws IOException {
        // Setze HTTP-Konformen Cookie Header
        if (newSessionId != null) {
            // HttpOnly = JS Schutz, SameSite = CSRF Schutz.
            // Secure fehlt, da localhost kein HTTPS hat.
            String cookieHeader = String.format("ITS25-SID=%s; Path=/; HttpOnly; SameSite=Strict", newSessionId);
            exchange.getResponseHeaders().add("Set-Cookie", cookieHeader);
        }

        byte[] body = text.getBytes();
        exchange.sendResponseHeaders(code, body.length);
        OutputStream os = exchange.getResponseBody();
        os.write(body);
        os.close();
    }

    // --- Handler ---

    static class WKDHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed", null);
                return;
            }

            URI uri = exchange.getRequestURI();
            String path = uri.getPath();
            
            if (path.length() <= WKD_CONTEXT.length()) {
                 sendResponse(exchange, 404, "Not Found", null);
                 return;
            }

            String subPath = path.substring(WKD_CONTEXT.length());
            String[] parts = subPath.split("/");

            if (parts.length != 3 || !"hu".equals(parts[1])) {
                sendResponse(exchange, 400, "Bad Request", null);
                return;
            }
            
            String localPart = "dummy"; 
            if (exchange.getRequestURI().getQuery() != null && exchange.getRequestURI().getQuery().contains("l=")) {
                 String[] queryParts = exchange.getRequestURI().getQuery().split("=");
                 if (queryParts.length > 1) {
                     localPart = queryParts[1];
                 }
            }
            
            String email = localPart + "@" + parts[0];
            System.out.println("[WKD] Anfrage für: " + email);
            byte[] keyData = gpgService.getPublicKey(email); 
            
            if (keyData != null) {
                exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                exchange.sendResponseHeaders(200, keyData.length);
                OutputStream os = exchange.getResponseBody();
                os.write(keyData);
                os.close();
            } else {
                sendResponse(exchange, 404, "Not Found", null);
            }
        }
    }

    static class ProtectedHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            
            // 1. Ressource parsen
            String path = exchange.getRequestURI().getPath();
            if (!path.startsWith(LABOR_CONTEXT)) {
                sendResponse(exchange, 404, "Not Found", null);
                return;
            }
            String requestedResource = path.substring(LABOR_CONTEXT.length());
            if (requestedResource.contains("?")) requestedResource = requestedResource.split("\\?")[0];

            if (requestedResource.isEmpty() || requestedResource.contains("/")) {
                sendResponse(exchange, 400, "Bad Request", null);
                return;
            }

            String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
            String authIdentifier = clientIp + ":" + requestedResource;

            // 2. Session Check
            String sessionId = getSessionCookie(exchange);
            String sessionUser = null;
            if (sessionId != null) {
                sessionUser = WKDServer.sessionManager.getUserFromSession(sessionId);
            }

            // 3. Wenn eingeloggt: ACL (Access Control List)
            if (sessionUser != null) {
                System.out.println("[ACL] User '" + sessionUser + "' greift zu auf '" + requestedResource + "'");
                
                boolean isOwner = sessionUser.equals(requestedResource);
                // Hier ist der Admin-Check. Aber man wird nur Admin, wenn man Modul 4 (Auth) besteht!
                boolean isAdmin = "lars.fischer".equals(sessionUser);

                if (isOwner || isAdmin) {
                    Path filePath = Paths.get(requestedResource);
                    if (Files.exists(filePath)) {
                        String content = new String(Files.readAllBytes(filePath));
                        sendResponse(exchange, 200, content, null);
                    } else {
                        sendResponse(exchange, 404, "Datei nicht gefunden.", null);
                    }
                } else {
                    sendResponse(exchange, 403, "Forbidden: ACL Verweigert.", null);
                }
                return;
            }

            // 4. Auth Header prüfen (Login-Versuch)
            List<String> authHeaders = exchange.getRequestHeaders().get("Authorization");
            if (authHeaders != null && !authHeaders.isEmpty()) {
                String authHeader = authHeaders.get(0);
                if (authHeader.startsWith("GPGSig ")) {
                    String base64Sig = authHeader.substring("GPGSig ".length());
                    
                    byte[] originalNonce = WKDServer.challengeManager.getChallenge(authIdentifier);
                    if (originalNonce == null) {
                        sendResponse(exchange, 400, "Challenge abgelaufen/nicht gefunden.", null);
                        return;
                    }

                    try {
                        byte[] signatureBytes = Base64.getDecoder().decode(base64Sig);
                        boolean isValid = false;
                        String successEmail = null;

                        // --- SICHERHEITS-SCHLEIFE ---
                        // Wir testen alle erlaubten Domains.
                        // Ein Angriff auf "lars.fischer" wird scheitern, weil wir explizit
                        // gegen den Key von "lars.fischer@hs-bremerhaven.de" prüfen.
                        // Hat der Angreifer diesen Private Key nicht, schlägt verify fehl.
                        for (String domain : VALID_DOMAINS) {
                            String candidateEmail = requestedResource + "@" + domain;
                            
                            if (gpgService.verifySignature(originalNonce, signatureBytes, candidateEmail)) {
                                System.out.println("[AUTH] Erfolg! User: " + candidateEmail);
                                isValid = true;
                                successEmail = candidateEmail;
                                break;
                            }
                        }

                        if (isValid) {
                            WKDServer.challengeManager.removeChallenge(authIdentifier);
                            String newSid = WKDServer.sessionManager.createSession(requestedResource);
                            
                            Path filePath = Paths.get(requestedResource);
                            String content = "Login erfolgreich als " + successEmail;
                            if (Files.exists(filePath)) content = new String(Files.readAllBytes(filePath));
                            
                            sendResponse(exchange, 200, content, newSid);
                            return;
                        } else {
                            System.out.println("[AUTH] Fehlgeschlagen für " + requestedResource);
                            sendResponse(exchange, 403, "Forbidden: Signatur ungültig.", null);
                            return;
                        }
                    } catch (IllegalArgumentException e) {
                        sendResponse(exchange, 400, "Bad Request: Base64 Error", null);
                        return;
                    }
                }
            }

            // 5. Fallback: Neue Challenge senden
            String nonceBase64 = WKDServer.challengeManager.generateChallenge(authIdentifier);
            exchange.getResponseHeaders().add("WWW-Authenticate", "Challenge=" + nonceBase64);
            sendResponse(exchange, 401, "Unauthorized: Challenge erforderlich.\n", null);
        }
    }
}