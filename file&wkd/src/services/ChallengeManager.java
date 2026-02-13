package services;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChallengeManager {
    private static final ChallengeManager INSTANCE = new ChallengeManager();
    public static ChallengeManager getInstance() { return INSTANCE; }

    private final Map<String, byte[]> challengeStore = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public String generateChallenge(String identifier) {
        byte[] nonce = new byte[64];
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
