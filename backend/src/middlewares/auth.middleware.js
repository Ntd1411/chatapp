const jwt = require("jsonwebtoken");
const User = require("../models/user.model");
const BlacklistedToken = require("../models/blacklistedToken.model");


module.exports = async (req, res, next) => {
  try {
    // lấy header
    const authHeader = req.get("Authorization") || req.get("authorization")
    if(!authHeader || !authHeader.startsWith('Bearer ')) {
      return res.status(401).json({error: 'Unauthorized - No header Authorization'})
    }

    // lấy token
    const token = authHeader.slice(7).trim();
    if(!token) {
      return res.status(401).json({error: "Vui lòng đăng nhập để tiếp tục"})
    }

    // Kiểm tra token có trong blacklist không
    const blacklistedToken = await BlacklistedToken.findOne({ token });
    if (blacklistedToken) {
      return res.status(401).json({error: "Token đã bị thu hồi. Vui lòng đăng nhập lại"});
    }
    const payload = jwt.verify(token, process.env.JWT_SECRET_KEY);

    const user = await User.findById(payload.sub).select('-password -createdAt -updatedAt -__v');

    if(!user) {
      return res.status(401).json({error: "Vui lòng đăng nhập để tiếp tục"}); 
    }

    req.user = user;
    req.token = {
      token,
      exp: payload.exp,
    }; // Lưu token vào req để controller có thể dùng
    next();
  } catch (error) {
    if (error.name === 'JsonWebTokenError' || error.name === 'TokenExpiredError') {
      console.log("ERROR:", error)
      return res.status(401).json({error: "Token không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại"});
    }
    console.error("Auth middleware error:", error);
    return res.status(500).json({ message: "Internal server error" });
  }
}