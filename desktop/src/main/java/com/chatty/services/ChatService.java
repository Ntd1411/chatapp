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
    private final CryptoService cryptoService;  // NEW: For encryption/decryption
    private final Gson gson;
    private DHService dhService;  // NEW: Diffie-Hellman service (set after init)

    public ChatService(SocketService socketService) {
        this.apiService = new ApiService();
        this.socketService = socketService;
        this.cryptoService = new CryptoService();  // NEW
        this.dhService = null;  // Will be set via setDHService()

        // dùng GsonBuilder để đăng ký deserializer
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Message.class, new MessageDeserializer())
                .create();
    }
    
    // NEW: Setter for DHService (called from AuthService after login)
    public void setDHService(DHService dhService) {
        this.dhService = dhService;
    }

    // NEW: Helper method to filter users that have uploaded their DH public key
    private List<User> filterUsersWithValidDHKey(List<User> users) {
        List<User> validUsers = new ArrayList<>();
        
        for (User user : users) {
            // Check if user has dh_public_key from server response
            if (user.getDhPublicKey() != null && !user.getDhPublicKey().isEmpty()) {
                validUsers.add(user);  // ✓ User đã đăng nhập
            }
        }
        
        return validUsers;
    }

    // lấy danh sách tất cả người dùng
    public List<User> getUsers() throws IOException {
        try {
            JsonObject response = apiService.get("/messages/users", JsonObject.class, null);

            if(response != null && response.has("users")){
                JsonArray userArray = response.getAsJsonArray("users");

                Type listType = new TypeToken<List<User>>(){}.getType();
                System.out.println(userArray);
                List<User> allUsers = gson.fromJson(userArray, listType);
                
                // NEW: Filter only users with valid DH public key
                return filterUsersWithValidDHKey(allUsers);
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
                List<User> allUsers = gson.fromJson(userArray, listType);
                
                // NEW: Filter only users with valid DH public key
                return filterUsersWithValidDHKey(allUsers);
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
                List<Message> messages = gson.fromJson(messageArray, listType);
                
                // NEW: Decrypt messages if DH service is available
                if (dhService != null) {
                    for (Message msg : messages) {
                        try {
                            // msg.getSenderId() returns String directly
                            String senderId = msg.getSenderId();
                            
                            String desKey = dhService.prepareMessageDecryption(senderId);
                            String decryptedContent = cryptoService.desDecrypt(msg.getContent(), desKey);
                            msg.setContent(decryptedContent);
                        } catch (Exception e) {
                            System.err.println("Failed to decrypt message: " + e.getMessage());
                            // Keep encrypted content if decryption fails
                        }
                    }
                }
                
                return messages;
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

        try {
            // NEW: Prepare message encryption using DH if available
            String encryptedContent = content;
            if (dhService != null) {
                String desKey = dhService.prepareMessageEncryption(receiverId);
                encryptedContent = cryptoService.desEncrypt(content, desKey);
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
            localMsg.setContent(encryptedContent);  // Send encrypted content
            localMsg.setCreatedAt(Instant.now().toString());

            // gửi qua socket
            socketService.sendMessage(receiverId, encryptedContent);

            return localMsg;
        } catch (Exception e) {
            System.err.println("Failed to send message: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}

