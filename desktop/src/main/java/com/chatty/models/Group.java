package com.chatty.models;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import javafx.beans.property.*;
import java.util.ArrayList;
import java.util.List;

public class Group {
    private String _id;
    private String name;
    private String description;
    private String avatar;
    private JsonElement owner;
    private List<GroupMember> members = new ArrayList<>();
    private int unreadCount; // Sửa từ IntegerProperty thành int
    private LastMessage lastMessage;

    // các property này là "transient" - GSON sẽ bỏ qua chúng
    // property cho trạng thái đã gửi, đã xem
    private final transient StringProperty statusPreview = new SimpleStringProperty("");

    // property cho trạng thái đang soạn tin
    private final transient BooleanProperty isTyping = new SimpleBooleanProperty(false);

    // lớp dung để hứng dữ liệu danh sách thành viên nhóm từ backend
    public static class GroupMember {
        @SerializedName("userId")
        private User user;
        private String role; // owner, admin, member
        private String joinedAt;

        public GroupMember() {}

        public User getUser() {
            return user;
        }

        public void setUser(User user) {
            this.user = user;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getJoinedAt() {
            return joinedAt;
        }

        public void setJoinedAt(String joinedAt) {
            this.joinedAt = joinedAt;
        }

        public boolean isAdmin() {
            return "admin".equals(role);
        }
    }

    // lớp hứng dữ liệu về tin nhắn cuối cùng trong nhóm trả về từ backend
    public static class LastMessage {
        private String content;
        private String createdAt;
        private String senderName;
        private boolean isMine;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        public String getSenderName() {
            return senderName;
        }

        public void setSenderName(String senderName) {
            this.senderName = senderName;
        }

        public boolean isMine() {
            return isMine;
        }

        public void setIsMine(boolean isMine) {
            this.isMine = isMine;
        }
    }

    public Group() {}

    // logic cập nhật trạng thái tin nhắn cuối trong nhóm
    public void updateStatusPreview() {
        if (isTyping.get()) {
            statusPreview.set("Đang soạn tin...");
        } else {
            if (lastMessage != null && lastMessage.getContent() != null) {
                String prefix = lastMessage.isMine() ? "Bạn: " : lastMessage.getSenderName() + ": ";
                String content = lastMessage.getContent();
                if (content.length() > 25) {
                    content = content.substring(0, 25) + "...";
                }
                statusPreview.set(prefix + content);
            } else {
                statusPreview.set("Chạm để bắt đầu chat");
            }
        }
    }

    // Check if current user is admin
    public boolean isUserAdmin(String userId) {
        return members.stream()
                .anyMatch(m -> m.getUser() != null
                        && m.getUser().get_id().equals(userId)
                        && m.isAdmin());
    }

    // Check if current user is owner
    public boolean isUserOwner(String userId) {
        User u = getOwner(); // Gọi hàm getter thông minh bên dưới
        return u != null && u.get_id().equals(userId);
    }

    // Getters and Setters
    public String get_id() {
        return _id;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public User getOwner() {
        if (owner == null || owner.isJsonNull()) return null;

        // Trường hợp 1: API trả về Object (User đầy đủ) - ví dụ: getInfoGroup, createGroup
        if (owner.isJsonObject()) {
            return new Gson().fromJson(owner, User.class);
        }

        // Trường hợp 2: API trả về String (Chỉ có ID) - ví dụ: getGroups
        if (owner.isJsonPrimitive()) {
            User tempUser = new User();
            tempUser.set_id(owner.getAsString());
            return tempUser;
        }

        return null;
    }

    // 4. Setter cũng cần sửa để nhận JsonElement (hoặc bạn có thể overload)
    public void setOwner(JsonElement owner) {
        this.owner = owner;
    }

    // Helper để set owner bằng String ID (nếu cần dùng thủ công)
    public void setOwnerId(String id) {
        this.owner = new com.google.gson.JsonPrimitive(id);
    }

    public List<GroupMember> getMembers() {
        return members;
    }

    public void setMembers(List<GroupMember> members) {
        this.members = members;
    }

    public int getMemberCount() {
        return members != null ? members.size() : 0;
    }

    // JavaFX Properties
    public boolean isTyping() {
        return isTyping.get();
    }

    public BooleanProperty isTypingProperty() {
        return isTyping;
    }

    public void setTyping(boolean typing) {
        this.isTyping.set(typing);
        updateStatusPreview();
    }

    public StringProperty statusPreviewProperty() {
        return statusPreview;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }

    public LastMessage getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(LastMessage lastMessage) {
        this.lastMessage = lastMessage;
    }

    public String getStatusPreview() {
        return statusPreview.get();
    }

    public boolean isIsTyping() {
        return isTyping.get();
    }
}
