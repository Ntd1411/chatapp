#include <jni.h>
#include <stdio.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <stdlib.h>
#include "../driver/include/crypto_module.h"

#define DEVICE_FILE "/dev/chat_crypto"

// Helper: Convert bytes to hex string
static void bytes_to_hex(unsigned char *bytes, int len, char *hex) {
    for (int i = 0; i < len; i++) {
        sprintf(hex + i * 2, "%02x", bytes[i]);
    }
    hex[len * 2] = '\0';
}

// Helper: Convert hex string to bytes
static int hex_to_bytes(const char *hex, unsigned char *bytes, int max_len) {
    int len = strlen(hex);
    if (len % 2 != 0) return -1;
    
    int bytes_len = len / 2;
    if (bytes_len > max_len) return -1;
    
    for (int i = 0; i < bytes_len; i++) {
        sscanf(hex + i * 2, "%2hhx", &bytes[i]);
    }
    return bytes_len;
}

/**
 * Java_com_chatty_services_CryptoService_sha1Hash
 */
JNIEXPORT jstring JNICALL Java_com_chatty_services_CryptoService_sha1Hash(
    JNIEnv *env, jobject obj, jstring input) {
    
    const char *input_str = (*env)->GetStringUTFChars(env, input, 0);
    struct sha1_request req;
    char hex_digest[SHA1_DIGEST_SIZE * 2 + 1];
    jstring result = NULL;
    
    int fd = open(DEVICE_FILE, O_RDWR);
    if (fd < 0) {
        fprintf(stderr, "Cannot open %s\n", DEVICE_FILE);
        (*env)->ReleaseStringUTFChars(env, input, input_str);
        return NULL;
    }
    
    // Prepare request
    memset(&req, 0, sizeof(req));
    strncpy((char *)req.input, input_str, SHA1_MAX_INPUT - 1);
    req.input_len = strlen((char *)req.input);
    
    // Call kernel module
    if (ioctl(fd, CRYPTO_IOCTL_SHA1_HASH, &req) < 0) {
        perror("SHA1 ioctl failed");
        close(fd);
        (*env)->ReleaseStringUTFChars(env, input, input_str);
        return NULL;
    }
    
    // Convert to hex
    bytes_to_hex(req.digest, SHA1_DIGEST_SIZE, hex_digest);
    result = (*env)->NewStringUTF(env, hex_digest);
    
    close(fd);
    (*env)->ReleaseStringUTFChars(env, input, input_str);
    return result;
}

/**
 * Java_com_chatty_services_CryptoService_desEncrypt
 */
JNIEXPORT jstring JNICALL Java_com_chatty_services_CryptoService_desEncrypt(
    JNIEnv *env, jobject obj, jstring plaintext, jstring key) {
    
    const char *plain_str = (*env)->GetStringUTFChars(env, plaintext, 0);
    const char *key_str = (*env)->GetStringUTFChars(env, key, 0);
    struct des_request req;
    char hex_output[MAX_CRYPTO_DATA * 2 + 1];
    jstring result = NULL;
    
    int fd = open(DEVICE_FILE, O_RDWR);
    if (fd < 0) {
        fprintf(stderr, "Cannot open %s\n", DEVICE_FILE);
        (*env)->ReleaseStringUTFChars(env, plaintext, plain_str);
        (*env)->ReleaseStringUTFChars(env, key, key_str);
        return NULL;
    }
    
    // Prepare request
    memset(&req, 0, sizeof(req));
    strncpy((char *)req.input, plain_str, MAX_CRYPTO_DATA - 1);
    req.input_len = strlen((char *)req.input);
    req.mode = 0; // encrypt
    
    // Copy key (pad with zeros if needed)
    strncpy((char *)req.key, key_str, DES_KEY_SIZE);
    
    // Call kernel module
    if (ioctl(fd, CRYPTO_IOCTL_DES_ENCRYPT, &req) < 0) {
        perror("DES encrypt ioctl failed");
        close(fd);
        (*env)->ReleaseStringUTFChars(env, plaintext, plain_str);
        (*env)->ReleaseStringUTFChars(env, key, key_str);
        return NULL;
    }
    
    // Convert to hex
    bytes_to_hex(req.output, req.output_len, hex_output);
    result = (*env)->NewStringUTF(env, hex_output);
    
    close(fd);
    (*env)->ReleaseStringUTFChars(env, plaintext, plain_str);
    (*env)->ReleaseStringUTFChars(env, key, key_str);
    return result;
}

/**
 * Java_com_chatty_services_CryptoService_desDecrypt
 */
JNIEXPORT jstring JNICALL Java_com_chatty_services_CryptoService_desDecrypt(
    JNIEnv *env, jobject obj, jstring ciphertext, jstring key) {
    
    const char *cipher_str = (*env)->GetStringUTFChars(env, ciphertext, 0);
    const char *key_str = (*env)->GetStringUTFChars(env, key, 0);
    struct des_request req;
    char output_str[MAX_CRYPTO_DATA + 1];
    jstring result = NULL;
    
    int fd = open(DEVICE_FILE, O_RDWR);
    if (fd < 0) {
        fprintf(stderr, "Cannot open %s\n", DEVICE_FILE);
        (*env)->ReleaseStringUTFChars(env, ciphertext, cipher_str);
        (*env)->ReleaseStringUTFChars(env, key, key_str);
        return NULL;
    }
    
    // Prepare request FIRST (before using req fields)
    memset(&req, 0, sizeof(req));
    
    // THEN convert hex to bytes into req.input
    int input_len = hex_to_bytes(cipher_str, req.input, MAX_CRYPTO_DATA);
    if (input_len < 0) {
        fprintf(stderr, "Invalid hex string\n");
        close(fd);
        (*env)->ReleaseStringUTFChars(env, ciphertext, cipher_str);
        (*env)->ReleaseStringUTFChars(env, key, key_str);
        return NULL;
    }
    
    // Set input length and mode
    req.input_len = input_len;
    req.mode = 1; // decrypt
    
    // Copy key
    strncpy((char *)req.key, key_str, DES_KEY_SIZE);
    
    // Call kernel module
    if (ioctl(fd, CRYPTO_IOCTL_DES_DECRYPT, &req) < 0) {
        perror("DES decrypt ioctl failed");
        close(fd);
        (*env)->ReleaseStringUTFChars(env, ciphertext, cipher_str);
        (*env)->ReleaseStringUTFChars(env, key, key_str);
        return NULL;
    }
    
    // Convert to string
    strncpy(output_str, (char *)req.output, req.output_len);
    output_str[req.output_len] = '\0';
    result = (*env)->NewStringUTF(env, output_str);
    
    close(fd);
    (*env)->ReleaseStringUTFChars(env, ciphertext, cipher_str);
    (*env)->ReleaseStringUTFChars(env, key, key_str);
    return result;
}
