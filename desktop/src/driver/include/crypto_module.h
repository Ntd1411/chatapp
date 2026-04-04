#ifndef CRYPTO_MODULE_H
#define CRYPTO_MODULE_H

#include <linux/ioctl.h>

/* Device file */
#define CRYPTO_DEVICE_FILE "/dev/chat_crypto"

/* Constants */
#define SHA1_DIGEST_SIZE 20
#define SHA1_MAX_INPUT 4096
#define DES_KEY_SIZE 8
#define MAX_CRYPTO_DATA 4096

/* Error codes */
#define CRYPTO_OK 0
#define CRYPTO_ERR_NO_DEVICE -1
#define CRYPTO_ERR_INPUT -2
#define CRYPTO_ERR_IOCTL -3

/* IOCTL command codes */
#define CRYPTO_IOCTL_MAGIC 0xC0
#define CRYPTO_IOCTL_SHA1_HASH _IOWR(CRYPTO_IOCTL_MAGIC, 1, struct sha1_request)
#define CRYPTO_IOCTL_DES_ENCRYPT _IOWR(CRYPTO_IOCTL_MAGIC, 2, struct des_request)
#define CRYPTO_IOCTL_DES_DECRYPT _IOWR(CRYPTO_IOCTL_MAGIC, 3, struct des_request)

/* Request structures for IOCTL - userspace compatible */
struct sha1_request {
    unsigned char input[SHA1_MAX_INPUT];
    size_t input_len;
    unsigned char digest[SHA1_DIGEST_SIZE];
};

struct des_request {
    unsigned char key[DES_KEY_SIZE];
    unsigned char input[MAX_CRYPTO_DATA];
    size_t input_len;
    unsigned char output[MAX_CRYPTO_DATA];
    size_t output_len;
    int mode;  /* 0 = encrypt, 1 = decrypt */
};

#endif /* CRYPTO_MODULE_H */
