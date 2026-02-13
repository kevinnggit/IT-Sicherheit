package services;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class SessionManager {

    // Singleton: Wir brauchen nur einen Chef für die Sessions
    private static final SessionManager INSTANCE = new SessionManager();

    private SessionManager() {}

    public static SessionManager getInstance() {
        return INSTANCE;
    }

    private final Map<String, String> sessionStore = new HashMap<>();
    private final SecureRandom random = new SecureRandom();
    private static final String TEAM_NAME = "Team12";

    public String createSession(String userId) {
        try {
            byte[] nonce = new byte[16];
            random.nextBytes(nonce);
            String input = Base64.getEncoder().encodeToString(nonce) + Instant.now().toString() + userId + TEAM_NAME;

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String sessionId = Base64.getEncoder().encodeToString(digest.digest(input.getBytes()));

            synchronized (sessionStore) {
                sessionStore.put(sessionId, userId);
            }
            System.out.println("[SESSION] Neue Session fr: " + userId);
            return sessionId;

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 Error", e);
        }
    }

    public String getUserFromSession(String sessionId) {
        synchronized (sessionStore) {
            return sessionStore.get(sessionId);
        }
    }

    // Logout / Aufräumen
    public void invalidateSession(String sessionId) {
        synchronized (sessionStore) {
            sessionStore.remove(sessionId);
        }
    }
}
