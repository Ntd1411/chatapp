const Message = require('../../models/message.model');
const Group = require('../../models/group.model');

module.exports = (io, socket) => {
  socket.on("send-group-message", async (data, updateStatus) => {
    try {
      const { groupId, content, replyTo, fileUrl } = data;
      const senderId = socket.user._id;

      // Validate
      if (!groupId || !content.trim()) {
        return updateStatus({ success: false, message: "Dữ liệu không hợp lệ" });
      }

      // Lưu DB
      const message = await Message.create({
        senderId,
        groupId,
        content: content.trim(),
        replyTo,
        attachments: fileUrl || null
      });

      // Populate sender info
      await message.populate("senderId", "username fullName avatar");

      updateStatus({ success: true });

      // Gửi tới tất cả người trong group
      io.to(`group-${groupId}`).emit("receive-group-message", {
        _id: message._id,
        senderId: message.senderId,
        groupId: groupId,
        content: message.content,
        replyTo,
        attachments: message.attachments,
        createdAt: message.createdAt
      });

    } catch (error) {
      console.error("Error sending group message:", error);
      updateStatus({ success: false, message: error.message });
    }
  });

  // Xem tin nhắn trong group
  socket.on("seen-group-message", async (data) => {
    try {
      const { messageId, groupId } = data;
      const userId = socket.user._id;

      const message = await Message.findByIdAndUpdate(
        messageId,
        { $addToSet: { seenBy: userId } },
        { new: true }
      );

      if (message) {
        io.to(`group-${groupId}`).emit("user-seen-message", {
          messageId,
          userId: userId.toString(),
          seenBy: message.seenBy
        });
      }
    } catch (error) {
      console.error("Error marking seen:", error);
    }
  });

  // User bắt đầu gõ trong group
  socket.on("group-typing-start", (data) => {
    const { groupId } = data;
    if (!groupId) return;

    io.to(`group-${groupId}`).emit("group-typing-start", {
      senderId: socket.user._id.toString(),
      senderName: socket.user.fullName || socket.user.username
    });
  });

  // User dừng gõ trong group
  socket.on("group-typing-stop", (data) => {
    const { groupId } = data;
    if (!groupId) return;

    io.to(`group-${groupId}`).emit("group-typing-stop", {
      senderId: socket.user._id.toString()
    });
  });

  socket.on("join-group", (data) => {
    const groupId = typeof data === 'string' ? data : data.groupId;
    socket.join(`group-${groupId}`);
  });

  socket.on("leave-group", (data) => {
      const groupId = typeof data === 'string' ? data : data.groupId;
      socket.leave(`group-${groupId}`);
  });

  socket.on("group-created", (data) => {
    const { groupId, members } = data;
    if (!groupId || !members) return;

    // Broadcast to all members that a new group was created
    members.forEach(memberId => {
      io.to(`${memberId}`).emit("reload-groups");
    });
  });

  socket.on("group-deleted", (data) => {
    const { groupId, members } = data;
    if (!groupId || !members) return;

    // Broadcast to all members that group was deleted
    members.forEach(memberId => {
      io.to(`${memberId}`).emit("reload-groups");
    });
  });
};