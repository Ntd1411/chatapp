package com.chatty.services;

/**
 * Example usage of CryptoService in the chat application
 * 
 * This demonstrates how to use the kernel crypto module for:
 * - Message encryption
 * - User password hashing
 * - Data integrity verification
 */
public class CryptoUsageExample {
    
    private CryptoService crypto;
    
    public CryptoUsageExample() {
        this.crypto = new CryptoService();
        System.out.println("Kernel crypto driver initialized");
    }
    
    /**
     * Hash a user password using SHA1 (kernel driver)
     */
    public String hashPassword(String password) {
        return crypto.sha1Hash(password);
    }
    
    /**
     * Encrypt message content using DES (kernel driver)
     */
    public String encryptMessage(String message, String encryptionKey) {
        return crypto.desEncrypt(message, encryptionKey);
    }
    
    /**
     * Decrypt message content using DES (kernel driver)
     */
    public String decryptMessage(String encryptedMessage, String decryptionKey) {
        return crypto.desDecrypt(encryptedMessage, decryptionKey);
    }
    
    /**
     * Example: Verify message integrity with SHA1
     */
    public boolean verifyMessageIntegrity(String message, String expectedHash) {
        String actualHash = hashPassword(message);
        return actualHash != null && actualHash.equals(expectedHash);
    }
    
    /**
     * Demo usage
     */
    public static void main(String[] args) {
        CryptoUsageExample example = new CryptoUsageExample();
        
        // Example 1: Hash a password
        String password = "user123";
        String hashedPassword = example.hashPassword(password);
        System.out.println("Password: " + password);
        System.out.println("Hash: " + hashedPassword);
        System.out.println();
        
        // Example 2: Encrypt a message
        String message = "Secret message chào bạn";
        String key = "12345678";  // 8-character key for DES
        String encrypted = example.encryptMessage(message, key);
        System.out.println("Message: " + message);
        System.out.println("Key: " + key);
        System.out.println("Encrypted: " + encrypted);
        System.out.println();
        
        // Example 3: Decrypt the message
        if (encrypted != null) {
            String decrypted = example.decryptMessage(encrypted, key);
            System.out.println("Decrypted: " + decrypted);
            System.out.println();
        }
        
        // Example 4: Verify integrity
        String messageToVerify = "Important data";
        String hash = example.hashPassword(messageToVerify);
        boolean isValid = example.verifyMessageIntegrity(messageToVerify, hash);
        System.out.println("Message: " + messageToVerify);
        System.out.println("Hash: " + hash);
        System.out.println("Verified: " + isValid);
    }
}
