const Group = require('../models/group.model');
const mongoose = require('mongoose');
const User = require('../models/user.model');

module.exports.createGroup = async (req, res) => {
  try {
    const userId = req.user._id;
    const { name, members, description } = req.body;

    if (!name || name.trim() === '') {
      return res.status(400).json({ message: "Tên nhóm không được để trống" });
    }

    if (!members || !Array.isArray(members)) {
      return res.status(400).json({ message: "Danh sách thành viên nhóm không hợp lệ" });
    }

    if (members.length < 2) {
      return res.status(400).json({ message: "Nhóm phải có ít nhất 3 thành viên" });
    }

    const invalidIds = members.filter(id => !mongoose.Types.ObjectId.isValid(id));
    if (invalidIds.length > 0) {
      return res.status(400).json({ message: "Một số Id thành viên không hợp lệ" })
    }

    const memberObjectIds = members.map(id => new mongoose.Types.ObjectId(id));
    const existingUsers = await User.find({
      _id: { $in: memberObjectIds }
    }).select('_id');

    if (existingUsers.length != memberObjectIds.length) {
      return res.status(400).json({ message: "Một số thành viên không tồn tại!" });
    }

    const creatorObjectId = new mongoose.Types.ObjectId(userId);
    const groupMembers = [
      {
        userId: creatorObjectId,
        role: 'admin',
        joinedAt: new Date()
      },
      ...memberObjectIds.filter(id => id != creatorObjectId)
        .map(id => ({
          userId: id,
          role: 'member',
          joinedAt: new Date()
        }))
    ]

    const group = await Group.create({
      name: name.trim(),
      description: description?.trim() || '',
      owner: creatorObjectId,
      members: groupMembers,
      isActive: true
    })

    const populatedGroup = await Group.findById(group._id)
      .populate('owner', 'username fullName avatar')
      .populate('members.userId', 'username fullName avatar');

    return res.status(201).json({
      message: "Tạo nhóm thành công",
      group: populatedGroup
    })
  } catch (error) {
    console.log("Lỗi tạo nhóm: ", error);
    return res.status(500).json({ message: "Lỗi server khi tạo nhóm" });
  }
}

module.exports.getGroups = async (req, res) => {
  try {
    const userId = req.user._id;
    const userObjectId = new mongoose.Types.ObjectId(userId);

    const groups = await Group.aggregate([
      //lọc các nhóm mà user tham gia
      {
        $match: {
          "members.userId": userObjectId,
          isActive: true
        }
      },
      //lấy danh sách các tin nhắn
      {
        $lookup: {
          from: "messages",
          let: { groupId: "$_id" },
          pipeline: [
            {
              $match: {
                $expr: { $eq: ["$groupId", "$$groupId"] }
              }
            },
            { $sort: { createdAt: -1 } }
          ],
          as: "allMessages"
        }
      },
      // tính toán lastMessage và unreadCount
      {
        $addFields: {
          lastMessage: { $arrayElemAt: ["$allMessages", 0] },
          unreadCount: {
            $size: {
              $filter: {
                input: "$allMessages",
                as: "msg",
                cond: {
                  $and: [
                    { $ne: ["$$msg.senderId", userObjectId] }, // Không phải mình gửi
                    { $not: { $in: [userObjectId, { $ifNull: ["$$msg.seenBy", []] }] } } // Mình chưa xem
                  ]
                }
              }
            }
          }
        }
      },
      //thông tin người gửi tin nhắn cuối
      {
        $lookup: {
          from: "users",
          localField: "lastMessage.senderId",
          foreignField: "_id",
          as: "lastMessageSender"
        }
      },
      { $unwind: { path: "$lastMessageSender", preserveNullAndEmptyArrays: true } },
      //project các field cần thiết
      {
        $project: {
          _id: 1,
          name: 1,
          avatar: 1,
          description: 1,
          owner: 1,
          unreadCount: 1,
          lastMessage: {
            content: "$lastMessage.content",
            createdAt: "$lastMessage.createdAt",
            senderName: "$lastMessageSender.fullName",
            isMine: { $eq: ["$lastMessage.senderId", userObjectId] }
          },
          updatedAt: 1
        }
      },
      //sắp xếp theo tin nhắn mới nhất hoặc thời gian cập nhật group
      {
        $sort: { "lastMessage.createdAt": -1, updatedAt: -1 }
      }
    ]);

    return res.status(200).json({
      success: true,
      groups
    });
  } catch (error) {
    console.log("Lỗi lấy danh sách nhóm: ", error);
    return res.status(500).json({ message: "Lỗi server khi lấy danh sách nhóm" });
  }
}

module.exports.updateGroup = async (req, res) => {
  try {
    const userId = req.user._id;
    const { id } = req.params;
    const { name, description, avatar } = req.body;
    
    if(!mongoose.Types.ObjectId.isValid(id)) return res.status(400).json({ message: "ID Nhóm không tồn tại"});

    const group = await Group.findById(id);
    if(!group) return res.status(404).json({ message: "Nhóm không tồn tại"});

    if(!group.isActive) return res.status(400).json({ message: "Nhóm không còn hoạt động"});

    const member = group.members.find(m => m.userId.toString() === userId.toString());
    if(!member) return res.status(403).json({ message: "Bạn không phải thành viên nhóm này"});

    if(member.role !== "admin") {
      return res.status(403).json({ message: "Chỉ admin được thay đổi thông tin nhóm"});
    }

    if (name !== undefined) {
      if (name.trim() === '') {
        return res.status(400).json({ message: "Tên nhóm không được để trống" });
      }
      group.name = name.trim();
    }

    if (description !== undefined) {
      group.description = description.trim();
    }

    if (avatar !== undefined) {
      group.avatar = avatar;
    }

    await group.save();

    return res.status(200).json({
      message: "Cập nhật thành công",
      group
    })
  } catch (error) {
    console.log("Lỗi khi sửa nhóm: ", error);
    return res.status(400).json({ message: "Lỗi server khi sửa nhóm"});
  }
  
}

module.exports.getInfoGroup = async (req, res) => {
  try {
    const userId = req.user._id;
    const { id } = req.params;

    if(!mongoose.Types.ObjectId.isValid(id)) return res.status(400).json({ message: "Id nhóm không hợp lệ"});

    const group = await Group.findById(id)
    .populate("members.userId", "username avatar fullName")
    .populate("owner", "username avatar fullName");
    if(!group) return res.status(404).json({ message: "Nhóm không tồn tại"});
    if(!group.isActive) return res.status(400).json({ message: "Nhóm không còn hoạt động"});

    const member = group.members.find(m =>
      m.userId &&
      m.userId._id &&
      m.userId._id.toString() === userId.toString()
    );
  
    if(!member) return res.status(403).json({ message: "Bạn không phải thành viên của nhóm này"});
    return res.status(200).json({ message: "Lấy thông tin nhóm thành công", group});
  } catch (error) {
    console.log("Lỗi khi lấy thông tin nhóm: ", error);
    return res.status(500).json({ message: "Lỗi server khi lấy thông tin nhóm"});
  }
}

module.exports.deleteGroup = async (req, res) => {
  try {
    const userId = req.user._id;
    const { id } = req.params;

    if(!mongoose.Types.ObjectId.isValid(id)) return res.status(400).json({ message: "ID Nhóm không hợp lệ"});

    const group = await Group.findById(id)

    if(!group) return res.status(404).json({ message: "Nhóm không tồn tại"});

    if(!group.isActive) {
      return res.status(400).json({ message: "Nhóm đã dừng hoạt động"});
    }

    const member = group.members.find(m => m.userId.toString() === userId.toString());
    if(!member) return res.status(403).json({ message: "Bạn không phải là thành viên của nhóm này"});
    // Nếu là chủ group thì dừng hoạt động group nếu khác thì rời nhóm
    if(group.owner.toString() === userId.toString()) {
      group.isActive = false;
      await group.save();
      return res.status(200).json({ message: "Xóa nhóm thành công", group});
    } 

    group.members = group.members.filter(m => m.userId.toString() !== userId.toString());
    if(group.members.length === 0) {
      group.isActive = false;
    }
    await group.save();
    return res.status(200).json({ message: "Rời nhóm thành công", group});
    
  } catch (error) {
    console.log("Lỗi khi xóa nhóm: ", error);
    return res.status(500).json({ message: "Lỗi server khi xóa nhóm"})
  }
}