package com.chatty.services;

import com.chatty.models.Group;
import com.chatty.models.GroupMessage;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// phục vụ quản lý các hoạt động liên quan đến nhóm
public class GroupService {
    private final ApiService apiService;
    private final SocketService socketService;
    private final Gson gson;

    public GroupService(SocketService socketService) {
        this.apiService = new ApiService();
        this.socketService = socketService;
        this.gson = new Gson();
    }

    // ==================== GROUP CRUD ====================

    // lấy danh sách tất cả các nhóm của người dùng
    public List<Group> getGroups() throws IOException {
        try {
            JsonObject response = apiService.get("/groups/getGroups", JsonObject.class, null);

            if (response != null && response.has("groups")) {
                JsonArray groupArray = response.getAsJsonArray("groups");
                Type listType = new TypeToken<List<Group>>(){}.getType();
                return gson.fromJson(groupArray, listType);
            }

            return new ArrayList<>();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    // tạo nhóm
    public Group createGroup(String name, String description, List<String> memberIds) throws IOException {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("name", name);
            payload.addProperty("description", description);

            JsonArray membersArray = new JsonArray();
            for (String id : memberIds) {
                membersArray.add(id);
            }
            payload.add("members", membersArray);

            JsonObject response = apiService.post("/groups/create", payload, JsonObject.class);

            if (response != null && response.has("group")) {
                Group group = gson.fromJson(response.get("group"), Group.class);

                // tự động tham gia vào nhóm sau khi tạo
                if (group != null) {
                    socketService.joinGroup(group.get_id());
                }

                return group;
            }

            return null;
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    // cập nhật thông tin nhóm
    public void updateGroup(String groupId, String newName, String newDesc, String newAvatarUrl) throws IOException {
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("name", newName);
        updateData.put("description", newDesc);
        updateData.put("avatar", newAvatarUrl);

        apiService.patch("/groups/update/" + groupId, updateData, Void.class);
    }

    // lấy thông tin chi tiết của nhóm
    public Group getGroupInfo(String groupId) throws IOException {
        try {
            JsonObject response = apiService.get("/groups/" + groupId, JsonObject.class, null);

            if (response != null && response.has("group")) {
                return gson.fromJson(response.get("group"), Group.class);
            }

            return null;
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    // xóa nhóm (với owner) hoặc rời nhóm (với admin/member)
    public boolean deleteGroup(String groupId) throws IOException {
        try {
            apiService.delete("/groups/delete/" + groupId, JsonObject.class);
            socketService.leaveGroup(groupId);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    // ==================== GROUP MESSAGES ====================

    // lấy toàn bộ tin nhắn nhóm
    public List<GroupMessage> getGroupMessages(String groupId) throws IOException {
        try {
            JsonObject response = apiService.get("/groups/" + groupId + "/messages", JsonObject.class, null);

            if (response != null && response.has("messages")) {
                JsonArray messageArray = response.getAsJsonArray("messages");
                Type listType = new TypeToken<List<GroupMessage>>(){}.getType();
                return gson.fromJson(messageArray, listType);
            }

            return new ArrayList<>();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    // gửi tin nhắn nhóm
    public GroupMessage sendGroupMessage(String groupId, String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }

        // tạo tin nhắn cục bộ để cập nhật UI ngay lập tức
        GroupMessage localMsg = new GroupMessage();
        localMsg.set_id(String.valueOf(System.currentTimeMillis()));
        localMsg.setGroupId(groupId);
        localMsg.setContent(content);
        localMsg.setCreatedAt(Instant.now().toString());

        // gửi tin nhắn qua socket
        socketService.sendGroupMessage(groupId, content);

        return localMsg;
    }

    // ==================== GROUP MEMBERS ====================

    // lấy danh sách thành viên của nhóm
    public List<Group.GroupMember> getGroupMembers(String groupId) throws IOException {
        try {
            JsonObject response = apiService.get("/groups/" + groupId + "/getMembers", JsonObject.class, null);

            if (response != null && response.has("members")) {
                JsonArray memberArray = response.getAsJsonArray("members");
                Type listType = new TypeToken<List<Group.GroupMember>>(){}.getType();
                return gson.fromJson(memberArray, listType);
            }

            return new ArrayList<>();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    // thêm thành viên vào nhóm
    public boolean addMembers(String groupId, List<String> memberIds) throws IOException {
        try {
            JsonObject payload = new JsonObject();
            JsonArray membersArray = new JsonArray();
            for (String id : memberIds) {
                membersArray.add(id);
            }
            payload.add("memberIds", membersArray);

            apiService.post("/groups/" + groupId + "/addMembers", payload, JsonObject.class);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    // xóa thành viên khỏi nhóm (dành cho owner và admin)
    public boolean removeMember(String groupId, String memberId) throws IOException {
        try {
            apiService.delete("/groups/" + groupId + "/deleteMembers/" + memberId, JsonObject.class);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    // phân quyền thành viên nhóm (dành cho owner)
    public boolean changeRole(String groupId, String memberId, String newRole) throws IOException {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("newRole", newRole);

            apiService.patch("/groups/" + groupId + "/changeRole/" + memberId, payload, JsonObject.class);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    // lớp nội bộ đơn giản để Gson parse JSON response của việc upload
    private static class UploadResponse {
        private String url;

        public String getUrl() {
            return url;
        }
    }

    // đẩy ảnh đại diện nhóm lên đám mây
    public String uploadGroupAvatar(File file) throws IOException {
        UploadResponse response = apiService.postMultipart("/messages/upload", file, UploadResponse.class);
        if (response != null && response.getUrl() != null) {
            return response.getUrl();
        }
        throw new IOException("API không trả về URL của ảnh sau khi upload.");
    }

    // ==================== TYPING INDICATORS (CHƯA HOÀN THIỆN) ====================

    // báo hiệu bắt đầu nhập tin
    public void startTyping(String groupId) {
        socketService.emitGroupTypingStart(groupId);
    }

    // báo hiệu dừng nhập tin
    public void stopTyping(String groupId) {
        socketService.emitGroupTypingStop(groupId);
    }

    // báo hiệu đã xem tin nhắn
    public void markMessageAsSeen(String messageId, String groupId) {
        socketService.emitSeenGroupMessage(messageId, groupId);
    }

    // ==================== HELPER METHODS ====================

    // tham gia nhóm
    public void joinGroupRoom(String groupId) {
        socketService.joinGroup(groupId);
    }

    // rời nhóm
    public void leaveGroupRoom(String groupId) {
        socketService.leaveGroup(groupId);
    }
}