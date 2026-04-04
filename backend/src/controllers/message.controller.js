const Message = require("../models/message.model");
const mongoose = require("mongoose");
const User = require("../models/user.model");

// lấy messages giữa tôi và người này
module.exports.getMessages = async (req, res) => {
  try {
    const { id: friendId } = req.params;
    const userId = req.user.id;

    // lấy tất cả messages giữa tôi và người này
    const messages = await Message.find({
      senderId: { $in: [userId, friendId] },
      receiverId: { $in: [userId, friendId] }
    }).sort({ createdAt: 1 }).select('-updatedAt');    

    return res.status(200).json({
      messages,
    })


  } catch (error) {
    console.log(error);
    return res.status(500).json({message: "Lỗi server khi lấy messages"});
  }
}


// lấy danh sách người dùng đã từng nhắn tin với tôi
module.exports.getUsers = async (req, res) => {
  try {
    const userId = req.user.id;
    const userObjectId = new mongoose.Types.ObjectId(userId);

    const users = await Message.aggregate([
      // 1. Lọc tin nhắn liên quan đến userId
      {
        $match: {
          $or: [
            { senderId: userObjectId },
            { receiverId: userObjectId }
          ]
        }
      },
      
      // 2. Sắp xếp theo thời gian (mới nhất trước)
      { $sort: { createdAt: -1 } },
      
      // 3. Group theo otherUserId
      {
        $group: {
          _id: {
            $cond: [
              { $eq: ['$senderId', userObjectId] },
              '$receiverId',
              '$senderId'
            ]
          },
          lastMessage: { $first: '$$ROOT' },  // Tin nhắn cuối cùng
          unreadCount: {
            $sum: {
              $cond: [
                {
                  $and: [
                    { $eq: ['$receiverId', userObjectId] },  // Tin gửi cho mình
                    { $not: { $in: [userObjectId, '$seenBy'] } }  // Mình chưa xem
                  ]
                },
                1,
                0
              ]
            }
          }
        }
      },
      
      // 4. Lookup thông tin user
      {
        $lookup: {
          from: 'users',
          localField: '_id',
          foreignField: '_id',
          as: 'userInfo'
        }
      },
      { $unwind: '$userInfo' },
      
      // 5. Project các field cần thiết
      {
        $project: {
          _id: '$userInfo._id',
          username: '$userInfo.username',
          fullName: '$userInfo.fullName',
          email: '$userInfo.email',
          avatar: '$userInfo.avatar',
          unreadCount: 1,
          lastMessage: {
            content: '$lastMessage.content',
            createdAt: '$lastMessage.createdAt',
            isMine: { $eq: ['$lastMessage.senderId', userObjectId] }
          },
          lastMessageTime: '$lastMessage.createdAt'
        }
      },
      
      // 6. Sắp xếp theo tin nhắn mới nhất
      { $sort: { lastMessageTime: -1 } }
    ]);

    return res.status(200).json({ 
      users,
    })
  } catch (error) {
    console.log(error);
    return res.status(500).json({message: "Lỗi server khi lấy danh sách người dùng"});
  }
}

module.exports.upload = async (req, res) => {
  try {
    const userId = req.user.id;
    const fileUrl = req.body.file;
    if (!fileUrl) {
      return res.status(400).json({
        message: "Bạn chưa upload file"
      })
    }

    return res.status(200).json({
      message: "Upload file thành công",
      url: fileUrl
    })
  } catch (error) {
    console.log(error);
    return res.status(500).json({
      message: "Lỗi server khi upload file"
    })
  }
}

module.exports.deleteMessage = async (req, res) => {
  try {
    const userId = req.user._id;
    const { messageId } = req.body;

    if(!messageId || !mongoose.Types.ObjectId.isValid(messageId)) 
      return res.status(400).json({ message: "Message id không hợp lệ"})

    const message = await Message.findById(messageId);

    if(!message) return res.status(404).json({ message: "Tin nhắn không tồn tại"});

    if(message.senderId.toString() !== userId.toString() && message.receiverId.toString() !== userId.toString())
      return res.status(403).json({ message: "Bạn không có quyền xóa tin nhắn này"});

    const deleted = await Message.findByIdAndDelete(messageId);

    return res.status(200).json({ message: "Xóa tin nhắn thành công", deleted });
  } catch (error) {
    console.log("Lỗi khi xóa tin nhắn: ", error);
    return res.status(500).json({ message: "Lỗi server khi xóa tin nhắn"});
  }
}

module.exports.deleteAllMessage = async (req, res) => {
  try {
    const userId = req.user._id;
    const { id } = req.params;

    if(!id || !mongoose.Types.ObjectId.isValid(id)) return res.status(400).json({ message: "Id người dùng không hợp lệ"})

    const friend = await User.findById(id);

    if(!friend) return res.status(404).json({ message: "Người dùng không tồn tại"})

    const result = await Message.deleteMany({
      senderId: { $in: [userId, friend._id]},
      receiverId: {$in: [userId, friend._id]}
    })

    return res.status(200).json({ message: "Xóa thành công tin nhắn", deletedCount: result.deletedCount })
  } catch (error) {
    console.log("Lỗi khi xóa tất cả tin nhắn: ", error);
    return res.status(500).json({ message: "Lỗi server khi xóa tất cả tin nhắn"})
  }
}