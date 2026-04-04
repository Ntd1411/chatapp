# Realtime Chat Application (KMA Chatty)

Chào mừng bạn đến với **KMA Chatty** - Ứng dụng nhắn tin thời gian thực đa nền tảng. Dự án này là một hệ thống Fullstack hoàn chỉnh bao gồm Backend xử lý logic, Web Client hiện đại và Desktop Client (JavaFX) hoạt động đồng bộ.

Dự án này minh họa cách xây dựng ứng dụng realtime sử dụng Socket.IO và cách porting giao diện/logic từ Web sang Desktop Application.

## 🌟 Tính Năng Nổi Bật

### Chức Năng Chính
- **Real-time Messaging:** Gửi và nhận tin nhắn tức thì (độ trễ thấp) thông qua giao thức WebSocket (Socket.IO).
- **Authentication:** Hệ thống Đăng nhập / Đăng ký người dùng an toàn.
- **User Management:**
  - Hiển thị danh sách người dùng trong hệ thống.
  - Cập nhật trạng thái **Online / Offline** theo thời gian thực.
- **Chat History:** Tải và hiển thị lịch sử tin nhắn cũ khi vào lại cuộc trò chuyện.

### Kiến Trúc Kỹ Thuật
- **Frontend:** Single Page Application (SPA) tối ưu tốc độ.
- **Desktop:** Ứng dụng Native chạy trên Windows/macOS/Linux.
- **Backend:** RESTful API kết hợp WebSocket Server.

## 📂 Cấu Trúc Dự Án

```bash
RealtimeChatApp
├── backend
│   ├── src
│   ├── package-lock.json
│   └── package.json
├── desktop
│   ├── src
│   ├── target
│   └── pom.xml
├── frontend
│   ├── src
│   ├── call.html
│   ├── chat.html
│   ├── index.html
│   ├── package-lock.json
│   ├── package.json
│   └── vite.config.js
└── README.md
```

## 🛠️ Công Nghệ Sử Dụng

Các công nghệ cốt lõi được sử dụng để xây dựng dự án:

*   **Backend (Server Side):**
    ![NodeJS](https://img.shields.io/badge/Node.js-339933?style=flat&logo=node.js&logoColor=white)
    ![Express.js](https://img.shields.io/badge/Express.js-000000?style=flat&logo=express&logoColor=white)
    ![Socket.io](https://img.shields.io/badge/Socket.io-010101?style=flat&logo=socket.io&logoColor=white)
    ![MongoDB](https://img.shields.io/badge/MongoDB-47A248?style=flat&logo=mongodb&logoColor=white)
    ![Mongoose](https://img.shields.io/badge/Mongoose-880000?style=flat&logo=mongoose&logoColor=white)

*   **Frontend (Web Client):**
    ![HTML5](https://img.shields.io/badge/HTML5-E34F26?style=flat&logo=html5&logoColor=white)
    ![CSS3](https://img.shields.io/badge/CSS3-1572B6?style=flat&logo=css3&logoColor=white)
    ![JavaScript](https://img.shields.io/badge/JavaScript-F7DF1E?style=flat&logo=javascript&logoColor=black)
    ![Vite](https://img.shields.io/badge/Vite-646CFF?style=flat&logo=vite&logoColor=white)
    ![Axios](https://img.shields.io/badge/Axios-5A29E4?style=flat&logo=axios&logoColor=white)

*   **Desktop (Java Client):**
    ![Java](https://img.shields.io/badge/Java_17-ED8B00?style=flat&logo=openjdk&logoColor=white)
    ![JavaFX](https://img.shields.io/badge/JavaFX-2D79AD?style=flat&logo=java&logoColor=white)
    ![Maven](https://img.shields.io/badge/Maven-C71A36?style=flat&logo=apachemaven&logoColor=white)
    ![Socket.IO-Client](https://img.shields.io/badge/Socket.IO_Java-010101?style=flat&logo=socket.io&logoColor=white)

## ⚙️ Yêu cầu hệ thống

Để chạy được toàn bộ dự án (Fullstack), máy tính của bạn cần cài đặt sẵn:

| <div align="center">Phần mềm</div> | <div align="center">Phiên bản yêu cầu</div> | <div align="center">Mục đích</div> |
| :--- | :---: | :--- |
| <img src="https://cdn.simpleicons.org/nodedotjs/339933" width="16" height="16"/> **[Node.js](https://nodejs.org/)** | `v18+` | Chạy Server Backend & Frontend Build Tool |
| <img src="https://cdn.simpleicons.org/mongodb/47A248" width="16" height="16"/> **[MongoDB](https://www.mongodb.com/try/download/community)** | `v6.0+` | Cơ sở dữ liệu (Database) |
| <img src="https://cdn.simpleicons.org/openjdk/FFFFFF" width="16" height="16"/> **[JDK](https://www.oracle.com/java/technologies/downloads/)** | `v17+` | Môi trường chạy Desktop App (Java) |
| <img src="https://cdn.simpleicons.org/apachemaven/C71A36" width="16" height="16"/> **[Maven](https://maven.apache.org/download.cgi)** | `v3.6+` | Công cụ build cho Desktop App |

> **Lưu ý:** Đảm bảo rằng các lệnh `node`, `npm`, `java`, `mvn` đã được thêm vào biến môi trường (PATH) của hệ điều hành.

## <img src="https://cdn.simpleicons.org/git/F05032" width="20" height="20"/> Clone Repository Này

```bash
git clone https://github.com/An-K4/RealtimeChatApp.git
cd RealtimeChatApp
```

## 🚀 Hướng Dẫn Cài Đặt & Chạy

Để ứng dụng hoạt động, bạn cần chạy Backend trước, sau đó mới chạy các Client (Web hoặc Desktop).

### 1. Thiết lập biến môi trường
Để có thể khởi động Backend, cần tạo file .env ở thư mục gốc Backend với mẫu như file /backend/.env.example:

```bash
PORT=3000

MONGO_URI=
MONGO_URL=

JWT_SECRET_KEY=
JWT_EXPIRES_IN=

CLOUD_NAME=
CLOUD_KEY=
CLOUD_SECRET=

ALLOWED_ORIGINS=http://localhost:5173
```

**Mặc định:** Backend sẽ lắng nghe ở cổng 3000 và chấp nhận frontend ở cổng 5173

### 2. Khởi động Backend (Server)

Backend chịu trách nhiệm xử lý dữ liệu và kết nối Socket.

```bash
cd backend
npm install
npm run dev
```

Lưu ý: Backend mặc định sẽ chạy tại http://localhost:3000 như cấu hình .env mẫu. Hãy đảm bảo server đã sẵn sàng trước khi tiếp tục. 

<details>
<summary><b>Xem ví dụ khi Backend đã khởi chạy thành công ở đây</b></summary>
<br>
Nếu terminal hiển thị như dưới đây nghĩa là Backend đã sẵn sàng:


```bash
> backend@1.0.0 dev
> nodemon --inspect src/index.js

[nodemon] 3.1.11
[nodemon] to restart at any time, enter `rs`
[nodemon] watching path(s): *.*
[nodemon] watching extensions: js,mjs,cjs,json
[nodemon] starting `node --inspect src/index.js`
Debugger listening on ws://127.0.0.1:9229/3d7a0722-4ddc-4dea-a615-35ce93b98a25
For help, see: https://nodejs.org/en/docs/inspector
[dotenv@17.2.3] injecting env (9) from .env -- tip: ⚙️  enable debug logging with { debug: true }
Server is listening on port 3000
database connected successfully
```
</details>

### 2. Chạy Web Client (Frontend) để test tính năng của desktop app

Giao diện chat trên trình duyệt.

```bash
cd frontend
npm install
npm run dev
```

Truy cập ứng dụng tại: http://localhost:5173

### 3. Chạy Desktop Client (JavaFX)

Ứng dụng chat trên máy tính.

**Cách 1: Chạy bằng Maven (Khuyên dùng)**

```bash
cd desktop
mvn javafx:run
```

**Cách 2: Sử dụng IntelliJ IDEA**

Mở thư mục bằng IntelliJ IDEA, tìm đến file **Launcher.java** bên trong thư mục desktop, click chuột phải và chọn **Run**.

## ☕ Một Số Hình Ảnh Kết Quả

<!-- Container ảnh: Bạn hãy thay thế đường dẫn src bằng link ảnh thật của bạn sau khi chụp màn hình -->

<div align="center">
  <h3>Giao diện Đăng nhập</h3>
  <img src="https://github.com/user-attachments/assets/6e95cacf-b6b5-45a5-b8a0-8d533e37bad4" alt="Login Screen" width="100%" style="border-radius: 10px; margin-bottom: 20px;">
  
  <h3>Giao diện Chat</h3>
  <img src="https://github.com/user-attachments/assets/0047885f-73d3-4365-8188-5a48441a2a25" alt="Chat Interface" width="100%" style="border-radius: 10px;">
</div>

---

## 🤝 Đóng Góp (Contributing)

Dự án **KMA Chatty** hiện vẫn đang trong quá trình phát triển và hoàn thiện nên vẫn còn nhiều thiếu sót. Chúng mình rất hoan nghênh mọi sự đóng góp từ cộng đồng để dự án trở nên tốt hơn.

Nếu bạn muốn tham gia đóng góp:

1.  **Fork** repository này về tài khoản của bạn.
2.  Tạo nhánh mới cho tính năng của bạn (`git checkout -b feature/TinhNangMoi`).
3.  Commit những thay đổi (`git commit -m 'Thêm tính năng X'`).
4.  Push lên nhánh của bạn (`git push origin feature/TinhNangMoi`).
5.  Tạo một **Pull Request** trên GitHub.

## 👥 Đội Ngũ Phát Triển

Dự án được thực hiện bởi nhóm gồm 4 thành viên:

*   **Nguyễn Tuấn Dũng** - *Backend & Frontend*
*   **Trần Duy Quyến** - *Backend & Frontend*
*   **Vũ Quốc An** - *Desktop App*
*   **Đoàn Hoàng Anh** - *Test & Docs*

## ⭐️ Ủng hộ

Nếu bạn thấy dự án này thú vị hoặc hữu ích, hãy để lại một **Star** ⭐️ để động viên tinh thần cả nhóm nhé!

---
Developed with ❤️ by **KMA Chatty Team**.
