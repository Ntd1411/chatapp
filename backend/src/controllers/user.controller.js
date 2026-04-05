const User = require("../models/user.model");
const Group = require("../models/group.model");
const PublicKey = require("../models/publickey.model");
const hashingService = require("../services/hasing.service");

module.exports.editAccount = async (req, res) => {
  try {
    const {
      username,
      fullName,
      email
    } = req.body;
    const userId = req.user.id;

    // Kiểm tra username đã tồn tại ở user khác
    const usernameExist = await User.findOne({
      _id: {
        $ne: userId
      },
      username
    });

    if (usernameExist) {
      return res.status(400).json({
        message: "Username này đã tồn tại"
      });
    }

    // Kiểm tra email đã tồn tại ở user khác
    const emailExist = await User.findOne({
      _id: {
        $ne: userId
      },
      email
    });

    if (emailExist) {
      return res.status(400).json({
        message: "Email này đã tồn tại"
      });
    }

    // Cập nhật user
    const updated = await User.updateOne({
      _id: userId
    }, {
      username,
      fullName,
      email
    });

    return res.status(200).json({
      message: "Cập nhật tài khoản thành công",
      updated
    });

  } catch (error) {
    console.log(error);
    return res.status(500).json({
      message: "Lỗi server khi cập nhật tài khoản"
    });
  }
};


module.exports.uploadAvatar = async (req, res) => {
  try {
    const userId = req.user.id;
    const avatar = req.body.avatar;
    if (!avatar) {
      return res.status(400).json({
        message: "Bạn chưa upload avatar"
      })
    }
    // save avatar
    const user = await User.findByIdAndUpdate(
      userId, {
        avatar
      }, {
        new: true
      } // trả về user sau khi update
    );

    return res.status(200).json({
      message: "Avatar updated",
      avatarUrl: user.avatar
    })
  } catch (error) {
    console.log(error);
    return res.status(500).json({
      message: "Lỗi server"
    })
  }
}

module.exports.search = async (req, res) => {
  try {
    const { keyword } = req.query;

    if(!keyword) {
      return res.status(400).json({ message: "Vui lòng cung cấp keyword để tìm kiếm!"});
    }

    const userId = req.user.id;
    const users = await User.find({
      _id: { $ne: userId }, // Loại trừ user hiện tại
      username: { $regex: keyword, $options: 'i'}
    }).select('-password')

    const groups = await Group.find({
      'members.userId': userId,
      name: { $regex: keyword, $options: 'i'}
    })

    return res.status(200).json({
      message: "Tìm kiếm thành công!",
      users,
      groups
    })

  } catch (error) {
    console.log("Search error:", error);
    return res.status(500).json({ message: "Lỗi server khi tìm kiếm!"});
  }
}

module.exports.changePassword = async (req, res) => {
  try {
    const userId = req.user._id;
    const { oldPassword, newPassword } = req.body;

    if(!oldPassword || !newPassword) return res.status(400).json({ message: "Dữ liệu không hợp lệ"});

    if(newPassword.length < 6) {
      return res.status(400).json({message: "Mật khẩu phải có ít nhất 6 ký tự"});
    }

    const user = await User.findById(userId);
    
    if(!hashingService.compare(oldPassword, user.password)) 
      return res.status(400).json({ message: "Mật khẩu cũ không đúng"});

    const hashedNewPassword = hashingService.hash(newPassword);
    user.password = hashedNewPassword;

    await user.save();

    return res.status(200).json({ message: "Đổi mật khẩu thành công"});

  } catch (error) {
    console.log("Lỗi khi đổi mật khẩu: ", error);
    return res.status(500).json({ message: "Lỗi server khi đổi mật khẩu"});
  }
}

module.exports.uploadPublicKey = async (req, res) => {
  try {
    const userId = req.user.id;
    const { publicKey } = req.body;

    if(!publicKey) {
      return res.status(400).json({ message: "Public key không được để trống" });
    }

    // Validate Base64 format
    if(!/^[A-Za-z0-9+/=]+$/.test(publicKey)) {
      return res.status(400).json({ message: "Public key phải là định dạng Base64" });
    }

    // Upsert: Update if exists, create if not
    const result = await PublicKey.findOneAndUpdate(
      { userId },
      { 
        userId,
        publicKey,
        algorithm: "RSA-2048"
      },
      { upsert: true, new: true }
    );

    return res.status(200).json({ 
      message: "Lưu public key thành công",
      data: result
    });

  } catch (error) {
    console.log("Lỗi khi lưu public key:", error);
    return res.status(500).json({ message: "Lỗi server khi lưu public key" });
  }
}

module.exports.getPublicKey = async (req, res) => {
  try {
    const userId = req.params.id;

    if(!userId) {
      return res.status(400).json({ message: "User ID không hợp lệ" });
    }

    const publicKeyRecord = await PublicKey.findOne({ userId }).select('publicKey algorithm');

    if(!publicKeyRecord) {
      return res.status(404).json({ message: "Không tìm thấy public key của người dùng" });
    }

    return res.status(200).json({ 
      message: "Lấy public key thành công",
      data: publicKeyRecord
    });

  } catch (error) {
    console.log("Lỗi khi lấy public key:", error);
    return res.status(500).json({ message: "Lỗi server khi lấy public key" });
  }
}