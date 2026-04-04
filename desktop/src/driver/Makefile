# Makefile for ChatDriver Kernel Crypto Module
# Build: driver + test app
# Install: insmod
# Clean: remove artifacts
# 
# Compatible with Kernel 6.x (modern Crypto API: shash, skcipher)

KVERSION := $(shell uname -r)
KBUILD_DIR := /lib/modules/$(KVERSION)/build

obj-m := chat_crypto.o
EXTRA_CFLAGS := -I$(PWD)/../include

# Targets
all: chat_crypto.ko chat_crypto_test
	@echo "[OK] All targets built successfully"

chat_crypto.o: chat_crypto_v6.c
	@echo "Compiling crypto module for kernel $(KVERSION)..."
	gcc -c -I$(KBUILD_DIR)/include chat_crypto_v6.c -o chat_crypto.o

chat_crypto.ko: chat_crypto.o
	@echo "Building kernel module for kernel version: $(KVERSION)..."
	make -C $(KBUILD_DIR) M=$(PWD) modules

chat_crypto_test: chat_crypto_test.c
	@echo "Building test application..."
	gcc -o chat_crypto_test chat_crypto_test.c
	@echo "[OK] Test app built"

install: chat_crypto.ko
	@echo "Loading kernel module..."
	sudo insmod ./chat_crypto.ko
	@echo "[OK] Module loaded"
	sudo chmod 666 /dev/chat_crypto

uninstall:
	@echo "Unloading kernel module..."
	sudo rmmod chat_crypto
	@echo "[OK] Module unloaded"

clean:
	@echo "Cleaning artifacts..."
	make -C $(KBUILD_DIR) M=$(PWD) clean
	rm -f chat_crypto_test chat_crypto.o
	@echo "[OK] Clean complete"

.PHONY: all install uninstall clean

