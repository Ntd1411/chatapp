#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <unistd.h>

#include "./include/crypto_module.h"

int main(int argc, char *argv[]) {
    int i, fd, ret;
    if (argc < 3) {
        printf("Usage:\n");
        printf("  %s sha1 <text>              - Hash text\n", argv[0]);
        printf("  %s enc <text> <key>         - Encrypt (DES, ECB mode)\n", argv[0]);
        printf("  %s dec <hex> <key>          - Decrypt (DES, ECB mode)\n", argv[0]);
        return 1;
    }

    fd = open(CRYPTO_DEVICE_FILE, O_RDWR);
    if (fd < 0) {
        perror("Cannot open " CRYPTO_DEVICE_FILE);
        return 1;
    }

    // SHA1 HASH
    if (strcmp(argv[1], "sha1") == 0) {
        struct sha1_request req;
        memset(&req, 0, sizeof(req));

        strncpy((char *)req.input, argv[2], SHA1_MAX_INPUT - 1);
        req.input_len = strlen((char *)req.input);

        ret = ioctl(fd, CRYPTO_IOCTL_SHA1_HASH, &req);
        if (ret < 0) {
            perror("SHA1 ioctl failed");
            close(fd);
            return 1;
        }

        printf("%s: ", argv[2]);
        for (i = 0; i < SHA1_DIGEST_SIZE; i++)
            printf("%02x", req.digest[i]);
        printf("\n");
    }
    // DES ENCRYPT
    else if (strcmp(argv[1], "enc") == 0) {
        struct des_request req;
        memset(&req, 0, sizeof(req));

        if (strlen(argv[3]) != DES_KEY_SIZE) {
            printf("Error: Key must be exactly %d bytes\n", DES_KEY_SIZE);
            close(fd);
            return 1;
        }

        strncpy((char *)req.key, argv[3], DES_KEY_SIZE);
        strncpy((char *)req.input, argv[2], MAX_CRYPTO_DATA - 1);
        req.input_len = strlen((char *)req.input);

        // Pad to multiple of 8 bytes
        if (req.input_len % 8 != 0) {
            req.input_len = ((req.input_len / 8) + 1) * 8;
        }

        req.mode = 0;  // 0 = encrypt

        ret = ioctl(fd, CRYPTO_IOCTL_DES_ENCRYPT, &req);
        if (ret < 0) {
            perror("DES encrypt ioctl failed");
            close(fd);
            return 1;
        }

        printf("Encrypted: ");
        for (i = 0; i < req.output_len; i++)
            printf("%02x", req.output[i]);
        printf("\n");
    }
    // DES DECRYPT
    else if (strcmp(argv[1], "dec") == 0) {
        struct des_request req;
        memset(&req, 0, sizeof(req));

        if (strlen(argv[3]) != DES_KEY_SIZE) {
            printf("Error: Key must be exactly %d bytes\n", DES_KEY_SIZE);
            close(fd);
            return 1;
        }

        strncpy((char *)req.key, argv[3], DES_KEY_SIZE);

        // Parse hex string
        int hex_len = strlen(argv[2]) / 2;
        if (hex_len % 8 != 0) {
            printf("Error: Hex data length must be multiple of 8 bytes\n");
            close(fd);
            return 1;
        }

        for (i = 0; i < hex_len; i++) {
            unsigned int byte;
            sscanf(&argv[2][i*2], "%2x", &byte);
            req.input[i] = (unsigned char)byte;
        }
        req.input_len = hex_len;
        req.mode = 1;  // 1 = decrypt

        ret = ioctl(fd, CRYPTO_IOCTL_DES_DECRYPT, &req);
        if (ret < 0) {
            perror("DES decrypt ioctl failed");
            close(fd);
            return 1;
        }

        printf("Decrypted: %.*s\n", (int)req.output_len, req.output);
    }
    else {
        printf("Unknown command: %s\n", argv[1]);
        close(fd);
        return 1;
    }

    close(fd);
    return 0;
}