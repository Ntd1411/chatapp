const Group = require('../models/group.model');
const mongoose = require('mongoose');
const User = require('../models/user.model');

module.exports.addMember = async (req, res) => {
  try {
    const userId = req.user._id;
    const { id } = req.params;
    const { memberIds } = req.body;

    if(!Array.isArray(memberIds) || memberIds.length === 0) return res.status(400).json({ message: "Danh sách thành viên thêm vào không hợp lệ"});

    if(memberIds.filter(m => !mongoose.Types.ObjectId.isValid(m)).length > 0) return res.status(400).json({ message: "Một số Id thành viên không hợp lệ"});

    if(!mongoose.Types.ObjectId.isValid(id)) return res.status(400).json({ message: "ID Nhóm không tồn tại"});

    const group = await Group.findById(id);
    if(!group) return res.status(404).json({ message: "Nhóm không tồn tại"});

    if(!group.isActive) return res.status(400).json({ message: "Nhóm không còn hoạt động"});

    const member = group.members.find(m => m.userId.toString() === userId.toString());
    if(!member) return res.status(403).json({ message: "Bạn không phải thành viên nhóm này"});

    const memberObjectIds = memberIds.map(m => new mongoose.Types.ObjectId(m));
    const existingUsers = await User.find({
      _id: { $in: memberObjectIds}
    }).select('_id')

    if(memberObjectIds.length !== existingUsers.length) {
      return res.status(400).json({ message: "Một số thành viên không tồn tại"});
    }

    const newMembers = [];
    const existingMembers = [];

    memberObjectIds.forEach((memberId) => {
      if(!group.members.find(m => m.userId.toString() === memberId.toString())) {
        newMembers.push({
          userId: memberId,
          role: "member",
          joinedAt: new Date()
        })
      } else {
        existingMembers.push(memberId);
      }
    })

    if(existingMembers.length === memberObjectIds.length)
      return res.status(400).json({ message: "Tất cả thành viên đã có sẵn trong nhóm"});

    group.members.push(...newMembers);

    await group.save();

    return res.status(200).json({ message: "Thêm thành viên thành công", existingMembers, newMembers});


  } catch (error) {
    console.log("Lỗi khi thêm thành viên nhóm: ", error);
    return res.status(500).json({ message: "Lỗi server khi thêm thành viên"});
  }
}

module.exports.changeRole = async (req, res) => {
  try {
    const userId = req.user._id;
    const { id, memberId } = req.params;
    const { newRole } = req.body;

    if(!mongoose.Types.ObjectId.isValid(id)) {
      return res.status(400).json({ message: "ID nhóm không hợp lệ" });
    }

    if(!['admin', 'member'].includes(newRole)) {
      return res.status(400).json({ message: "Role không hợp lệ" });
    }

    const group = await Group.findById(id);
    if(!group) {
      return res.status(404).json({ message: "Nhóm không tồn tại" });
    }

    if(!group.isActive) {
      return res.status(400).json({ message: "Nhóm không còn hoạt động" });
    }

    // Chỉ người chủ nhóm (khác admin) mới được đổi role
    if (group.owner.toString() !== userId.toString()) {
      return res.status(403).json({ message: "Chỉ người chủ nhóm mới được đổi role" });
    }

    const targetMember = group.members.find(m => m.userId.toString() === memberId.toString());
    if(!targetMember) {
      return res.status(404).json({ message: "Thành viên không tồn tại trong nhóm" });
    }

    // Không thể hạ quyền chính mình
    if (userId.toString() === memberId.toString()) {
      return res.status(403).json({ message: "Bạn không thể hạ quyền chính mình" });
    }

    targetMember.role = newRole;
    await group.save();

    const updatedGroup = await Group.findById(id)
      .populate('members.userId', 'username fullName avatar')
      .populate('owner', 'username fullName avatar');

    return res.status(200).json({ 
      message: `Đã đổi role thành ${newRole}`,
      group: updatedGroup
    });
  } catch (error) {
    console.log("Lỗi khi đổi role: ", error);
    return res.status(500).json({ message: "Lỗi server khi đổi role" });
  }
}

module.exports.getMembers = async (req, res) => {
  try {
    const userId = req.user._id;
    const { id } = req.params;

    if(!id || !mongoose.Types.ObjectId.isValid(id)) return res.status(400).json({ message: "ID nhóm không hợp lệ"});

    const group = await Group.findById(id).populate('members.userId', 'username avatar fullName');

    if(!group || !group.isActive) return res.status(400).json({ message: "Nhóm không tồn tại hoặc không còn hoạt động"});

    const member = group.members.find(m => m.userId._id.toString() === userId.toString());
    if(!member) return res.status(403).json({ message: "Bạn không phải là thành viên của nhóm này"});

    const members = group.members.sort((a, b) => new Date(b.joinedAt) - new Date(a.joinedAt));

    return res.status(200).json({ message: "Lấy danh sách thành viên thành công", members});
  } catch (error) {
    console.log("Lỗi khi lấy danh sách nhân viên: ", error);
    return res.status(500).json({ message: "Lỗi server khi lấy danh sách thành viên"})
  }
}

module.exports.deleteMember = async (req, res) => {
  try {
    const userId = req.user._id;
    const { id, memberId } = req.params;

    if(!id || !mongoose.Types.ObjectId.isValid(id)) return res.status(400).json({ message: "ID nhóm không hợp lệ"});

    if(!memberId || !mongoose.Types.ObjectId.isValid(memberId)) return res.status(400).json({ message: "ID thành viên không hợp lệ"})

    const user = await User.findById(memberId);
    if(!user) return res.status(404).json({ message: "Người dùng không tồn tại"})

    const group = await Group.findById(id);
    if(!group || !group.isActive) return res.status(404).json({ message: "Nhóm không tồn tại hoặc đã dừng hoạt động"})

    const member = group.members.find(m => m.userId.toString() === userId.toString());
    if(!member || member.role !== "admin") return res.status(403).json({ message: "Bạn không có quyền thực hiện hành động này"});

    const target = group.members.find(m => m.userId.toString() === memberId.toString());
    if(!target) return res.status(404).json({ message: "Người dùng này không ở trong nhóm này"});

    if(group.owner.toString() === memberId.toString()) return res.status(400).json({ message: "Bạn không thể xóa chủ nhóm" }); 

    if(target.role === 'admin' && userId.toString() !== group.owner.toString())
      return res.status(403).json({ message: "Bạn không thể xóa admin khác"}); 

    group.members = group.members.filter(m => m.userId.toString() !== memberId.toString());

    await group.save();

    return res.status(200).json({ message: "Đã xóa thành công thành viên này khỏi nhóm", group});
  } catch (error) {
    console.log("Lỗi khi xóa thành viên: ", error);
    return res.status(500).json({ message: "Lỗi server khi xóa thành viên"});
  }
}