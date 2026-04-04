# Clone repository
git clone https://github.com/Ntd1411/chatapp.git
cd chatapp

# Cài JDK 17+
sudo apt install -y openjdk-17-jdk

# Cài Maven
sudo apt install -y maven

# Xác nhận
java -version
mvn --version

# Cài gcc
sudo apt-get install build-essential

# Cài driver
cd desktop/src/driver && make clean && make && make install

# Build project
cd desktop
mvn clean javafx:run

# Hoặc build thành file JAR
mvn clean package
java -jar target/chatty-desktop-1.0.0.jar

# Từ thư mục desktop
cd desktop

# Cách 1: Maven compile + run
mvn clean compile
mvn exec:java -Dexec.mainClass="com.chatty.services.CryptoUsageExample"

# Cách 2: Compile & run trực tiếp
javac -cp target/classes src/main/java/com/chatty/services/CryptoUsageExample.java
java -cp src/main/java:target/classes com.chatty.services.CryptoUsageExample

Using kernel module for crypto operations
Password: user123
Hash: 40dc55bb1b18f9cfe0c1ae4c79d4175b8e89ef8a

Message: Secret message
Key: 12345678
Encrypted: [hex string]

Decrypted: Secret message

Message: Important data
Hash: 2c3c13d47fc23d5a16b37e5db2ec3e5b6a63c6f9
Verified: true