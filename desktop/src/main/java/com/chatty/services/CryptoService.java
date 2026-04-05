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
            System.err.println("CryptoService: Cannot load JNI library!");
            System.err.println("Error: " + e.getMessage());
            System.err.println("REQUIRED: Must compile and install kernel driver first!");
            System.err.println("Steps:");
            System.err.println("  1. cd ~/chatapp/desktop/src/driver");
            System.err.println("  2. make clean && make && sudo make install");
            System.err.println("  3. Compile JNI: gcc ... -o libcrypto_jni.so crypto_jni.c");
            System.err.println("  4. Copy: cp libcrypto_jni.so /usr/lib/x86_64-linux-gnu/");
            isLoaded = false;
            throw new ExceptionInInitializerError("Kernel crypto driver required but not available!");
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
    
    /** 
     * The CryptoService requires the Linux kernel module (chat_crypto.ko)
     * and native JNI library (libcrypto_jni.so) to function.
     * 
     * All crypto operations MUST use the kernel driver through native methods.
     */
}
