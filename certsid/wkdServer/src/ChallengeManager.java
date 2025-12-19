package certsid.wkdServer.src;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChallengeManager {

    // Speicher: "IP:User" -> Nonce
    private static final Map<String, byte[]> challengeStore = new ConcurrentHashMap<>();
    private static final SecureRandom random = new SecureRandom();

    public String generateChallenge(String identifier) {
        byte[] nonce = new byte[64]; // 512 Bit
        random.nextBytes(nonce);
        
        challengeStore.put(identifier, nonce);
        
        return Base64.getEncoder().encodeToString(nonce);
    }

    public byte[] getChallenge(String identifier) {
        return challengeStore.get(identifier);
    }

    public void removeChallenge(String identifier) {
        challengeStore.remove(identifier);
    }
}
