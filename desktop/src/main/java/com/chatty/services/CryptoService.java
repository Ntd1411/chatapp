package com.chatty.services;

/**
 * CryptoService - Wrapper để kết nối Java với Kernel Crypto Module
 * 
 * Yêu cầu: Linux kernel module chat_crypto phải được load
 * $ sudo insmod chat_crypto.ko
 */
public class CryptoService {
    
    private static boolean isLoaded = false;
    
    static {
        try {
            // Load JNI library (libcrypto_jni.so trên Linux)
            System.loadLibrary("crypto_jni");
            isLoaded = true;
            System.out.println("CryptoService: JNI library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("CryptoService: Cannot load JNI library");
            System.err.println("Error: " + e.getMessage());
            isLoaded = false;
        }
    }
    
    // Native methods
    
    /**
     * Tính SHA1 hash của text
     * @param input Text cần hash
     * @return Hex string của SHA1 digest (40 ký tự)
     */
    public native String sha1Hash(String input);
    
    /**
     * Mã hóa text bằng DES (ECB mode)
     * @param plaintext Text cần mã hóa
     * @param key 8-byte DES key
     * @return Hex string của ciphertext
     */
    public native String desEncrypt(String plaintext, String key);
    
    /**
     * Giải mã DES ciphertext
     * @param ciphertext Hex string của ciphertext
     * @param key 8-byte DES key
     * @return Plaintext đã giải mã
     */
    public native String desDecrypt(String ciphertext, String key);
    
    /**
     * Kiểm tra kernel module có sẵn không
     */
    public static boolean isAvailable() {
        return isLoaded;
    }
    
    // Hỗ trợ fallback nếu kernel module không có
    
    /**
     * SHA1 fallback (dùng thư viện Java sẵn có)
     */
    public static String sha1HashFallback(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * DES Encrypt fallback (dùng javax.crypto)
     */
    public static String desEncryptFallback(String plaintext, String key) {
        try {
            byte[] keyBytes = new byte[8];
            byte[] tempKey = key.getBytes();
            System.arraycopy(tempKey, 0, keyBytes, 0, Math.min(8, tempKey.length));
            
            javax.crypto.SecretKey secretKey = new javax.crypto.spec.SecretKeySpec(keyBytes, 0, 8, "DES");
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("DES/ECB/PKCS5Padding");
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey);
            
            byte[] encrypted = cipher.doFinal(plaintext.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : encrypted) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * DES Decrypt fallback
     */
    public static String desDecryptFallback(String ciphertext, String key) {
        try {
            byte[] keyBytes = new byte[8];
            byte[] tempKey = key.getBytes();
            System.arraycopy(tempKey, 0, keyBytes, 0, Math.min(8, tempKey.length));
            
            javax.crypto.SecretKey secretKey = new javax.crypto.spec.SecretKeySpec(keyBytes, 0, 8, "DES");
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("DES/ECB/PKCS5Padding");
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey);
            
            byte[] encryptedBytes = new byte[ciphertext.length() / 2];
            for (int i = 0; i < ciphertext.length(); i += 2) {
                encryptedBytes[i / 2] = (byte) Integer.parseInt(ciphertext.substring(i, i + 2), 16);
            }
            
            byte[] decrypted = cipher.doFinal(encryptedBytes);
            return new String(decrypted);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
