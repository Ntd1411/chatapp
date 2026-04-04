const express = require("express");
const router = express.Router();
const authMiddleware = require("../middlewares/auth.middleware");

const controller = require("../controllers/auth.controller");
const validate = require("../validates/auth.validate");


router.post('/login', validate.loginValidate, controller.login);
router.post('/logout', authMiddleware, controller.logout);
router.post('/signup', validate.signupValidate, controller.signup);
router.get('/me', authMiddleware, controller.me);



module.exports = router;