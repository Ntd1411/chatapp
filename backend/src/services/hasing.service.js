const bcrypt = require("bcrypt");

module.exports.compare =  (password, hashedPassword) => {
  return bcrypt.compareSync(password, hashedPassword);
}

module.exports.hash = (password) => {
  const salt = bcrypt.genSaltSync(10);
  return bcrypt.hashSync(password, salt);
}