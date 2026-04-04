const jwt = require("jsonwebtoken")

module.exports.generateToken = (user) => {
  const payload = {
    sub: user.id,
    username: user.username,
  }
  return jwt.sign(payload, process.env.JWT_SECRET_KEY, {
    expiresIn: Number(process.env.JWT_EXPIRES_IN),
  });
}