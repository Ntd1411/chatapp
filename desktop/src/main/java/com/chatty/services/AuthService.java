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
    private String sessionCookie; // chưa phát triển
    private User currentUser;

    public AuthService() {
        this.apiService = new ApiService();
        this.userService = new UserService();
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
        JsonObject loginData = new JsonObject();
        loginData.addProperty("username", username);
        loginData.addProperty("password", password);

        JsonObject loginResponse = apiService.post("/auth/login", loginData, JsonObject.class);

        if(loginResponse == null || !loginResponse.has("token")){
            throw new IOException("Đăng nhập thất bại: không nhận được token");
        }

        String token = loginResponse.get("token").getAsString();
        ApiService.authToken = token;

        JsonObject meResponse = apiService.get("/auth/me", JsonObject.class, null);

        if(meResponse == null || !meResponse.has("user")){
            throw new IOException("Đăng nhập thất bại: không nhận được thông tin user");
        }

        Gson gson = new Gson();
        User user = gson.fromJson(meResponse.get("user"), User.class);

        user.setToken(token);
        this.currentUser = user;
        saveSessionCookie();

        return user;
    }

    // logic đăng ký
    public User signup(String username, String fullName, String email, String password) throws IOException {
        JsonObject signupData = new JsonObject();
        signupData.addProperty("username", username);
        signupData.addProperty("fullName", fullName);
        signupData.addProperty("email", email);
        signupData.addProperty("password", password);

        User user = apiService.post("/auth/signup", signupData, User.class);
        return user;
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
        clearSessionCookie();
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

    public void changePassword(String oldPassword, String newPassword) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("oldPassword", oldPassword);
        data.addProperty("newPassword", newPassword);

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
