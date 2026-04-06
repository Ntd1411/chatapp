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
     */
    public void generateSecretExponent() {
        if (currentDHKeyFile == null) {
            throw new IllegalStateException("User ID not set. Call setUserId() first.");
        }
        
        System.out.println("[DH] Generating new secret exponent for user: " + userId);
        
        SecureRandom random = new SecureRandom();
        int bitLength = P.bitLength();  // 2048 bits
        this.secretExponent = new BigInteger(bitLength, random).mod(P.subtract(BigInteger.ONE));
        if (this.secretExponent.compareTo(BigInteger.ONE) < 0) {
            this.secretExponent = this.secretExponent.add(BigInteger.ONE);
        }
        
        System.out.println("[DH] Secret exponent generated, is null: " + (secretExponent == null));
        
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
     * Prepare for message encryption: fetch recipient's g^a, compute shared secret, derive DES key.
     * Called before sending a message to recipientId.
     */
    public String prepareMessageEncryption(String recipientId) {
        // Check cache first
        if (desKeyCache.containsKey(recipientId)) {
            System.out.println("[DH] DES key found in cache for recipient: " + recipientId);
            return desKeyCache.get(recipientId);
        }

        try {
            System.out.println("[DH] Preparing message encryption for recipient: " + recipientId);
            System.out.println("[DH] Secret exponent initialized: " + (secretExponent != null));
            System.out.println("[DH] Current user ID: " + userId);
            
            // Fetch recipient's dh_public_key from server
            // GET /users/dh-key/:recipientId
            System.out.println("[DH] Fetching recipient's DH public key...");
            String recipientPublicKeyHex = apiService.getDHPublicKey(recipientId);

            if (recipientPublicKeyHex == null || recipientPublicKeyHex.isEmpty()) {
                throw new RuntimeException("Recipient's DH public key not found on server");
            }

            System.out.println("[DH] Recipient's DH public key fetched: " + recipientPublicKeyHex.substring(0, 8) + "...");
            BigInteger recipientPublicExp = new BigInteger(recipientPublicKeyHex, 16);

            // Compute shared secret: (g^b)^a mod p = g^(a*b) mod p
            System.out.println("[DH] Computing shared secret using modPow...");
            BigInteger sharedSecret = recipientPublicExp.modPow(secretExponent, P);
            System.out.println("[DH] Shared secret computed successfully");

            // Derive DES key from shared secret
            String desKey = deriveDesKey(sharedSecret, recipientId);
            System.out.println("[DH] DES key derived: " + desKey.substring(0, 8) + "...");

            // Cache it
            desKeyCache.put(recipientId, desKey);

            return desKey;
        } catch (Exception e) {
            System.err.println("[DH] ERROR: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to prepare message encryption: " + e.getMessage(), e);
        }
    }

    /**
     * Prepare for message decryption: fetch sender's g^a, compute shared secret, derive DES key.
     * Called before decrypting a message from senderId.
     */
    public String prepareMessageDecryption(String senderId) {
        // Check cache first
        if (desKeyCache.containsKey(senderId)) {
            return desKeyCache.get(senderId);
        }

        try {
            // Fetch sender's dh_public_key from server
            // GET /users/dh-key/:senderId
            String senderPublicKeyHex = apiService.getDHPublicKey(senderId);

            if (senderPublicKeyHex == null || senderPublicKeyHex.isEmpty()) {
                throw new RuntimeException("Sender's DH public key not found on server");
            }

            BigInteger senderPublicExp = new BigInteger(senderPublicKeyHex, 16);

            // Compute shared secret: (g^a_sender)^a_receiver mod p = g^(a_receiver*a_sender) mod p
            BigInteger sharedSecret = senderPublicExp.modPow(secretExponent, P);

            // Derive DES key from shared secret
            String desKey = deriveDesKey(sharedSecret, senderId);

            // Cache it
            desKeyCache.put(senderId, desKey);

            return desKey;
        } catch (Exception e) {
            throw new RuntimeException("Failed to prepare message decryption: " + e.getMessage(), e);
        }
    }

    /**
     * Derive DES key from shared secret using HMAC-SHA256.
     * DES_key = first 8 bytes (16 hex chars) of HMAC-SHA256(shared_secret, other_user_id)
     */
    private String deriveDesKey(BigInteger sharedSecret, String otherUserId) {
        try {
            String sharedSecretHex = sharedSecret.toString(16);
            byte[] sharedSecretBytes = sharedSecretHex.getBytes();
            byte[] otherUserIdBytes = otherUserId.getBytes();

            // HMAC-SHA256
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(sharedSecretBytes, 0, sharedSecretBytes.length, "HmacSHA256");
            hmac.init(secretKey);
            byte[] digestBytes = hmac.doFinal(otherUserIdBytes);

            // Convert first 8 bytes to hex (16 hex characters for DES key)
            StringBuilder desKeyHex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                desKeyHex.append(String.format("%02x", digestBytes[i]));
            }

            return desKeyHex.toString();
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
            // Clear the cache since we may have a different exponent now
            desKeyCache.clear();
        } catch (Exception e) {
            throw new RuntimeException("Failed to import secret exponent: " + e.getMessage(), e);
        }
    }

    /**
     * Clear the DES key cache (e.g., on logout or session reset).
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
