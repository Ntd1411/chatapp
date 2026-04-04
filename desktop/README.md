# Cài JDK 17+
sudo apt install -y openjdk-17-jdk

# Cài Maven
sudo apt install -y maven

# Xác nhận
java -version
mvn --version

cd desktop

# Build project
mvn clean javafx:run

# Hoặc build thành file JAR
mvn clean package
java -jar target/chatty-desktop-1.0.0.jar