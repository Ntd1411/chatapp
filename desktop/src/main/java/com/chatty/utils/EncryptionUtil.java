package com.chatty.utils;

import com.chatty.services.CryptoService;

/**
 * EncryptionUtil - Utility class for message/data encryption in chat app
 * 
 * Cung cấp các method để mã hóa/giải mã tin nhắn và hash password
 */
public class EncryptionUtil {
    
    private static final String DEFAULT_ENCRYPTION_KEY = "chatapp123";  // Nên đặt vào .env
    private static CryptoService crypto;
    
    static {
        crypto = new CryptoService();
    }
    
    /**
     * Hash password using SHA1
     */
    public static String hashPassword(String password) {
        if (CryptoService.isAvailable()) {
            return crypto.sha1Hash(password);
        } else {
            return CryptoService.sha1HashFallback(password);
        }
    }
    
    /**
     * Encrypt message content
     */
    public static String encryptMessage(String plaintext) {
        return encryptMessage(plaintext, DEFAULT_ENCRYPTION_KEY);
    }
    
    public static String encryptMessage(String plaintext, String key) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        
        if (CryptoService.isAvailable()) {
            String encrypted = crypto.desEncrypt(plaintext, key);
            return encrypted != null ? encrypted : plaintext;
        } else {
            String encrypted = CryptoService.desEncryptFallback(plaintext, key);
            return encrypted != null ? encrypted : plaintext;
        }
    }
    
    /**
     * Decrypt message content
     */
    public static String decryptMessage(String ciphertext) {
        return decryptMessage(ciphertext, DEFAULT_ENCRYPTION_KEY);
    }
    
    public static String decryptMessage(String ciphertext, String key) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }
        
        try {
            if (CryptoService.isAvailable()) {
                String decrypted = crypto.desDecrypt(ciphertext, key);
                return decrypted != null ? decrypted : ciphertext;
            } else {
                String decrypted = CryptoService.desDecryptFallback(ciphertext, key);
                return decrypted != null ? decrypted : ciphertext;
            }
        } catch (Exception e) {
            System.err.println("Decryption failed: " + e.getMessage());
            return ciphertext;  // Return original if decryption fails
        }
    }
    
    /**
     * Verify message integrity
     */
    public static boolean verifyMessageHash(String message, String expectedHash) {
        String actualHash = hashPassword(message);
        return actualHash != null && actualHash.equals(expectedHash);
    }
}
