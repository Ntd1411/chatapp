package com.chatty.services;

import com.chatty.models.Message;
import com.chatty.models.User;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

// lớp hỗ trợ desktop app xử lý linh hoạt dữ liệu trả về cho trường senderId từ backend (User hoặc String)
public class MessageDeserializer implements JsonDeserializer<Message> {

    @Override
    public Message deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();

        // tạo một đối tượng Message trống
        Message message = new Message();

        // đọc và gán các trường đơn giản, kiểm tra null để an toàn
        if (jsonObject.has("_id")) message.set_id(jsonObject.get("_id").getAsString());
        if (jsonObject.has("receiverId")) message.setReceiverId(jsonObject.get("receiverId").getAsString());
        if (jsonObject.has("content")) message.setContent(jsonObject.get("content").getAsString());
        if (jsonObject.has("image") && !jsonObject.get("image").isJsonNull()) {
            message.setImage(jsonObject.get("image").getAsString());
        }
        if (jsonObject.has("createdAt")) message.setCreatedAt(jsonObject.get("createdAt").getAsString());

        // xử lý trường 'seenBy' (là một mảng)
        if (jsonObject.has("seenBy") && jsonObject.get("seenBy").isJsonArray()) {
            // Dùng 'context' để deserialize các kiểu phức tạp như List
            Type listType = new TypeToken<List<String>>() {}.getType();
            List<String> seenByList = context.deserialize(jsonObject.get("seenBy"), listType);
            message.setSeenBy(seenByList);
        }

        // xử lý logic đặc biệt cho trường 'senderId'
        if (jsonObject.has("senderId")) {
            JsonElement senderElement = jsonObject.get("senderId");
            User sender = new User();

            if (senderElement.isJsonObject()) {
                // Trường hợp 1: senderId là một Object (tin nhắn nhóm)
                sender = context.deserialize(senderElement, User.class);
            } else if (senderElement.isJsonPrimitive() && senderElement.getAsJsonPrimitive().isString()) {
                // Trường hợp 2: senderId là một String (tin nhắn cá nhân)
                String senderIdString = senderElement.getAsString();
                sender.set_id(senderIdString);
            }
            message.setSenderId(sender.get_id());
        }

        // trả về đối tượng đã hoàn thiện
        return message;
    }
}