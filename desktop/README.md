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