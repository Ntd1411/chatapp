package com.chatty.models;

import java.util.ArrayList;
import java.util.List;

// hứng dữ liệu tin nhắn và phục vụ gửi nhận tin nhắn
public class Message {
    private String _id;
    private String senderId;
    private String receiverId;
    private String content;
    private String image; // gửi nhận tin nhắn chứa ảnh (chưa phát triển)
    private String createdAt;
    private List<String> seenBy = new ArrayList<>();

    public Message() {}

    public String get_id() {
        return _id;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public List<String> getSeenBy() {
        return seenBy;
    }

    public void setSeenBy(List<String> seenBy) {
        this.seenBy = seenBy;
    }

    public boolean isSeenBy(String userId){
        return seenBy != null && seenBy.contains(userId);
    }
}

