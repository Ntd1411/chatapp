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
    
    // NEW: Getter for DHService (used by HomeController for decryption)
    public DHService getDHService() {
        return dhService;
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
                
                System.out.println("[ChatService.getMessages] Loaded " + messages.size() + " messages from server");
                System.out.println("[ChatService.getMessages] DHService available: " + (dhService != null));
                System.out.println("[ChatService.getMessages] FriendId (other user): " + friendId);
                
                // NEW: Decrypt messages if DH service is available
                if (dhService != null) {
                    for (int idx = 0; idx < messages.size(); idx++) {
                        Message msg = messages.get(idx);
                        try {
                            System.out.println("[ChatService] Decrypting message " + idx + " from sender: " + msg.getSenderId());
                            System.out.println("[ChatService] Encrypted content: " + msg.getContent().substring(0, Math.min(16, msg.getContent().length())) + "...");
                            
                            // NEW: Use friendId (the other user in conversation) for key derivation
                            // This ensures we derive the SAME key regardless of whether we sent or received the message
                            String desKey = dhService.prepareMessageDecryption(friendId);
                            System.out.println("[ChatService] DES key derived: " + desKey.substring(0, 8) + "...");
                            
                            String decryptedContent = cryptoService.desDecrypt(msg.getContent(), desKey);
                            System.out.println("[ChatService] Decrypted bytes length: " + decryptedContent.length());
                            
                            // Log byte-by-byte as hex
                            StringBuilder hexView = new StringBuilder();
                            for (int i = 0; i < decryptedContent.length(); i++) {
                                hexView.append(String.format("%02x ", (int) decryptedContent.charAt(i)));
                            }
                            System.out.println("[ChatService] Decrypted bytes (hex): " + hexView.toString());
                            System.out.println("[ChatService] Decrypted raw content: '" + decryptedContent + "'");
                            
                            // Try to strip PKCS#7 padding if it exists
                            String cleanedContent = decryptedContent;
                            if (decryptedContent.length() > 0) {
                                // PKCS#7 padding: last byte indicates padding length
                                try {
                                    int lastByte = (int) decryptedContent.charAt(decryptedContent.length() - 1);
                                    System.out.println("[ChatService] Last byte value: 0x" + String.format("%02x", lastByte) + " (" + lastByte + ")");
                                    if (lastByte > 0 && lastByte <= 8 && decryptedContent.length() >= lastByte) {
                                        cleanedContent = decryptedContent.substring(0, decryptedContent.length() - lastByte);
                                        System.out.println("[ChatService] Stripped " + lastByte + " padding bytes");
                                        System.out.println("[ChatService] Cleaned content: '" + cleanedContent + "'");
                                    } else {
                                        System.out.println("[ChatService] Last byte doesn't look like valid PKCS#7 padding");
                                    }
                                } catch (Exception e) {
                                    System.err.println("[ChatService] Failed to strip padding: " + e.getMessage());
                                }
                            }
                            
                            System.out.println("[ChatService] Decrypted successfully: " + cleanedContent);
                            msg.setContent(cleanedContent);
                        } catch (Exception e) {
                            System.err.println("[ChatService] Failed to decrypt message " + idx + ": " + e.getMessage());
                            e.printStackTrace();
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
            System.out.println("[SendMessage] Message content is empty, ignoring");
            return null;
        }

        try {
            System.out.println("[SendMessage] Starting...");
            System.out.println("   From: " + senderId);
            System.out.println("   To: " + receiverId);
            System.out.println("   Content length: " + content.length());
            
            // NEW: Prepare message encryption using DH if available
            String encryptedContent = content;
            if (dhService != null) {
                System.out.println("[Encryption] Preparing DH encryption...");
                String desKey = dhService.prepareMessageEncryption(receiverId);
                System.out.println("[Encryption] DES Key generated: " + desKey.substring(0, 8) + "...");
                
                System.out.println("[Encryption] Plaintext: '" + content + "' (length: " + content.length() + ")");
                
                // Log plaintext as hex bytes
                StringBuilder hexView = new StringBuilder();
                for (int i = 0; i < content.length(); i++) {
                    hexView.append(String.format("%02x ", (int) content.charAt(i)));
                }
                System.out.println("[Encryption] Plaintext bytes (hex): " + hexView.toString());
                
                encryptedContent = cryptoService.desEncrypt(content, desKey);
                System.out.println("[Encryption] Ciphertext returned: '" + encryptedContent + "' (length: " + encryptedContent.length() + ")");
                System.out.println("[Encryption] Message encrypted, new length: " + encryptedContent.length());
            } else {
                System.out.println("[Encryption] DHService not available, sending plaintext");
            }

            // tạo tin nhắn mới
            Message localMsg = new Message();
            localMsg.set_id(String.valueOf(System.currentTimeMillis()));
            System.out.println("[Message] Created with ID: " + localMsg.get_id());

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
            System.out.println("[Socket] Sending message via socket...");
            socketService.sendMessage(receiverId, encryptedContent);
            System.out.println("[Socket] Message sent successfully!");

            return localMsg;
        } catch (Exception e) {
            System.err.println("[SendMessage] Failed to send message: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}

