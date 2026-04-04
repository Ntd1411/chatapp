package com.chatty.models;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

// chứa toàn bộ thông tin 1 người dùng cụ thể
public class User {
    private String _id;
    private String username;
    private String fullName;
    private String email;
    private String avatar;
    private String token;

    // các property khi nhắn tin cá nhân

    // property báo online
    private final BooleanProperty isOnline = new SimpleBooleanProperty(false);
    // property báo đang nhập
    private final BooleanProperty isTyping = new SimpleBooleanProperty(false);
    // property báo trạng thái tin nhắn (đã xem/ đã gửi)
    private final StringProperty statusPreview = new SimpleStringProperty("");

    // đếm số tin chưa đọc
    private int unreadCount;
    // chứa dữ liệu về tin nhắn cuối cùng trong cuộc trò chuyện
    private LastMessage lastMessage;

    public User() {}

    public User(String _id, String username, String fullName, String email, String avatar) {
        this._id = _id;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.avatar = avatar;
    }

    // lớp hứng dữ liệu tin nhắn cuối cùng khi nhắn tin cá nhân từ backend
    public static class LastMessage{
        private String content;
        private String createdAt;
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

        public boolean isMine() {
            return isMine;
        }

        public void setIsMine(boolean isMine) {
            this.isMine = isMine;
        }
    }

    // hàm cập nhật trạng thái nhắn tin
    public void updateStatusPreview(){
        if(isTyping.get()){
            statusPreview.set("Đang soạn tin...");
        } else {
            if(lastMessage != null && lastMessage.getContent() != null){
                String prefix = lastMessage.isMine() ? "Bạn: " : "";

                // cắt bớt nếu tin dài
                String content = lastMessage.getContent();
                if(content.length() > 25) content = content.substring(0, 25) + "...";
                statusPreview.set(prefix + content);
            } else {
                statusPreview.set("Chạm để bắt đầu chat");
            }
        }
    }

    // các getter và setter

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String get_id() {
        return _id;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public boolean isOnline() {
        return isOnline.get();
    }

    public BooleanProperty isOnlineProperty() {
        return isOnline;
    }

    public void setOnline(boolean online){
        this.isOnline.set(online);
    }

    public void setTyping(boolean typing) {
        this.isTyping.set(typing);
        updateStatusPreview();
    }

    public BooleanProperty isTypingProperty() {
        return isTyping;
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
        updateStatusPreview();
    }
}