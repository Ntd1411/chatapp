#!/bin/bash
# Quick verification script to check if kernel driver and JNI are properly set up
# 
# Usage: ./verify-setup.sh

echo "=== ChatApp Crypto Setup Verification ==="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

PASSED=0
FAILED=0

# Test 1: Check Java
echo -n "[1/6] Checking Java 17... "
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | grep -oP '17' || echo "")
    if [ ! -z "$JAVA_VERSION" ]; then
        echo -e "${GREEN}✓${NC}"
        PASSED=$((PASSED+1))
    else
        echo -e "${YELLOW}⚠${NC} (version not 17)"
        FAILED=$((FAILED+1))
    fi
else
    echo -e "${RED}✗${NC}"
    FAILED=$((FAILED+1))
fi

# Test 2: Check gcc
echo -n "[2/6] Checking gcc... "
if command -v gcc &> /dev/null; then
    echo -e "${GREEN}✓${NC}"
    PASSED=$((PASSED+1))
else
    echo -e "${RED}✗${NC}"
    FAILED=$((FAILED+1))
fi

# Test 3: Check kernel module
echo -n "[3/6] Checking kernel module (chat_crypto.ko)... "
if lsmod | grep -q chat_crypto; then
    echo -e "${GREEN}✓${NC} (loaded)"
    PASSED=$((PASSED+1))
else
    echo -e "${RED}✗${NC} (not loaded)"
    FAILED=$((FAILED+1))
fi

# Test 4: Check device file
echo -n "[4/6] Checking device file (/dev/chat_crypto)... "
if [ -c /dev/chat_crypto ]; then
    PERMS=$(ls -la /dev/chat_crypto | awk '{print $1}' | tail -c 4)
    echo -e "${GREEN}✓${NC}"
    PASSED=$((PASSED+1))
else
    echo -e "${RED}✗${NC}"
    FAILED=$((FAILED+1))
fi

# Test 5: Check JNI library
echo -n "[5/6] Checking JNI library (libcrypto_jni.so)... "
if [ -f /usr/lib/x86_64-linux-gnu/libcrypto_jni.so ]; then
    echo -e "${GREEN}✓${NC} (system library path)"
    PASSED=$((PASSED+1))
elif [ -f ~/.local/lib/libcrypto_jni.so ]; then
    echo -e "${YELLOW}⚠${NC} (user library path, may need LD_LIBRARY_PATH)"
    FAILED=$((FAILED+1))
else
    echo -e "${RED}✗${NC} (not found)"
    FAILED=$((FAILED+1))
fi

# Test 6: Check JNI symbols
echo -n "[6/6] Checking JNI library symbols... "
if [ -f /usr/lib/x86_64-linux-gnu/libcrypto_jni.so ]; then
    if nm /usr/lib/x86_64-linux-gnu/libcrypto_jni.so | grep -q "Java_com_chatty_services_CryptoService"; then
        echo -e "${GREEN}✓${NC}"
        PASSED=$((PASSED+1))
    else
        echo -e "${RED}✗${NC} (symbols missing)"
        FAILED=$((FAILED+1))
    fi
else
    echo -e "${RED}✗${NC} (library not found)"
    FAILED=$((FAILED+1))
fi

echo ""
echo "=== Results ==="
echo -e "Passed: ${GREEN}${PASSED}/6${NC}"
echo -e "Failed: ${RED}${FAILED}/6${NC}"
echo ""

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ All checks passed! Setup is complete.${NC}"
    echo ""
    echo "Next steps:"
    echo "1. Build project: mvn clean compile"
    echo "2. Run application: mvn javafx:run"
else
    echo -e "${RED}✗ Some checks failed. Please run the setup script:${NC}"
    echo ""
    echo "cd ~/chatapp/desktop/src/driver"
    echo "chmod +x setup-jni.sh"
    echo "./setup-jni.sh"
    echo ""
    echo "For detailed help, see: JNI_SETUP_GUIDE.md"
fi
