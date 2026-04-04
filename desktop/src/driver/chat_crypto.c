#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/fs.h>
#include <linux/cdev.h>
#include <linux/device.h>
#include <linux/uaccess.h>
#include <linux/crypto.h>
#include <linux/scatterlist.h>
#include <linux/string.h>
#include <crypto/hash.h>
#include <crypto/skcipher.h>

#include "./include/crypto_module.h"

MODULE_LICENSE("GPL");
MODULE_AUTHOR("ChatApp");
MODULE_DESCRIPTION("Chat Crypto Driver - Kernel 6.x Compatible");

// Device configuration
#define DEVICE_NAME "chat_crypto"
#define CLASS_NAME  "chat_crypto_class"

// Device state (global)
static int dev_major = 0;
static struct class *crypto_class = NULL;
static struct device *crypto_device = NULL;
static struct cdev crypto_cdev;

// SHA1 using modern shash API (kernel 6.x compatible)
static int do_sha1(struct sha1_request *req) {
    struct crypto_shash *tfm;
    struct shash_desc *desc;
    int ret;

    tfm = crypto_alloc_shash("sha1", 0, 0);
    if (IS_ERR(tfm)) {
        printk(KERN_ERR "chat_crypto: Failed to alloc sha1\n");
        return PTR_ERR(tfm);
    }

    // Allocate descriptor with variable size
    desc = kmalloc(sizeof(struct shash_desc) + crypto_shash_descsize(tfm), GFP_KERNEL);
    if (!desc) {
        crypto_free_shash(tfm);
        return -ENOMEM;
    }

    desc->tfm = tfm;
    desc->flags = 0;

    ret = crypto_shash_init(desc);
    if (ret) goto out;

    ret = crypto_shash_update(desc, req->input, req->input_len);
    if (ret) goto out;

    ret = crypto_shash_final(desc, req->digest);

out:
    kfree(desc);
    crypto_free_shash(tfm);
    return ret;
}

// DES using modern skcipher API (kernel 6.x compatible)
static int do_des(struct des_request *req) {
    struct crypto_skcipher *tfm;
    struct skcipher_request *skreq;
    struct scatterlist sg_in, sg_out;
    unsigned char *input_buf = NULL;
    int ret, crypt_len = req->input_len;

    // DES ECB mode requires input length to be multiple of 8 bytes
    if (crypt_len % 8 != 0) {
        crypt_len = ((crypt_len / 8) + 1) * 8;
    }

    // Allocate temp buffer for input (with padding)
    input_buf = kzalloc(crypt_len, GFP_KERNEL);
    if (!input_buf) return -ENOMEM;

    memcpy(input_buf, req->input, req->input_len);

    // Allocate skcipher (modern API)
    tfm = crypto_alloc_skcipher("ecb(des)", 0, 0);
    if (IS_ERR(tfm)) {
        printk(KERN_ERR "chat_crypto: Failed to alloc des cipher\n");
        kfree(input_buf);
        return PTR_ERR(tfm);
    }

    // Set key
    ret = crypto_skcipher_setkey(tfm, req->key, DES_KEY_SIZE);
    if (ret) {
        printk(KERN_ERR "chat_crypto: Failed to set key\n");
        crypto_free_skcipher(tfm);
        kfree(input_buf);
        return ret;
    }

    // Allocate request
    skreq = skcipher_request_alloc(tfm, GFP_KERNEL);
    if (!skreq) {
        crypto_free_skcipher(tfm);
        kfree(input_buf);
        return -ENOMEM;
    }

    // Setup scatter-gather
    sg_init_one(&sg_in, input_buf, crypt_len);
    sg_init_one(&sg_out, req->output, crypt_len);

    skcipher_request_set_crypt(skreq, &sg_in, &sg_out, crypt_len, NULL);

    // Encrypt or Decrypt
    if (req->mode == 0) {
        ret = crypto_skcipher_encrypt(skreq);
    } else {
        ret = crypto_skcipher_decrypt(skreq);
    }

    req->output_len = crypt_len;

    skcipher_request_free(skreq);
    crypto_free_skcipher(tfm);
    kfree(input_buf);
    return ret;
}

// IOCTL handler
static long crypto_ioctl(struct file *file, unsigned int cmd, unsigned long arg) {
    int ret = 0;

    switch (cmd) {
        case CRYPTO_IOCTL_SHA1_HASH: {
            struct sha1_request *req = kzalloc(sizeof(*req), GFP_KERNEL);
            if (!req) return -ENOMEM;

            if (copy_from_user(req, (void __user *)arg, sizeof(*req))) {
                kfree(req);
                return -EFAULT;
            }

            ret = do_sha1(req);

            if (ret == 0 && copy_to_user((void __user *)arg, req, sizeof(*req))) {
                ret = -EFAULT;
            }

            kfree(req);
            break;
        }

        case CRYPTO_IOCTL_DES_CRYPT: {
            struct des_request *req = kzalloc(sizeof(*req), GFP_KERNEL);
            if (!req) return -ENOMEM;

            if (copy_from_user(req, (void __user *)arg, sizeof(*req))) {
                kfree(req);
                return -EFAULT;
            }

            ret = do_des(req);

            if (ret == 0 && copy_to_user((void __user *)arg, req, sizeof(*req))) {
                ret = -EFAULT;
            }

            kfree(req);
            break;
        }

        default:
            return -EINVAL;
    }

    return ret;
}

// File operations
static struct file_operations fops = {
    .owner = THIS_MODULE,
    .unlocked_ioctl = crypto_ioctl,
};

// Module initialization
static int __init crypto_init(void) {
    int ret;
    dev_t dev;

    printk(KERN_INFO "chat_crypto: Initializing crypto driver\n");

    // Allocate device number
    ret = alloc_chrdev_region(&dev, 0, 1, DEVICE_NAME);
    if (ret < 0) {
        printk(KERN_ERR "chat_crypto: Failed to allocate device number\n");
        return ret;
    }

    dev_major = MAJOR(dev);
    printk(KERN_INFO "chat_crypto: Device major number: %d\n", dev_major);

    // Create class - modern API (kernel 6.x)
    crypto_class = class_create(CLASS_NAME);
    if (IS_ERR(crypto_class)) {
        printk(KERN_ERR "chat_crypto: Failed to create class\n");
        unregister_chrdev_region(dev, 1);
        return PTR_ERR(crypto_class);
    }

    // Create device
    crypto_device = device_create(crypto_class, NULL, dev, NULL, DEVICE_NAME);
    if (IS_ERR(crypto_device)) {
        printk(KERN_ERR "chat_crypto: Failed to create device\n");
        class_destroy(crypto_class);
        unregister_chrdev_region(dev, 1);
        return PTR_ERR(crypto_device);
    }

    // Register character device
    cdev_init(&crypto_cdev, &fops);
    ret = cdev_add(&crypto_cdev, dev, 1);
    if (ret < 0) {
        printk(KERN_ERR "chat_crypto: Failed to add cdev\n");
        device_destroy(crypto_class, dev);
        class_destroy(crypto_class);
        unregister_chrdev_region(dev, 1);
        return ret;
    }

    printk(KERN_INFO "chat_crypto: Driver initialized successfully\n");
    return 0;
}

// Module cleanup
static void __exit crypto_exit(void) {
    dev_t dev = MKDEV(dev_major, 0);

    printk(KERN_INFO "chat_crypto: Cleaning up crypto driver\n");

    cdev_del(&crypto_cdev);
    device_destroy(crypto_class, dev);
    class_destroy(crypto_class);
    unregister_chrdev_region(dev, 1);

    printk(KERN_INFO "chat_crypto: Driver unloaded\n");
}

module_init(crypto_init);
module_exit(crypto_exit);
