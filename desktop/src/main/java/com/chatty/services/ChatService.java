package com.chatty.services;

import com.chatty.models.Message;
import com.chatty.models.User;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// phục vụ nhắn tin giữa các cá nhân và nhắn tin nhóm
public class ChatService {
    private final ApiService apiService;
    private final SocketService socketService;
    private final Gson gson;

    public ChatService(SocketService socketService) {
        this.apiService = new ApiService();
        this.socketService = socketService;

        // dùng GsonBuilder để đăng ký deserializer
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Message.class, new MessageDeserializer())
                .create();
    }

    // lấy danh sách tất cả người dùng
    public List<User> getUsers() throws IOException {
        try {
            JsonObject response = apiService.get("/messages/users", JsonObject.class, null);

            if(response != null && response.has("users")){
                JsonArray userArray = response.getAsJsonArray("users");

                Type listType = new TypeToken<List<User>>(){}.getType();
                System.out.println(userArray);
                return gson.fromJson(userArray, listType);
            }

            return new ArrayList<>();
        } catch (Exception e){
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // tìm kiếm người dùng theo tên dựa trên từ khóa truyền vào
    public List<User> searchUser(String searchTerm) throws IOException {
        try {
            String endpoint = "/users/search?keyword=" + searchTerm;
            JsonObject response = apiService.get(endpoint, JsonObject.class);

            if (response != null && response.has("users")){
                JsonArray userArray = response.getAsJsonArray("users");

                Type listType = new TypeToken<List<User>>(){}.getType();
                return gson.fromJson(userArray, listType);
            }

            return new ArrayList<>();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    // tải toàn bộ tin nhắn với 1 người dùng cụ thể
    public List<Message> getMessages(String friendId) throws IOException {
        try {
            JsonObject response = apiService.get("/messages/" + friendId, JsonObject.class, null);

            if(response != null && response.has("messages")){
                JsonArray messageArray = response.getAsJsonArray("messages");

                Type listType = new TypeToken<List<Message>>(){}.getType();
                return gson.fromJson(messageArray, listType);
            }

            return new ArrayList<>();
        } catch (Exception e){
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // gửi tin nhắn
    public Message sendMessage(String senderId, String receiverId, String content){
        if(content == null || content.trim().isEmpty()){
            return null;
        }

        // tạo tin nhắn mới
        Message localMsg = new Message();
        localMsg.set_id(String.valueOf(System.currentTimeMillis()));

        // tạo một đối tượng User cho người gửi
        User senderUser = new User();
        senderUser.set_id(senderId);

        // gán cả đối tượng User vào tin nhắn
        localMsg.setSenderId(senderUser.get_id());

        // gán các thông tin còn lại
        localMsg.setReceiverId(receiverId);
        localMsg.setContent(content);
        localMsg.setCreatedAt(Instant.now().toString());

        // gửi qua socket
        socketService.sendMessage(receiverId, content);

        return localMsg;
    }
}

