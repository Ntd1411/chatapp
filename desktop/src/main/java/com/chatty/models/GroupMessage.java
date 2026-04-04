package com.chatty.models;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class GroupMessage {
    private String _id;
    private String groupId;

     /*
     --- trước đây ---
     private String senderId;
      */

    // --- bây giờ ---
    @SerializedName("senderId")
    private User sender;

    private String content;
    private String fileUrl;
    private String replyTo;
    private String createdAt;
    private String updatedAt;
    private List<String> seenBy = new ArrayList<>();

    public GroupMessage() {}

    // kiểm tra tin nhắn đã được xem bởi người này chưa
    public boolean isSeenBy(String userId) {
        return seenBy != null && seenBy.contains(userId);
    }

    public String getSenderName() {
        if (sender != null) {
            return sender.getFullName() != null ? sender.getFullName() : sender.getUsername();
        }
        return "Unknown";
    }

    public String getSenderAvatar() {
        if (sender != null) {
            return sender.getAvatar();
        }
        return null;
    }

    // các getter và setter
    public String get_id() {
        return _id;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getSenderId() {
        return sender.get_id();
    }

    public void setSenderId(User sender) {
        this.sender = sender;
    }

    public User getSender() {
        return sender;
    }

    public void setSender(User sender) {
        this.sender = sender;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<String> getSeenBy() {
        return seenBy;
    }

    public void setSeenBy(List<String> seenBy) {
        this.seenBy = seenBy;
    }
}