package com.chatty.services;

import com.chatty.models.User;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import okhttp3.Cookie;
import okhttp3.HttpUrl;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class AuthService {
    private final ApiService apiService;
    private final UserService userService;
    private final CryptoService cryptoService;
    private DHService dhService;  // NEW: Diffie-Hellman service
    private String sessionCookie; // chưa phát triển
    private User currentUser;

    public AuthService() {
        this.apiService = new ApiService();
        this.userService = new UserService();
        this.cryptoService = new CryptoService();
        this.dhService = new DHService(apiService, cryptoService);  // NEW: Initialize DH service
        this.sessionCookie = loadSessionCookie();
    }

    // xác thực người dùng, phục vụ đăng nhập tự động
    public User checkAuth() {
        if(ApiService.authToken == null || ApiService.authToken.isEmpty()) return  null;

        try {
            JsonObject response = apiService.get("/auth/me", JsonObject.class, null);

            if(response != null && response.has("user")){
                Gson gson = new Gson();
                User user = gson.fromJson(response.get("user"), User.class);

                user.setToken(ApiService.authToken);

                this.currentUser = user;
                
                // NEW: Initialize DH on auto login
                initializeDHService(user);

                return user;
            }
        } catch (Exception e){
            System.out.println("Token không hợp lệ hoặc đã hết hạn");
            ApiService.authToken = null;
            this.currentUser = null;
        }

        return null;
    }

    // logic đăng nhập
    public User login(String username, String password) throws IOException {
        try {
            // Hash password sử dụng kernel crypto driver
            String hashedPassword = cryptoService.sha1Hash(password);
            
            JsonObject loginData = new JsonObject();
            loginData.addProperty("username", username);
            loginData.addProperty("password", hashedPassword);

            JsonObject loginResponse = apiService.post("/auth/login", loginData, JsonObject.class);

            if(loginResponse == null || !loginResponse.has("token")){
                throw new IOException("Không nhận được token từ server");
            }

            String token = loginResponse.get("token").getAsString();
            ApiService.authToken = token;

            JsonObject meResponse = apiService.get("/auth/me", JsonObject.class, null);

            if(meResponse == null || !meResponse.has("user")){
                throw new IOException("Không nhận được thông tin user");
            }

            Gson gson = new Gson();
            User user = gson.fromJson(meResponse.get("user"), User.class);

            user.setToken(token);
            this.currentUser = user;
            
            // NEW: Initialize DH on login
            initializeDHService(user);
            
            saveSessionCookie();

            return user;
        } catch (ExceptionInInitializerError e) {
            throw new IOException("Lỗi crypto: Kernel driver không khả dụng - " + e.getMessage());
        } catch (NullPointerException e) {
            throw new IOException("Lỗi crypto: CryptoService chưa được khởi tạo");
        }
    }

    // logic đăng ký
    public User signup(String username, String fullName, String email, String password) throws IOException {
        try {
            // Hash password sử dụng kernel crypto driver
            String hashedPassword = cryptoService.sha1Hash(password);
            
            JsonObject signupData = new JsonObject();
            signupData.addProperty("username", username);
            signupData.addProperty("fullName", fullName);
            signupData.addProperty("email", email);
            signupData.addProperty("password", hashedPassword);

            User user = apiService.post("/auth/signup", signupData, User.class);
            
            // NEW: Initialize DH after signup
            // Note: After signup, you'll likely call login() to get the token
            // DHService initialization happens in login(), so we don't do it here
            // But you could initialize it here if you want to:
            // dhService = new DHService(apiService, cryptoService);
            // dhService.generateSecretExponent();
            // dhService.uploadPublicExponent(user.get_id(), user.getUsername());
            
            return user;
        } catch (ExceptionInInitializerError e) {
            throw new IOException("Lỗi crypto: Kernel driver không khả dụng - " + e.getMessage());
        } catch (NullPointerException e) {
            throw new IOException("Lỗi crypto: CryptoService chưa được khởi tạo");
        }
    }

    // logic đăng xuất
    public void logout() {
        try {
            JsonObject empty = new JsonObject();
            apiService.post("/auth/logout", empty, JsonObject.class);
        } catch (IOException e) {
            // Ignore
        }
        this.currentUser = null;
        this.sessionCookie = null;
        if (this.dhService != null) {
            this.dhService.clearCache();  // Clear DES key cache (in-memory only)
            // NOTE: Do NOT delete stored secret exponent on logout!
            // User should be able to read old messages on next login
            // this.dhService.deleteStoredSecretExponent();
        }
        clearSessionCookie();
    }

    // NEW: Helper method to initialize DH service on login/auto-login
    // WARNING: This is CPU-intensive and network I/O intensive, must be called on background thread!
    private void initializeDHService(User user) throws IOException {
        long initStart = System.currentTimeMillis();
        System.out.println("[AuthService] ⚠ Initializing DHService for: " + user.getUsername() + " (may take 1-3 seconds)");
        
        // NEW: Set user ID first (for per-user secret exponent file)
        dhService.setUserId(user.get_id());
        
        // Try to load existing secret exponent from local storage
        boolean loaded = dhService.loadSecretExponentFromStorage();
        
        if (!loaded) {
            // First time login: generate new secret exponent (VERY CPU-intensive, ~1-2 seconds)
            long genStart = System.currentTimeMillis();
            System.out.println("[AuthService] First time login: generating new secret exponent...");
            dhService.generateSecretExponent();
            long genTime = System.currentTimeMillis() - genStart;
            System.out.println("[AuthService] Secret exponent generated in " + genTime + "ms");
        } else {
            System.out.println("[AuthService] ✓ Existing secret exponent loaded from storage");
        }
        
        // Always re-upload to ensure server has the correct g^a for this user
        long uploadStart = System.currentTimeMillis();
        System.out.println("[AuthService] Uploading DH public key to server...");
        dhService.uploadPublicExponent(user.get_id(), user.getUsername());
        long uploadTime = System.currentTimeMillis() - uploadStart;
        System.out.println("[AuthService] DH public key uploaded in " + uploadTime + "ms");
        
        long totalTime = System.currentTimeMillis() - initStart;
        System.out.println("[AuthService] =====================================================" );
        System.out.println("[AuthService] DHService initialization completed in " + totalTime + "ms");
        System.out.println("[AuthService] =====================================================" );
    }

    public void setCurrentUser(User currentUser) {
        this.currentUser = currentUser;
    }

    public User getCurrentUser() {
        try {
            return userService.getCurrentUserInfo();
        } catch (IOException e) {
            System.out.println("Không lấy được toàn bộ thông tin");
            return currentUser;
        }
    }

    public String getToken() {
        return ApiService.authToken;
    }
    
    // NEW: Getter for DHService (used by ChatService)
    public DHService getDHService() {
        return dhService;
    }
    
    // NEW: Re-initialize DHService after login (call this from HomeController.show())
    public void reinitializeDHService(User user) {
        if (user != null) {
            try {
                System.out.println("[AuthService] Re-initializing DHService for user: " + user.getUsername());
                this.currentUser = user;  // NEW: Update currentUser
                initializeDHService(user);
                System.out.println("[AuthService] DHService re-initialized successfully");
            } catch (IOException e) {
                System.err.println("[AuthService] Failed to re-initialize DHService: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.err.println("[AuthService] Cannot re-initialize DHService: user is null");
        }
    }

    public void changePassword(String oldPassword, String newPassword) throws IOException {
        // Hash passwords sử dụng kernel crypto driver
        String hashedOldPassword = cryptoService.sha1Hash(oldPassword);
        String hashedNewPassword = cryptoService.sha1Hash(newPassword);
        
        JsonObject data = new JsonObject();
        data.addProperty("oldPassword", hashedOldPassword);
        data.addProperty("newPassword", hashedNewPassword);

        // We expect a success response, otherwise ApiService throws IOException
        apiService.patch("/users/change-password", data, JsonObject.class);
    }

    private void saveSessionCookie() {
        // In a real app, save cookie to persistent storage
    }

    private String loadSessionCookie() {
        // In a real app, load cookie from persistent storage
        return null;
    }

    private void clearSessionCookie() {
        // In a real app, clear cookie from persistent storage
    }
}
