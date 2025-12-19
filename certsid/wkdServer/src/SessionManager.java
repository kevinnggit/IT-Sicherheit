package certsid.wkdServer.src;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class SessionManager {

    // Speicher: SessionID -> UserID
    private static final Map<String, String> sessionStore = new HashMap<>();
    
    private static final String TEAM_NAME = "Team12";
    private static final SecureRandom random = new SecureRandom();

    public String createSession(String userId) {
        try {
            // 1. Random Nonce
            byte[] nonce = new byte[16];
            random.nextBytes(nonce);
            String randomStr = Base64.getEncoder().encodeToString(nonce);

            // 2. Zeitstempel
            String dateStr = Instant.now().toString();

            // 3. Konkatenation
            String input = randomStr + dateStr + userId + TEAM_NAME;

            // 4. Hash (SHA-256)
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());

            // 5. Encode
            String sessionId = Base64.getEncoder().encodeToString(hash);
            
            // Speichern
            sessionStore.put(sessionId, userId);
            System.out.println("[SESSION] Neu: " + userId + " -> " + sessionId.substring(0, 10) + "...");
            
            return sessionId;

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 Error", e);
        }
    }

    public String getUserFromSession(String sessionId) {
        return sessionStore.get(sessionId);
    }
    
    public void invalidateSession(String sessionId) {
        sessionStore.remove(sessionId);
    }
}