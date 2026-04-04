const mongoose = require("mongoose");

const userSchema = new mongoose.Schema(
  {
    username: {
      type: String,
      unique: true,
      required: true,
    },
    password: {
      type: String,
      required: true,
      minlength: 6,
    },
    fullName: {
      type: String,
      required: true,
    },
    email: {
      type: String,
      required: true,
      unique: true
    },
    avatar: {
      type: String,
      required: false,
    }
  },
  { timestamps: true }
);

const User = mongoose.model("User", userSchema);

module.exports =  User;
