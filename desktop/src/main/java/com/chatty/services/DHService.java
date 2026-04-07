package com.chatty.services;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

public class DHService {
    // RFC 3526 2048-bit safe prime and generator (copied from backend config)
    private static final String P_HEX = "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7EDEE386BFB5A899FA5AE9F24117C4B1FE649286651ECE65381FFFFFFFFFFFFFFFF";
    private static final BigInteger P = new BigInteger(P_HEX, 16);
    private static final BigInteger G = BigInteger.valueOf(2);
    
    // NEW: Local storage directory for 'a' backup
    private static final String DH_STORAGE_DIR = System.getProperty("user.home") + File.separator + ".chatapp" + File.separator + "dh";

    // User's secret exponent 'a' (2048-bit random)
    private BigInteger secretExponent;
    private String userId;
    private String username;
    private String currentDHKeyFile;  // NEW: Per-user file path

    // Cache for DES keys: Map<recipientId → DES_key_hex>
    private final Map<String, String> desKeyCache;

    // Dependencies
    private final ApiService apiService;
    private final CryptoService cryptoService;

    public DHService(ApiService apiService, CryptoService cryptoService) {
        this.apiService = apiService;
        this.cryptoService = cryptoService;
        this.desKeyCache = new HashMap<>();
        ensureStorageDirectoryExists();
    }

    /**
     * Ensure DH storage directory exists.
     */
    private void ensureStorageDirectoryExists() {
        File directory = new File(DH_STORAGE_DIR);
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    /**
     * NEW: Set the user ID and create per-user file path.
     * Call this before loading/generating secret exponent.
     */
    public void setUserId(String userId) {
        this.userId = userId;
        // NEW: Per-user file: ~/.chatapp/dh/secret_exponent_<userId>.dat
        this.currentDHKeyFile = DH_STORAGE_DIR + File.separator + "secret_exponent_" + userId + ".dat";
    }

    /**
     * Generate a random 2048-bit secret exponent 'a' at signup.
     * Stores it in memory for this session.
     * WARNING: This is CPU-intensive (modPow on 2048-bit numbers), run on background thread!
     */
    public void generateSecretExponent() {
        long startTime = System.currentTimeMillis();
        if (currentDHKeyFile == null) {
            throw new IllegalStateException("User ID not set. Call setUserId() first.");
        }
        
        System.out.println("[DH] ⚠ Generating new secret exponent for user: " + userId + " (CPU-intensive, ~1-2 seconds)");
        
        SecureRandom random = new SecureRandom();
        int bitLength = P.bitLength();  // 2048 bits
        this.secretExponent = new BigInteger(bitLength, random).mod(P.subtract(BigInteger.ONE));
        if (this.secretExponent.compareTo(BigInteger.ONE) < 0) {
            this.secretExponent = this.secretExponent.add(BigInteger.ONE);
        }
        
        long elapsedTime = System.currentTimeMillis() - startTime;
        System.out.println("[DH] ✓ Secret exponent generated in " + elapsedTime + "ms");
        
        // NEW: Save to local storage immediately
        saveSecretExponentToStorage();
    }

    /**
     * NEW: Try to load an existing secret exponent from local storage.
     * Returns true if loaded successfully, false if no stored exponent found.
     */
    public boolean loadSecretExponentFromStorage() {
        if (currentDHKeyFile == null) {
            throw new IllegalStateException("User ID not set. Call setUserId() first.");
        }
        
        try {
            System.out.println("[DH] Attempting to load secret exponent from: " + currentDHKeyFile);
            
            File keyFile = new File(currentDHKeyFile);
            if (!keyFile.exists()) {
                System.out.println("[DH] File does not exist: " + currentDHKeyFile);
                return false;
            }
            
            String aHex = new String(Files.readAllBytes(Paths.get(currentDHKeyFile)), StandardCharsets.UTF_8).trim();
            if (aHex.isEmpty()) {
                System.out.println("[DH] File is empty");
                return false;
            }
            
            this.secretExponent = new BigInteger(aHex, 16);
            System.out.println("[DH] Loaded existing secret exponent for user: " + userId);
            System.out.println("[DH] Secret exponent is now: " + (secretExponent != null ? "SET" : "NULL"));
            return true;
        } catch (Exception e) {
            System.err.println("[DH] Failed to load secret exponent: " + e.getMessage());
            return false;
        }
    }

    /**
     * NEW: Save the current secret exponent to local storage.
     */
    private void saveSecretExponentToStorage() {
        if (currentDHKeyFile == null) {
            throw new IllegalStateException("User ID not set. Call setUserId() first.");
        }
        
        try {
            ensureStorageDirectoryExists();
            String aHex = secretExponent.toString(16);
            Files.write(Paths.get(currentDHKeyFile), aHex.getBytes(StandardCharsets.UTF_8));
            System.out.println("✓ Saved secret exponent for user: " + userId);
        } catch (Exception e) {
            System.err.println("Failed to save secret exponent: " + e.getMessage());
        }
    }

    /**
     * NEW: Clear the stored secret exponent from local storage.
     * Call this on logout/uninstall.
     * Also clears all cached DES keys from disk.
     */
    public void deleteStoredSecretExponent() {
        if (currentDHKeyFile == null) {
            return;
        }
        
        try {
            File keyFile = new File(currentDHKeyFile);
            if (keyFile.exists()) {
                keyFile.delete();
                System.out.println("✓ Deleted stored secret exponent for user: " + userId);
            }
            
            // NEW: Also clean up all DES keys on logout
            deleteAllDesKeysFromStorage();
            
            // Clear memory caches
            secretExponent = null;
            desKeyCache.clear();
        } catch (Exception e) {
            System.err.println("Failed to delete secret exponent: " + e.getMessage());
        }
    }

    /**
     * Compute public exponent: g^a mod p
     * Called after generateSecretExponent() to get the value to upload to server.
     */
    public BigInteger computePublicExponent() {
        if (secretExponent == null) {
            throw new IllegalStateException("Secret exponent not initialized. Call generateSecretExponent() first.");
        }
        return G.modPow(secretExponent, P);
    }

    /**
     * Upload the computed public exponent (g^a) to the server via API.
     * Called during signup/login flow.
     */
    public void uploadPublicExponent(String userId, String username) {
        this.userId = userId;
        this.username = username;

        if (secretExponent == null) {
            throw new IllegalStateException("Secret exponent not initialized. Call generateSecretExponent() first.");
        }

        BigInteger publicExp = computePublicExponent();
        String publicExpHex = publicExp.toString(16);

        try {
            // POST to backend endpoint: POST /users/dh-key
            // Expects: { dh_public_key: "hex_string" }
            apiService.uploadDHPublicKey(userId, publicExpHex);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload DH public key: " + e.getMessage(), e);
        }
    }

    /**
     * Check if the server already has a public key for this user.
     * Returns true if public key exists on server, false otherwise.
     * This avoids re-uploading the same key on every login.
     */
    public boolean checkPublicKeyExists(String userId) {
        try {
            // GET /dh-key/:userId
            String publicKeyHex = apiService.getDHPublicKey(userId);
            // If we get here without exception, key exists on server
            return publicKeyHex != null && !publicKeyHex.isEmpty();
        } catch (Exception e) {
            // 404 or any other error means key doesn't exist
            System.out.println("[DHService] Public key not found on server: " + e.getMessage());
            return false;
        }
    }

    /**
     * OPTIMIZED: Prepare for message encryption: fetch recipient's g^a, compute shared secret, derive DES key.
     * Called before sending a message to recipientId.
     * NEW: Checks in-memory cache → disk cache → computes and saves to disk
     */
    public String prepareMessageEncryption(String recipientId) {
        long startTime = System.currentTimeMillis();
        
        // 1. Check in-memory cache first
        if (desKeyCache.containsKey(recipientId)) {
            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println("[DH] ✓ DES key found in MEMORY cache for recipient: " + recipientId + " (" + elapsed + "ms)");
            return desKeyCache.get(recipientId);
        }

        // 2. Check disk cache (NEW: OPTIMIZATION)
        long diskLoadStart = System.currentTimeMillis();
        String cachedKey = loadDesKeyFromStorage(recipientId);
        if (cachedKey != null) {
            long diskLoadTime = System.currentTimeMillis() - diskLoadStart;
            long totalTime = System.currentTimeMillis() - startTime;
            System.out.println("[DH] ✓ DES key found in DISK cache for recipient: " + recipientId);
            System.out.println("    └─ Disk load time: " + diskLoadTime + "ms | Total: " + totalTime + "ms");
            desKeyCache.put(recipientId, cachedKey);  // Load back to memory cache
            return cachedKey;
        }

        try {
            System.out.println("[DH] ⚠ Computing DES key for recipient: " + recipientId + " (first time)");
            System.out.println("[DH] Secret exponent initialized: " + (secretExponent != null));
            System.out.println("[DH] Current user ID: " + userId);
            
            // Fetch recipient's dh_public_key from server
            // GET /users/dh-key/:recipientId
            long networkStart = System.currentTimeMillis();
            System.out.println("[DH] Fetching recipient's DH public key from server...");
            String recipientPublicKeyHex = apiService.getDHPublicKey(recipientId);
            long networkTime = System.currentTimeMillis() - networkStart;

            if (recipientPublicKeyHex == null || recipientPublicKeyHex.isEmpty()) {
                throw new RuntimeException("Recipient's DH public key not found on server");
            }

            System.out.println("[DH] Recipient's DH public key fetched: " + recipientPublicKeyHex.substring(0, 8) + "...");
            System.out.println("    └─ Network time: " + networkTime + "ms");
            BigInteger recipientPublicExp = new BigInteger(recipientPublicKeyHex, 16);

            // Compute shared secret: (g^b)^a mod p = g^(a*b) mod p
            long cryptoStart = System.currentTimeMillis();
            System.out.println("[DH] Computing shared secret using modPow...");
            BigInteger sharedSecret = recipientPublicExp.modPow(secretExponent, P);
            long cryptoTime = System.currentTimeMillis() - cryptoStart;
            System.out.println("[DH] Shared secret computed successfully (" + cryptoTime + "ms)");

            // Derive DES key from shared secret
            String desKey = deriveDesKey(sharedSecret, userId, recipientId);
            System.out.println("[DH] DES key derived: " + desKey.substring(0, 8) + "...");

            // Cache it in memory AND save to disk (NEW: OPTIMIZATION)
            desKeyCache.put(recipientId, desKey);
            saveDesKeyToStorage(recipientId, desKey);

            long totalTime = System.currentTimeMillis() - startTime;
            System.out.println("[DH] =====================================================");
            System.out.println("[DH] Key computation summary for: " + recipientId);
            System.out.println("    ├─ Network fetch: " + networkTime + "ms");
            System.out.println("    ├─ Crypto (modPow): " + cryptoTime + "ms");
            System.out.println("    └─ Total time: " + totalTime + "ms");
            System.out.println("[DH] =====================================================");

            return desKey;
        } catch (Exception e) {
            System.err.println("[DH] ERROR: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to prepare message encryption: " + e.getMessage(), e);
        }
    }

    /**
     * OPTIMIZED: Prepare for message decryption: fetch sender's g^a, compute shared secret, derive DES key.
     * Called before decrypting a message from senderId.
     * NEW: Checks in-memory cache → disk cache → computes and saves to disk
     */
    public String prepareMessageDecryption(String senderId) {
        long startTime = System.currentTimeMillis();
        
        // 1. Check in-memory cache first
        if (desKeyCache.containsKey(senderId)) {
            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println("[DH.prepareMessageDecryption] ✓ Key found in MEMORY cache for sender: " + senderId + " (" + elapsed + "ms)");
            return desKeyCache.get(senderId);
        }

        // 2. Check disk cache (NEW: OPTIMIZATION)
        long diskLoadStart = System.currentTimeMillis();
        String cachedKey = loadDesKeyFromStorage(senderId);
        if (cachedKey != null) {
            long diskLoadTime = System.currentTimeMillis() - diskLoadStart;
            long totalTime = System.currentTimeMillis() - startTime;
            System.out.println("[DH.prepareMessageDecryption] ✓ Key found in DISK cache for sender: " + senderId);
            System.out.println("    └─ Disk load time: " + diskLoadTime + "ms | Total: " + totalTime + "ms");
            desKeyCache.put(senderId, cachedKey);  // Load back to memory cache
            return cachedKey;
        }

        try {
            System.out.println("[DH.prepareMessageDecryption] ⚠ Computing DES key for sender: " + senderId + " (first time)");
            
            // Fetch sender's dh_public_key from server
            // GET /users/dh-key/:senderId
            long networkStart = System.currentTimeMillis();
            String senderPublicKeyHex = apiService.getDHPublicKey(senderId);
            long networkTime = System.currentTimeMillis() - networkStart;

            if (senderPublicKeyHex == null || senderPublicKeyHex.isEmpty()) {
                throw new RuntimeException("Sender's DH public key not found on server");
            }

            System.out.println("[DH.prepareMessageDecryption] Fetched sender's public key (" + networkTime + "ms)");
            BigInteger senderPublicExp = new BigInteger(senderPublicKeyHex, 16);

            // Compute shared secret: (g^a_sender)^a_receiver mod p = g^(a_receiver*a_sender) mod p
            long cryptoStart = System.currentTimeMillis();
            BigInteger sharedSecret = senderPublicExp.modPow(secretExponent, P);
            long cryptoTime = System.currentTimeMillis() - cryptoStart;
            System.out.println("[DH.prepareMessageDecryption] Shared secret computed (" + cryptoTime + "ms)");

            // Derive DES key from shared secret
            String desKey = deriveDesKey(sharedSecret, userId, senderId);

            // Cache it in memory AND save to disk (NEW: OPTIMIZATION)
            desKeyCache.put(senderId, desKey);
            saveDesKeyToStorage(senderId, desKey);

            long totalTime = System.currentTimeMillis() - startTime;
            System.out.println("[DH] =====================================================");
            System.out.println("[DH] Key computation summary for decryption: " + senderId);
            System.out.println("    ├─ Network fetch: " + networkTime + "ms");
            System.out.println("    ├─ Crypto (modPow): " + cryptoTime + "ms");
            System.out.println("    └─ Total time: " + totalTime + "ms");
            System.out.println("[DH] =====================================================");

            return desKey;
        } catch (Exception e) {
            System.err.println("[DH.prepareMessageDecryption] ERROR: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to prepare message decryption: " + e.getMessage(), e);
        }
    }

    /**
     * NEW OPTIMIZATION: Save a DES key to disk storage.
     * Creates file: ~/.chatapp/dh/deskey_<userId>_<otherUserId>.dat
     */
    private void saveDesKeyToStorage(String otherUserId, String desKey) {
        if (userId == null) {
            return;  // Silent fail if user ID not set
        }
        
        try {
            ensureStorageDirectoryExists();
            
            // Create canonical filename using sorted user IDs for consistency
            String[] ids = {userId, otherUserId};
            java.util.Arrays.sort(ids);
            String desKeyFile = DH_STORAGE_DIR + File.separator + "deskey_" + ids[0] + "_" + ids[1] + ".dat";
            
            Files.write(Paths.get(desKeyFile), desKey.getBytes(StandardCharsets.UTF_8));
            System.out.println("✓ Saved DES key to disk for conversation with: " + otherUserId);
        } catch (Exception e) {
            System.err.println("[DH] Failed to save DES key to storage: " + e.getMessage());
            // Don't throw exception, just log it
        }
    }

    /**
     * NEW OPTIMIZATION: Load a DES key from disk storage.
     * Reads from: ~/.chatapp/dh/deskey_<userId>_<otherUserId>.dat
     * Returns the key if found, null otherwise.
     */
    private String loadDesKeyFromStorage(String otherUserId) {
        if (userId == null) {
            return null;  // Silent fail if user ID not set
        }
        
        try {
            // Create canonical filename using sorted user IDs for consistency
            String[] ids = {userId, otherUserId};
            java.util.Arrays.sort(ids);
            String desKeyFile = DH_STORAGE_DIR + File.separator + "deskey_" + ids[0] + "_" + ids[1] + ".dat";
            
            File keyFile = new File(desKeyFile);
            if (!keyFile.exists()) {
                System.out.println("[DH] DES key file not found: " + desKeyFile);
                return null;
            }
            
            String desKey = new String(Files.readAllBytes(Paths.get(desKeyFile)), StandardCharsets.UTF_8).trim();
            if (desKey.isEmpty()) {
                System.out.println("[DH] DES key file is empty: " + desKeyFile);
                return null;
            }
            
            System.out.println("[DH] Loaded DES key from storage for: " + otherUserId);
            return desKey;
        } catch (Exception e) {
            System.err.println("[DH] Failed to load DES key from storage: " + e.getMessage());
            return null;  // Return null if loading fails
        }
    }

    /**
     * NEW: Delete DES key from disk storage when needed.
     * Call this when ending a conversation or cleaning up.
     */
    public void deleteDesKeyFromStorage(String otherUserId) {
        if (userId == null) {
            return;
        }
        
        try {
            // Create canonical filename using sorted user IDs for consistency
            String[] ids = {userId, otherUserId};
            java.util.Arrays.sort(ids);
            String desKeyFile = DH_STORAGE_DIR + File.separator + "deskey_" + ids[0] + "_" + ids[1] + ".dat";
            
            File keyFile = new File(desKeyFile);
            if (keyFile.exists()) {
                keyFile.delete();
                System.out.println("✓ Deleted DES key from storage for: " + otherUserId);
            }
        } catch (Exception e) {
            System.err.println("[DH] Failed to delete DES key from storage: " + e.getMessage());
        }
    }

    /**
     * NEW: Delete all DES keys from disk storage (for cleanup/logout).
     */
    public void deleteAllDesKeysFromStorage() {
        if (userId == null) {
            return;
        }
        
        try {
            File dhDir = new File(DH_STORAGE_DIR);
            if (!dhDir.exists()) {
                return;
            }
            
            // Delete all deskey_* files for this user
            File[] files = dhDir.listFiles((dir, name) -> name.startsWith("deskey_") && name.endsWith(".dat"));
            if (files != null) {
                int deletedCount = 0;
                for (File file : files) {
                    // Only delete files related to this user
                    if (file.getName().contains(userId)) {
                        file.delete();
                        deletedCount++;
                    }
                }
                System.out.println("✓ Deleted " + deletedCount + " DES keys from storage");
            }
        } catch (Exception e) {
            System.err.println("[DH] Failed to delete all DES keys: " + e.getMessage());
        }
    }

    /**
     * Derive DES key from shared secret using HMAC-SHA256.
     * Uses BOTH user IDs in canonical (sorted) order to ensure both parties derive the SAME key.
     * DES_key = first 8 bytes (16 hex chars) of HMAC-SHA256(shared_secret, canonical_user_pair)
     */
    private String deriveDesKey(BigInteger sharedSecret, String userId, String otherUserId) {
        long startTime = System.currentTimeMillis();
        
        try {
            // NEW: Use both user IDs in sorted order for CANONICAL representation
            // This ensures both A and B derive the SAME key from the SAME shared secret
            String[] ids = {userId, otherUserId};
            java.util.Arrays.sort(ids);
            String canonicalPair = ids[0] + "|" + ids[1];
            
            System.out.println("[DH.deriveDesKey] Current userId: " + userId);
            System.out.println("[DH.deriveDesKey] Other userId: " + otherUserId);
            System.out.println("[DH.deriveDesKey] Canonical pair: " + canonicalPair);
            
            String sharedSecretHex = sharedSecret.toString(16);
            byte[] sharedSecretBytes = sharedSecretHex.getBytes();
            byte[] canonicalPairBytes = canonicalPair.getBytes();

            // HMAC-SHA256
            long hmacStart = System.currentTimeMillis();
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(sharedSecretBytes, 0, sharedSecretBytes.length, "HmacSHA256");
            hmac.init(secretKey);
            byte[] digestBytes = hmac.doFinal(canonicalPairBytes);
            long hmacTime = System.currentTimeMillis() - hmacStart;

            // Convert first 8 bytes to hex (16 hex characters for DES key)
            StringBuilder desKeyHex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                desKeyHex.append(String.format("%02x", digestBytes[i]));
            }

            String finalKey = desKeyHex.toString();
            long totalTime = System.currentTimeMillis() - startTime;
            
            System.out.println("[DH.deriveDesKey] Generated key: " + finalKey);
            System.out.println("    ├─ HMAC-SHA256: " + hmacTime + "ms");
            System.out.println("    └─ Total: " + totalTime + "ms");
            
            return finalKey;
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive DES key: " + e.getMessage(), e);
        }
    }

    /**
     * Export the user's secret exponent 'a' as hex string for backup.
     * User can save this and import it on a different device.
     */
    public String exportSecretExponent() {
        if (secretExponent == null) {
            throw new IllegalStateException("Secret exponent not initialized");
        }
        return secretExponent.toString(16);
    }

    /**
     * Import a previously exported secret exponent from hex string.
     * Used when logging in on a different device with backed-up 'a'.
     */
    public void importSecretExponent(String aHex) {
        try {
            this.secretExponent = new BigInteger(aHex, 16);
            // Clear caches since we have a different exponent now
            // DES keys computed with old exponent are no longer valid
            desKeyCache.clear();
            deleteAllDesKeysFromStorage();  // Remove invalid stored keys
        } catch (Exception e) {
            throw new RuntimeException("Failed to import secret exponent: " + e.getMessage(), e);
        }
    }

    /**
     * NEW: Clear the DES key cache (e.g., on logout or session reset).
     * Also optionally remove from disk.
     */
    public void clearCache(boolean alsoClearDisk) {
        desKeyCache.clear();
        if (alsoClearDisk) {
            deleteAllDesKeysFromStorage();
        }
    }

    /**
     * Legacy method: Clear only memory cache.
     */
    public void clearCache() {
        desKeyCache.clear();
    }

    // Getters
    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public boolean hasSecretExponent() {
        return secretExponent != null;
    }
}
