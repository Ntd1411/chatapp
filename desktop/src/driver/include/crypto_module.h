#ifndef CRYPTO_MODULE_H
#define CRYPTO_MODULE_H

#include <linux/ioctl.h>

#define CRYPTO_DEVICE_FILE "/dev/chat_crypto"

// SHA1 constants
#define SHA1_DIGEST_SIZE  20
#define SHA1_MAX_INPUT    512

// DES constants
#define DES_KEY_SIZE      8
#define DES_MAX_INPUT     512
#define DES_MAX_OUTPUT    512

// Struct cho SHA1
struct sha1_request {
    unsigned char input[SHA1_MAX_INPUT];
    unsigned int  input_len;
    unsigned char digest[SHA1_DIGEST_SIZE];
};

// Struct cho DES
struct des_request {
    unsigned char input[DES_MAX_INPUT];
    unsigned int  input_len;
    unsigned char output[DES_MAX_OUTPUT];
    unsigned int  output_len;
    unsigned char key[DES_KEY_SIZE];
    unsigned int  mode; // 0 = encrypt, 1 = decrypt
};

// IOCTL commands
#define CRYPTO_IOCTL_SHA1_HASH _IOWR('C', 1, struct sha1_request)
#define CRYPTO_IOCTL_DES_CRYPT _IOWR('C', 2, struct des_request)

#endif
