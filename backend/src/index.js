const express = require("express");
require("dotenv").config();
const cors = require("cors")
const initSocket = require("./socket.io/index");

const databaseConfig = require("./configs/database.config");

const UserRouter = require("./routes/index.route")
const {
  createServer
} = require("http");
const path = require("path");


const app = express();
// ===== SERVE FRONTEND STATIC FILES =====
app.use(express.static(path.join(__dirname, "../frontend")));




// middleware 
// CORS configuration
const allowedOrigins = process.env.ALLOWED_ORIGINS ? process.env.ALLOWED_ORIGINS.split(',') : [
  'http://localhost:5173'
]
app.use(cors({
    origin: (origin, callback) => {
      // nếu request không có origin được gửi đến
      // không phải browser nên không cần check cors -> cho phép
      if (!origin) return callback(null, true);

      // nằm trong allow thì được phép
      if (allowedOrigins.indexOf(origin) !== -1 || allowedOrigins.includes('*')) {
        return callback(null, true);
      }   
      return callback(new Error("Not allowed by CORS"));
    },
    // cho phép cookie/auth gửi kèm (cookie, session, jwt trong cookie)
    // Lưu ý: Nếu cho phép tất cả origin mà dùng credentials: true thì CORS sẽ bị sai theo chuẩn.
    credentials: true,
    methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
    // Chỉ cho phép những header cần
    // còn các header như cookie, origin, referer trình duyệt tự xử lý và không cần cho phép thủ công.
    allowedHeaders: ['Content-Type', 'Authorization']
  }

))
app.use(express.urlencoded({
  extended: false
}));
app.use(express.json());


databaseConfig.connect();

app.get("/", (req, res) => {
  res.send("Welcome to chatapp!");
})
UserRouter(app)

const httpServer = createServer(app);
const io = initSocket(httpServer);
app.set('io', io);

const PORT = process.env.PORT || 3000;
httpServer.listen(PORT, () => {
  console.log("Server is listening on port " + PORT)
})