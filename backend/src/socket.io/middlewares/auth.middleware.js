const jwt = require("jsonwebtoken");
const BlacklistedToken = require("../../models/blacklistedToken.model");
const User = require("../../models/user.model");

module.exports = async (socket, next) => {
  try {
    const token = socket.handshake.auth.token;
    if (!token) {
      next(new Error("Vui lòng đăng nhập để tiếp tục"));
    }
    const blacklistedToken = await BlacklistedToken.findOne({
      token
    });

    if (blacklistedToken) {
      next(new Error("Token đã bị thu hồi"));
    }
    const payload = jwt.verify(token, process.env.JWT_SECRET_KEY);

    const user = await User.findById(payload.sub).select('-password -createdAt -updatedAt -__v');

    if (!user) {
      next(new Error("Vui lòng đăng nhập để tiếp tục"));
    }
    socket.user = user;
    next();

  } catch (error) {
    if (error.name === 'JsonWebTokenError' || error.name === 'TokenExpiredError') {
      console.log("ERROR:", error)
      next(new Error("Token không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại"));
    }
    console.error("Auth middleware error:", error);
    next(new Error("Internal server error"));
  }
}