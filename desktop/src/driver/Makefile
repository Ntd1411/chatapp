# Makefile for ChatDriver Kernel Crypto Module
# Build: driver + test app
# Install: insmod
# Clean: remove artifacts
# 
# WARNING: This kernel module uses deprecated crypto API (crypto_hash_desc, crypto_blkcipher)
# Ubuntu 24 kernel 6.x+ may not support these APIs.
# For production chat app, recommend using userspace TLS (OpenSSL) instead.

KVERSION := $(shell uname -r)
KBUILD_DIR := /lib/modules/$(KVERSION)/build

obj-m := chat_crypto.o
EXTRA_CFLAGS := -I$(PWD)/../include

# Targets
all: chat_crypto.ko chat_crypto_test
	@echo "[✓] All targets built successfully"

chat_crypto.ko:
	@echo "Building kernel module for kernel version: $(KVERSION)..."
	make -C $(KBUILD_DIR) M=$(PWD) modules

chat_crypto_test: chat_crypto_test.c
	@echo "Building test application..."
	gcc -o chat_crypto_test chat_crypto_test.c
	@echo "[✓] Test app built"

install: chat_crypto.ko
	@echo "Loading kernel module..."
	sudo insmod ./chat_crypto.ko
	@echo "[✓] Module loaded"
	sudo chmod 666 /dev/chat_crypto

uninstall:
	@echo "Unloading kernel module..."
	sudo rmmod chat_crypto
	@echo "[✓] Module unloaded"

clean:
	@echo "Cleaning artifacts..."
	make -C $(KBUILD_DIR) M=$(PWD) clean
	rm -f chat_crypto_test
	@echo "[✓] Clean complete"

.PHONY: all install uninstall clean

