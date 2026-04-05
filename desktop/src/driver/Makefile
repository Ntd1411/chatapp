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
all: chat_crypto.ko chat_crypto_test jni
	@echo "[OK] All targets built successfully"

# Build kernel module using kbuild (NOT direct gcc)
chat_crypto.ko:
	@echo "Building kernel module for kernel version: $(KVERSION)..."
	make -C $(KBUILD_DIR) M=$(PWD) modules

chat_crypto_test: chat_crypto_test.c
	@echo "Building test application..."
	gcc -I../include -o chat_crypto_test chat_crypto_test.c
	@echo "[OK] Test app built"

jni:
	@echo "Compiling JNI library..."
	gcc -I/usr/lib/jvm/java-17-openjdk-amd64/include \
		-I/usr/lib/jvm/java-17-openjdk-amd64/include/linux \
		-I./include \
		-fPIC -shared \
		-o libcrypto_jni.so crypto_jni.c
	@echo "[OK] JNI library compiled: libcrypto_jni.so"
	@ls -lh libcrypto_jni.so
	@echo "Installing JNI library to system path..."
	sudo cp libcrypto_jni.so /usr/lib/x86_64-linux-gnu/
	sudo ldconfig
	@echo "[OK] JNI library installed"

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
	rm -f chat_crypto_test
	@echo "[OK] Clean complete"

.PHONY: all install uninstall clean

