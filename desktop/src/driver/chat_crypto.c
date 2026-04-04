#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/fs.h>
#include <linux/cdev.h>
#include <linux/device.h>
#include <linux/uaccess.h>
#include <linux/crypto.h>
#include <linux/scatterlist.h>
#include <linux/string.h>

#include "../include/crypto_module.h"

// Device configuration
#define DEVICE_NAME "chat_crypto"
#define CLASS_NAME  "chat_crypto_class"

// Device state (global)
static int dev_major = 0;
static struct class *crypto_class = NULL;
static struct device *crypto_device = NULL;
static struct cdev crypto_cdev;

// Ham bam SHA1
static int do_sha1(struct sha1_request *req) {
    struct crypto_hash *tfm;
    struct hash_desc desc;
    struct scatterlist sg;

    tfm = crypto_alloc_hash("sha1", 0, CRYPTO_ALG_ASYNC);
    if (IS_ERR(tfm)) return PTR_ERR(tfm);

    desc.tfm = tfm;
    desc.flags = 0;

    sg_init_one(&sg, req->input, req->input_len);
    crypto_hash_init(&desc);
    crypto_hash_update(&desc, &sg, req->input_len);
    crypto_hash_final(&desc, req->digest);

    crypto_free_hash(tfm);
    return 0;
}

// Ham ma hoa / giai ma DES (0=encrypt, 1=decrypt)
static int do_des(struct des_request *req) {
    struct crypto_blkcipher *tfm;
    struct blkcipher_desc desc;
    struct scatterlist sg_in, sg_out;
    unsigned char *input_buf = NULL;
    int ret, crypt_len = req->input_len;

    // DES ECB mode requires input length to be multiple of 8 bytes
    if (crypt_len % 8 != 0) {
        crypt_len = ((crypt_len / 8) + 1) * 8;
    }

    // Allocate temp buffer for input (with padding space)
    input_buf = kzalloc(crypt_len, GFP_KERNEL);
    if (!input_buf) return -ENOMEM;

    // Copy user input to temp buffer (rest is zero-padded by kzalloc)
    memcpy(input_buf, req->input, req->input_len);

    tfm = crypto_alloc_blkcipher("ecb(des)", 0, CRYPTO_ALG_ASYNC);
    if (IS_ERR(tfm)) {
        kfree(input_buf);
        return PTR_ERR(tfm);
    }

    ret = crypto_blkcipher_setkey(tfm, req->key, DES_KEY_SIZE);
    if (ret) {
        crypto_free_blkcipher(tfm);
        kfree(input_buf);
        return ret;
    }

    desc.tfm = tfm;
    desc.flags = 0;

    sg_init_one(&sg_in, input_buf, crypt_len);
    sg_init_one(&sg_out, req->output, crypt_len);

    if (req->mode == 0) ret = crypto_blkcipher_encrypt(&desc, &sg_out, &sg_in, crypt_len);
    else                ret = crypto_blkcipher_decrypt(&desc, &sg_out, &sg_in, crypt_len);

    req->output_len = crypt_len;
    crypto_free_blkcipher(tfm);
    kfree(input_buf);
    return ret;
}

// Ham lang nghe su kien tu User-space
static long crypto_ioctl(struct file *file, unsigned int cmd, unsigned long arg) {
    int ret = 0;

    switch (cmd) {
        case CRYPTO_IOCTL_SHA1_HASH: {
            struct sha1_request *req = kzalloc(sizeof(*req), GFP_KERNEL);
            if (!req) return -ENOMEM;

            if (copy_from_user(req, (void *)arg, sizeof(*req))) {
                kfree(req);
                return -EFAULT;
            }

            ret = do_sha1(req);

            if (copy_to_user((void *)arg, req, sizeof(*req))) {
                kfree(req);
                return -EFAULT;
            }

            kfree(req);
            break;
        }
        case CRYPTO_IOCTL_DES_ENCRYPT:
        case CRYPTO_IOCTL_DES_DECRYPT: {
            struct des_request *req = kzalloc(sizeof(*req), GFP_KERNEL);
            if (!req) return -ENOMEM;

            if (copy_from_user(req, (void *)arg, sizeof(*req))) {
                kfree(req);
                return -EFAULT;
            }

            ret = do_des(req);

            if (copy_to_user((void *)arg, req, sizeof(*req))) {
                kfree(req);
                return -EFAULT;
            }

            kfree(req);
            break;
        }
        default:
            return -EINVAL;
    }

    return ret;
}

// Device open handler
static int crypto_open(struct inode *inode, struct file *file) {
    printk(KERN_NOTICE "[KMA] Device opened\n");
    return 0;
}

// Device release handler
static int crypto_release(struct inode *inode, struct file *file) {
    printk(KERN_NOTICE "[KMA] Device closed\n");
    return 0;
}

static const struct file_operations crypto_fops = {
    .owner = THIS_MODULE,
    .open = crypto_open,
    .release = crypto_release,
    .unlocked_ioctl = crypto_ioctl,
};

static int __init crypto_init(void) {
    dev_t dev;
    int ret;

    printk(KERN_NOTICE "[KMA] Initializing crypto driver...\n");

    // Cấp phát device number động
    ret = alloc_chrdev_region(&dev, 0, 1, DEVICE_NAME);
    if (ret < 0) {
        printk(KERN_ERR "[KMA] Failed to allocate device number: %d\n", ret);
        return ret;
    }

    dev_major = MAJOR(dev);
    printk(KERN_NOTICE "[KMA] Device number allocated: MAJOR=%d, MINOR=%d\n", dev_major, MINOR(dev));

    // Khởi tạo character device
    cdev_init(&crypto_cdev, &crypto_fops);
    crypto_cdev.owner = THIS_MODULE;
    
    ret = cdev_add(&crypto_cdev, dev, 1);
    if (ret < 0) {
        printk(KERN_ERR "[KMA] Failed to add character device: %d\n", ret);
        unregister_chrdev_region(dev, 1);
        return ret;
    }
    printk(KERN_NOTICE "[KMA] Character device registered\n");

    // Tạo device class
    crypto_class = class_create(THIS_MODULE, CLASS_NAME);
    if (IS_ERR(crypto_class)) {
        printk(KERN_ERR "[KMA] Failed to create device class\n");
        cdev_del(&crypto_cdev);
        unregister_chrdev_region(dev, 1);
        return PTR_ERR(crypto_class);
    }
    printk(KERN_NOTICE "[KMA] Device class created: %s\n", CLASS_NAME);

    // Tạo device node
    crypto_device = device_create(crypto_class, NULL, dev, NULL, DEVICE_NAME);
    if (IS_ERR(crypto_device)) {
        printk(KERN_ERR "[KMA] Failed to create device node\n");
        class_destroy(crypto_class);
        cdev_del(&crypto_cdev);
        unregister_chrdev_region(dev, 1);
        return PTR_ERR(crypto_device);
    }
    printk(KERN_NOTICE "[KMA] Device node created: /dev/%s\n", DEVICE_NAME);
    printk(KERN_NOTICE "[KMA] ===== Crypto driver initialized successfully! =====\n");
    printk(KERN_NOTICE "[KMA] Device: /dev/%s (Major: %d)\n", DEVICE_NAME, dev_major);
    
    return 0;
}

static void __exit crypto_exit(void) {
    dev_t dev = MKDEV(dev_major, 0);

    printk(KERN_NOTICE "[KMA] Cleaning up crypto driver...\n");
    
    // Xóa device node
    device_destroy(crypto_class, dev);
    printk(KERN_NOTICE "[KMA] Device node destroyed\n");
    
    // Xóa device class
    class_destroy(crypto_class);
    printk(KERN_NOTICE "[KMA] Device class destroyed\n");
    
    // Xóa character device
    cdev_del(&crypto_cdev);
    printk(KERN_NOTICE "[KMA] Character device removed\n");
    
    // Unregister device number
    unregister_chrdev_region(dev, 1);
    printk(KERN_NOTICE "[KMA] Device number unregistered: MAJOR=%d\n", dev_major);
    printk(KERN_NOTICE "[KMA] Crypto driver unloaded\n");
}

module_init(crypto_init);
module_exit(crypto_exit);
MODULE_LICENSE("GPL");
MODULE_AUTHOR("KMA Chatty Team");
MODULE_DESCRIPTION("KMA Crypto Driver - SHA1 and DES via kernel crypto API");
MODULE_VERSION("2.0");