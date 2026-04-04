const User = require("../models/user.model");
const hashingService = require("../services/hasing.service");
const generateTokenService = require("../services/generateToken.service");
const BlacklistedToken = require("../models/blacklistedToken.model");
const jwt = require("jsonwebtoken");
// login
module.exports.login = async (req, res) => {
  try {
    const {username, password} = req.body;
    // check user
    const user = await User.findOne({username});
    if(!user){
      return res.status(401).json({message: "Tên đăng nhập hoặc mật khẩu không đúng "});
    }

    // check mật khẩu
    const isPasswordValid = hashingService.compare(
      password,
      user.password,
    );
    if(!isPasswordValid){
      return res.status(401).json({message: "Tên đăng nhập hoặc mật khẩu không đúng "});
    }

    // tạo token
    const token = generateTokenService.generateToken(user);

    return res.status(200).json({
      message: "Đăng nhập thành công",
      token
    });
  } catch (error) {
    console.error("Login error:", error);
    return res.status(500).json({message: "Lỗi server khi đăng nhập"});
  }
}



// signup
module.exports.signup = async(req, res) => {
  try {
    const {username, password, fullName, email} = req.body;
    // check username exists
    const user = await User.findOne({username});
    if(user) {
      return res.status(400).json({message: "Tên đăng nhập đã tồn tại"});
    }

    // check email exists
    const emailExists = await User.findOne({email});
    if(emailExists) {
      return res.status(400).json({message: "Email đã tồn tại"});
    }

    // hash password
    const hashedPassword = hashingService.hash(password);

    // create user
    const newUser = await User.create({
      username,
      password: hashedPassword,
      fullName,
      email,
    });

    return res.status(201).json({
      message: "Đăng ký thành công",
      user: {
        username: newUser.username,
        fullName: newUser.fullName,
        email: newUser.email,
      }
    });
  } catch (error) {
    console.error("Signup error:", error);
    return res.status(500).json({message: "Lỗi server khi đăng ký"});
  }
}


// logout
module.exports.logout = async (req, res) => {
  try {
    // lấy token 
    const { token, exp } = req.token;

    // Kiểm tra token đã được blacklist chưa
    const existingToken = await BlacklistedToken.findOne({ token });
    if (existingToken) {
      return res.status(200).json({ message: "Đã đăng xuất thành công" });
    }

    // Tính thời gian hết hạn từ exp đã lấy từ payload
    const expiresAt = exp ? new Date(exp * 1000) : new Date(Date.now() + 24 * 60 * 60 * 1000); // Mặc định 24h nếu không có exp

    // Lưu token vào blacklist
    await BlacklistedToken.create({
      token,
      expiresAt,
    });

    return res.status(200).json({ message: "Đăng xuất thành công" });
  } catch (error) {
    console.error("Logout error:", error);
    return res.status(500).json({ message: "Lỗi server khi đăng xuất" });
  }
}



// me
module.exports.me = async (req, res) => {
  try {
    return res.status(200).json({ user: req.user });
  } catch (error) {
    console.error("Me error:", error);
    return res.status(500).json({ message: "Lỗi server khi lấy thông tin người dùng" });
  }
}