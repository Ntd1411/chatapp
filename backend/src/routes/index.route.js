const authRouter = require("./auth.route");
const messageRouter = require("./message.route");
const userRouter = require("./user.route");
const groupRouter = require("./group.route.js");

module.exports = (app) => {
  app.use('/auth', authRouter);
  app.use('/messages', messageRouter);
  app.use('/users', userRouter);
  app.use('/groups', groupRouter);
}

