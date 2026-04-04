const Group = require('../models/group.model');
const mongoose = require('mongoose');
const User = require('../models/user.model');
const Message = require('../models/message.model');

module.exports.getGroupMessages = async (req, res) => {
  try {
    const userId = req.user._id;
    const { id } = req.params;

    if(!mongoose.Types.ObjectId.isValid(id)) return res.status(400).json({ message: "ID Nhóm không tồn tại"});

    const group = await Group.findById(id);
    if(!group) return res.status(404).json({ message: "Nhóm không tồn tại"});

    if(!group.isActive) return res.status(400).json({ message: "Nhóm không còn hoạt động"});

    const member = group.members.find(m => m.userId.toString() === userId.toString());
    if(!member) return res.status(403).json({ message: "Bạn không phải thành viên nhóm này"});

    const messages = await Message.find({
      groupId: id
    }).sort({ createdAt: 1}).populate("senderId", "username avatar fullName");

    return res.status(200).json({ message: "Lấy tin nhắn thành công", messages})


  } catch (error) {
    console.log("Lỗi khi lấy tin nhắn trong nhóm: ", error);
    return res.status(500).json({ message: "Lỗi server khi lấy tin nhắn"})
  }
}