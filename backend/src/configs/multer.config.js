const multer = require('multer');
// const storageMulter = require("../../helpers/storageMulter");
module.exports = multer({
  limits: {
    fieldSize: 2 * 1024 * 1024 // Set field size to 2MB (2 * 1024 * 1024 bytes)
  }
});