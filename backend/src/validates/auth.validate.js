module.exports.loginValidate = (req, res, next) => {
  const {username, password} = req.body;
  if(!username || !password) {
    return res.status(400).json({message: "Vui lòng nhập tên đăng nhập và mật khẩu"});
  }
  if(username.length < 3 || username.length > 16) {
    return res.status(400).json({message: "Tên đăng nhập phải có từ 3 đến 16 ký tự"});
  }
  if(password.length < 6) {
    return res.status(400).json({message: "Mật khẩu phải có ít nhất 6 ký tự"});
  }
  next();
}


module.exports.signupValidate = (req, res, next) => {
  const {username, password, fullName, email} = req.body;
  if(!username || !password || !fullName || !email) {
    return res.status(400).json({message: "Vui lòng nhập tên đăng nhập, mật khẩu, tên đầy đủ và email"});
  }
  if(username.length < 3 || username.length > 16) {
    return res.status(400).json({message: "Tên đăng nhập phải có từ 3 đến 16 ký tự"});
  }
  if(password.length < 6) {
    return res.status(400).json({message: "Mật khẩu phải có ít nhất 6 ký tự"});
  }
  // Validate email format
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if(!emailRegex.test(email)) {
    return res.status(400).json({message: "Email không hợp lệ"});
  }
  next();
}
