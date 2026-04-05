#!/bin/bash
# Setup script để biên dịch JNI library và cài đặt kernel driver
# 
# Usage: ./setup-jni.sh
# Yêu cầu: Java 17 JDK, gcc, Linux kernel development headers

set -e

echo "=== ChatApp JNI & Kernel Driver Setup ==="
echo ""

# Kiểm tra Java
echo "[1/5] Checking Java..."
if ! command -v javac &> /dev/null; then
    echo "ERROR: Java compiler (javac) not found! Installing Java 17..."
    sudo apt update && sudo apt install -y openjdk-17-jdk
fi
JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
echo "✓ Java found at: $JAVA_HOME"
echo ""

# Kiểm tra gcc
echo "[2/5] Checking gcc..."
if ! command -v gcc &> /dev/null; then
    echo "ERROR: gcc not found! Installing..."
    sudo apt update && sudo apt install -y build-essential
fi
echo "✓ gcc found: $(gcc --version | head -n1)"
echo ""

# Kiểm tra kernel headers
echo "[3/5] Checking Linux kernel development headers..."
KVERSION=$(uname -r)
if [ ! -d "/lib/modules/$KVERSION/build" ]; then
    echo "ERROR: Kernel headers for $KVERSION not found!"
    echo "Installing kernel headers..."
    sudo apt update && sudo apt install -y linux-headers-generic
fi
echo "✓ Kernel headers found for: $KVERSION"
echo ""

# Biên dịch kernel module
echo "[4/5] Compiling kernel module..."
cd "$(dirname "$0")"
echo "Working directory: $(pwd)"

if [ -f "Makefile" ]; then
    echo "Building kernel module..."
    make clean
    make
    echo "✓ Kernel module compiled successfully"
    echo "   Output: chat_crypto.ko"
    
    # Cây cố cài đặt kernel module
    echo ""
    echo "Installing kernel module..."
    sudo make install
    echo "✓ Kernel module installed"
    echo "   Device file: /dev/chat_crypto"
    echo "   Check with: ls -la /dev/chat_crypto"
else
    echo "ERROR: Makefile not found!"
    exit 1
fi
echo ""

# Biên dịch JNI library
echo "[5/5] Compiling JNI library..."
if [ -f "crypto_jni.c" ] && [ -f "include/crypto_module.h" ]; then
    echo "Compiling libcrypto_jni.so..."
    gcc -I"$JAVA_HOME/include" \
        -I"$JAVA_HOME/include/linux" \
        -I./include \
        -fPIC -shared \
        -o libcrypto_jni.so \
        crypto_jni.c
    
    if [ -f "libcrypto_jni.so" ]; then
        echo "✓ JNI library compiled: libcrypto_jni.so"
        
        # Cài đặt JNI library vào system path
        echo ""
        echo "Installing JNI library to system library path..."
        sudo cp libcrypto_jni.so /usr/lib/x86_64-linux-gnu/
        
        if [ -f "/usr/lib/x86_64-linux-gnu/libcrypto_jni.so" ]; then
            echo "✓ JNI library installed to /usr/lib/x86_64-linux-gnu/"
        else
            echo "ERROR: Failed to install JNI library!"
            exit 1
        fi
    else
        echo "ERROR: Failed to compile JNI library!"
        echo "Check compilation errors above"
        exit 1
    fi
else
    echo "ERROR: crypto_jni.c or include/crypto_module.h not found!"
    exit 1
fi
echo ""

echo "=== Setup Complete ==="
echo ""
echo "Verification steps:"
echo "  1. Check kernel driver: ls -la /dev/chat_crypto"
echo "  2. Check JNI library: ls -la /usr/lib/x86_64-linux-gnu/libcrypto_jni.so"
echo "  3. Run Java app: mvn clean compile javafx:run"
echo ""
echo "If JNI library fails to load, check:"
echo "  - /var/log/syslog for kernel module errors"
echo "  - java.library.path configuration"
echo "  - LD_LIBRARY_PATH environment variable"
echo ""
