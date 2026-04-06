const express = require("express")
const router = express.Router();
const controller = require("../controllers/user.controller");
const authMiddleware = require("../middlewares/auth.middleware");
const validate = require("../validates/user.validate");
const uploadMulter = require("../configs/multer.config");
const uploadCloud = require("../middlewares/uploadClound.middleware");


router.patch("/update", authMiddleware, validate.editAccountValidate, controller.editAccount);
router.patch("/upload-avatar", authMiddleware, uploadMulter.single("avatar"), uploadCloud.upload, controller.uploadAvatar);
router.get("/search", authMiddleware, controller.search);
router.post("/change-password", authMiddleware, controller.changePassword);

module.exports = router;

