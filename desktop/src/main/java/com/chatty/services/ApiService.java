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
        System.out.println("API Base URL: " + BASE_URL + " (Môi trường: " + AppConfig.getEnvironmentName() + ")");
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
                throw new IOException("Unexpected code " + response + ": " + errorBody);
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
                throw new IOException("Request failed: " + errorBody);
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
                throw new IOException("PUT request failed: " + response + " - " + errorBody);
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
                throw new IOException("PATCH request failed: " + response + " - " + errorBody);
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
                throw new IOException("DELETE request failed: " + response + " - " + errorBody);
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
                throw new IOException("Request failed: " + errorBody);
            }
            String responseJson = response.body().string();
            return gson.fromJson(responseJson, responseClass);
        }
    }

    public OkHttpClient getClient() {
        return client;
    }

    public Gson getGson() {
        return gson;
    }
}

