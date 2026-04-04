package com.chatty;

import com.chatty.models.User;
import com.chatty.services.ThemeService;
import javafx.application.Application;
import javafx.stage.Stage;
import com.chatty.controllers.LoginController;
import com.chatty.services.AuthService;

public class ChattyApp extends Application {
    @Override
    public void start(Stage primaryStage) {
        AuthService authService = new AuthService();
        User user = authService.checkAuth();    // lấy người dùng đã từng đăng nhập trước đó

        primaryStage.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                ThemeService.install(newScene);
            }
        });

        // kiểm tra xem người dùng đã được xác định (có token) chưa
        if (user != null) {
            // chuyển hướng vào trang chủ
            new com.chatty.controllers.HomeController().show(primaryStage, user);
        } else {
            // chuyển hướng ra đăng nhập
            new LoginController().show(primaryStage);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

