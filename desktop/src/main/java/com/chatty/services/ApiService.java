package com.chatty.services;

import com.chatty.AppConfig;
import com.google.gson.Gson;
import okhttp3.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

// lớp đóng vai trò trung gian giao tiếp với backend
public class ApiService {
    private static final String BASE_URL = AppConfig.getApiUrl();
    private final OkHttpClient client;
    private final Gson gson;
    private final CookieJar cookieJar;
    public static String authToken = null;

    public ApiService() {
        this.cookieJar = new MemoryCookieJar();
        this.client = new OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .build();
        this.gson = new Gson();
        // Removed verbose logging that causes console lag
        // System.out.println("API Base URL: " + BASE_URL + " (Môi trường: " + AppConfig.getEnvironmentName() + ")");
    }

    private static class MemoryCookieJar implements CookieJar {
        private final ConcurrentHashMap<String, List<Cookie>> cookieStore = new ConcurrentHashMap<>();

        @Override
        public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
            cookieStore.put(url.host(), cookies);
        }

        @Override
        public List<Cookie> loadForRequest(HttpUrl url) {
            List<Cookie> cookies = cookieStore.get(url.host());
            return cookies != null ? cookies : new ArrayList<>();
        }
    }

    // GET
    public <T> T get(String endpoint, Class<T> responseClass) throws IOException {
        return get(endpoint, responseClass, null);
    }

    public <T> T get(String endpoint, Class<T> responseClass, String cookie) throws IOException {
        Request.Builder requestBuilder = new Request.Builder()
                .url(BASE_URL + endpoint)
                .get();

        if(authToken != null){
            requestBuilder.addHeader("Authorization", "Bearer " + authToken);
        }

        try (Response response = client.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                String errorMessage = extractErrorMessage(errorBody);
                throw new IOException(errorMessage);
            }
            String json = response.body().string();
            if (responseClass == String.class) {
                return (T) json;
            }
            return gson.fromJson(json, responseClass);
        }
    }

    // POST
    public <T> T post(String endpoint, Object body, Class<T> responseClass) throws IOException {
        String json = gson.toJson(body);
        RequestBody requestBody = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));
        
        Request.Builder requestBuilder = new Request.Builder()
                .url(BASE_URL + endpoint)
                .post(requestBody)
                .addHeader("Content-Type", "application/json");

        if(authToken != null){
            requestBuilder.addHeader("Authorization", "Bearer " + authToken);
        }

        Request request = requestBuilder.build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body().string();
                String errorMessage = extractErrorMessage(errorBody);
                throw new IOException(errorMessage);
            }
            String responseJson = response.body().string();
            return gson.fromJson(responseJson, responseClass);
        }
    }

    // PUT
    public <T> T put(String endpoint, Object body, Class<T> responseClass) throws IOException {
        String json = gson.toJson(body);
        RequestBody requestBody = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));

        Request.Builder requestBuilder = new Request.Builder()
                .url(BASE_URL + endpoint)
                .put(requestBody)
                .addHeader("Content-Type", "application/json");

        if (authToken != null) {
            requestBuilder.addHeader("Authorization", "Bearer " + authToken);
        }

        Request request = requestBuilder.build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                String errorMessage = extractErrorMessage(errorBody);
                throw new IOException(errorMessage);
            }

            String responseJson = response.body() != null ? response.body().string() : "";
            if (responseClass == String.class) {
                return (T) responseJson;
            }
            return gson.fromJson(responseJson, responseClass);
        }
    }

    // PATCH
    public <T> T patch(String endpoint, Object body, Class<T> responseClass) throws IOException {
        String json = gson.toJson(body);
        RequestBody requestBody = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));

        Request.Builder requestBuilder = new Request.Builder()
                .url(BASE_URL + endpoint)
                .patch(requestBody)
                .addHeader("Content-Type", "application/json");

        if (authToken != null) {
            requestBuilder.addHeader("Authorization", "Bearer " + authToken);
        }

        Request request = requestBuilder.build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                String errorMessage = extractErrorMessage(errorBody);
                throw new IOException(errorMessage);
            }

            if (responseClass == Void.class) return null;

            String responseJson = response.body() != null ? response.body().string() : "";
            if (responseClass == String.class) {
                return (T) responseJson;
            }
            return gson.fromJson(responseJson, responseClass);
        }
    }

    public <T> T postMultipart(String endpoint, java.io.File file, Class<T> responseClass) throws IOException {
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                        "file",
                        file.getName(),
                        RequestBody.create(file, MediaType.parse("image/*"))
                )
                .build();

        Request.Builder requestBuilder = new Request.Builder()
                .url(BASE_URL + endpoint)
                .post(requestBody);

        if (authToken != null) {
            requestBuilder.addHeader("Authorization", "Bearer " + authToken);
        }

        Request request = requestBuilder.build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("Multipart POST request failed: " + response + " - " + errorBody);
            }

            String responseJson = response.body() != null ? response.body().string() : "";
            return gson.fromJson(responseJson, responseClass);
        }
    }

    // DELETE
    public <T> T delete(String endpoint, Class<T> responseClass) throws IOException {
        Request.Builder requestBuilder = new Request.Builder()
                .url(BASE_URL + endpoint)
                .delete();

        if (authToken != null) {
            requestBuilder.addHeader("Authorization", "Bearer " + authToken);
        }

        Request request = requestBuilder.build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                String errorMessage = extractErrorMessage(errorBody);
                throw new IOException(errorMessage);
            }

            String responseJson = response.body() != null ? response.body().string() : "";
            if (responseJson.isEmpty() || responseClass == Void.class) {
                return null;
            }
            if (responseClass == String.class) {
                return (T) responseJson;
            }
            return gson.fromJson(responseJson, responseClass);
        }
    }

    public <T> T postWithCookies(String endpoint, Object body, Class<T> responseClass, String cookie) throws IOException {
        String json = gson.toJson(body);
        RequestBody requestBody = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));
        
        Request request = new Request.Builder()
                .url(BASE_URL + endpoint)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Cookie", cookie)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body().string();
                String errorMessage = extractErrorMessage(errorBody);
                throw new IOException(errorMessage);
            }
            String responseJson = response.body().string();
            return gson.fromJson(responseJson, responseClass);
        }
    }

    // ===== DIFFIE-HELLMAN KEY ENDPOINTS =====
    
    /**
     * Upload user's DH public exponent (g^a) to server.
     * Called after generating secret exponent during signup/login.
     */
    public void uploadDHPublicKey(String userId, String dhPublicKeyHex) throws IOException {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("dh_public_key", dhPublicKeyHex);
        
        post("/users/dh-key", body, com.google.gson.JsonObject.class);
    }

    /**
     * Fetch another user's DH public exponent (g^a) from server by user_id.
     * Used before encrypting/decrypting messages.
     */
    public String getDHPublicKey(String userId) throws IOException {
        com.google.gson.JsonObject response = get("/users/dh-key/" + userId, com.google.gson.JsonObject.class);
        if (response != null && response.has("dh_public_key")) {
            return response.get("dh_public_key").getAsString();
        }
        throw new IOException("DH public key not found for user: " + userId);
    }

    // Helper method to extract error message from JSON response
    private String extractErrorMessage(String errorBody) {
        if (errorBody == null || errorBody.isEmpty()) {
            return "Unknown error";
        }
        
        try {
            com.google.gson.JsonObject errorJson = gson.fromJson(errorBody, com.google.gson.JsonObject.class);
            if (errorJson != null && errorJson.has("message")) {
                return errorJson.get("message").getAsString();
            }
        } catch (Exception e) {
            // If JSON parsing fails, return the original error body
        }
        
        return errorBody;
    }

    public OkHttpClient getClient() {
        return client;
    }

    public Gson getGson() {
        return gson;
    }
}

