package com.chatty.controllers;

import com.chatty.models.User;
import com.chatty.services.AuthService;
import com.chatty.services.ThemeService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.application.Platform;

// trang đăng ký
public class SignUpController {
    private AuthService authService;

    public SignUpController() {
        this.authService = new AuthService();
    }

    public void show(Stage primaryStage) {
        primaryStage.setTitle("Kma Chatty - Đăng ký");
        primaryStage.centerOnScreen();

        // khung chứa chính
        HBox mainContainer = new HBox();
        mainContainer.getStyleClass().add("login-container");

        // phần bên trái - form đăng ký
        VBox leftPane = new VBox(20);
        leftPane.setPadding(new Insets(40));
        leftPane.setAlignment(Pos.CENTER);
        leftPane.setPrefWidth(450);
        leftPane.getStyleClass().add("login-form-pane");

        // logo
        VBox logoContainer = new VBox(10);
        logoContainer.setAlignment(Pos.CENTER);
        Label title = new Label("Tạo tài khoản");
        title.getStyleClass().add("login-title");
        Label subtitle = new Label("Bắt đầu với một tài khoản hoàn toàn miễn phí");
        subtitle.getStyleClass().add("login-subtitle");

        logoContainer.getChildren().addAll(title, subtitle);

        // vùng chứa toàn bộ form
        VBox formContainer = new VBox(20);
        formContainer.setPrefWidth(350);

        // tên đăng nhập
        Label usernameLabel = new Label("Tên đăng nhập");
        usernameLabel.getStyleClass().add("form-label");
        HBox usernameContainer = new HBox(10);
        usernameContainer.setAlignment(Pos.CENTER_LEFT);
        usernameContainer.getStyleClass().add("input-container");
        FontIcon usernameIcon = new FontIcon("mdi2a-account-circle");
        usernameIcon.setIconSize(20);
        usernameIcon.getStyleClass().add("input-icon");
        TextField usernameField = new TextField();
        usernameField.setPromptText("nguyen_van_a");
        usernameField.getStyleClass().add("text-input");
        usernameField.setPrefWidth(310);
        usernameContainer.getChildren().addAll(usernameIcon, usernameField);

        // mật khẩu
        Label passwordLabel = new Label("Mật khẩu");
        passwordLabel.getStyleClass().add("form-label");
        HBox passwordContainer = new HBox(10);
        passwordContainer.setAlignment(Pos.CENTER_LEFT);
        passwordContainer.getStyleClass().add("input-container");
        FontIcon lockIcon = new FontIcon("mdi2l-lock");
        lockIcon.setIconSize(20);
        lockIcon.getStyleClass().add("input-icon");
        StackPane passwordField = new StackPane();

        // trường mật khẩu (********)
        PasswordField invisiblePasswordField = new PasswordField();
        invisiblePasswordField.setPromptText("••••••••");
        invisiblePasswordField.getStyleClass().add("text-input");
        invisiblePasswordField.setPrefWidth(270);

        // trường mật khẩu đầy đủ
        TextField visiblePasswordField = new TextField();
        visiblePasswordField.setPromptText("••••••••");
        visiblePasswordField.getStyleClass().add("text-input");
        visiblePasswordField.setVisible(false);
        visiblePasswordField.setPrefWidth(270);

        // nút con mắt hiện mật khẩu
        ToggleButton showPasswordBtn = new ToggleButton();
        FontIcon eyeIcon = new FontIcon("mdi2e-eye");
        eyeIcon.setIconSize(20);
        showPasswordBtn.setGraphic(eyeIcon);
        showPasswordBtn.getStyleClass().add("icon-button");

        showPasswordBtn.setOnAction(e -> {
            if (showPasswordBtn.isSelected()) {
                visiblePasswordField.setText(invisiblePasswordField.getText());
                invisiblePasswordField.setVisible(false);
                visiblePasswordField.setVisible(true);
                eyeIcon.setIconLiteral("mdi2e-eye-off");
            } else {
                invisiblePasswordField.setText(visiblePasswordField.getText());
                visiblePasswordField.setVisible(false);
                invisiblePasswordField.setVisible(true);
                eyeIcon.setIconLiteral("mdi2e-eye");
            }
        });

        passwordField.getChildren().addAll(invisiblePasswordField, visiblePasswordField);
        passwordContainer.getChildren().addAll(lockIcon, passwordField, showPasswordBtn);

        // tên đầy đủ
        Label fullNameLabel = new Label("Họ và tên");
        fullNameLabel.getStyleClass().add("form-label");
        HBox fullNameContainer = new HBox(10);
        fullNameContainer.setAlignment(Pos.CENTER_LEFT);
        fullNameContainer.getStyleClass().add("input-container");
        FontIcon userIcon = new FontIcon("mdi2a-account");
        userIcon.setIconSize(20);
        userIcon.getStyleClass().add("input-icon");
        TextField fullNameField = new TextField();
        fullNameField.setPromptText("Nguyễn Văn A");
        fullNameField.getStyleClass().add("text-input");
        fullNameField.setPrefWidth(310);
        fullNameContainer.getChildren().addAll(userIcon, fullNameField);

        // email
        Label emailLabel = new Label("Email");
        emailLabel.getStyleClass().add("form-label");
        HBox emailContainer = new HBox(10);
        emailContainer.setAlignment(Pos.CENTER_LEFT);
        emailContainer.getStyleClass().add("input-container");
        FontIcon mailIcon = new FontIcon("mdi2e-email");
        mailIcon.setIconSize(20);
        mailIcon.getStyleClass().add("input-icon");
        TextField emailField = new TextField();
        emailField.setPromptText("nguyenvana@gmail.com");
        emailField.getStyleClass().add("text-input");
        emailField.setPrefWidth(310);
        emailContainer.getChildren().addAll(mailIcon, emailField);

        // nút đăng ký
        Button signupButton = new Button("Đăng ký");
        signupButton.getStyleClass().add("primary-button");
        signupButton.setPrefWidth(350);

        // link sang trang đăng nhập để người dùng chuyển hướng nếu đã có tài khoản
        HBox loginLinkContainer = new HBox();
        loginLinkContainer.setAlignment(Pos.CENTER);
        Label loginLabel = new Label("Bạn đã có tài khoản? ");
        loginLabel.getStyleClass().add("login-subtitle");
        Hyperlink loginLink = new Hyperlink("Đăng nhập");
        loginLink.getStyleClass().add("link");
        loginLinkContainer.getChildren().addAll(loginLabel, loginLink);

        formContainer.getChildren().addAll(usernameLabel, usernameContainer, passwordLabel, passwordContainer, fullNameLabel, fullNameContainer, emailLabel, emailContainer, signupButton, loginLinkContainer);

        leftPane.getChildren().addAll(logoContainer, formContainer);

        // phần bên phải - ảnh và lời chào mừng
        VBox rightPane = new VBox();
        rightPane.setPrefWidth(550);
        rightPane.getStyleClass().add("login-pattern-pane");
        ImageView logo = new ImageView();
        logo.setFitWidth(250);
        logo.setFitHeight(250);
        logo.setImage(new Image(getClass().getResource("/logo.png").toExternalForm()));

        // bo tròn logo
        Circle clip = new Circle(125, 125, 125); // Tọa độ tâm X, Y, Bán kính (Bán kính = 1 nửa kích thước ảnh)
        logo.setClip(clip);

        Label patternTitle = new Label("Tham gia cộng đồng của chúng tôi");
        patternTitle.getStyleClass().add("pattern-title");
        Label patternSubtitle = new Label("Kết nối với bạn bè, chia sẻ khoảnh khắc và luôn giữ liên lạc với những người bạn yêu thương.");
        patternSubtitle.getStyleClass().add("pattern-subtitle");
        rightPane.setAlignment(Pos.CENTER);
        rightPane.setSpacing(20);
        rightPane.getChildren().addAll(logo, patternTitle, patternSubtitle);

        mainContainer.getChildren().addAll(leftPane, rightPane);

        // logic đăng ký
        signupButton.setOnAction(e -> {
            String username = usernameField.getText();
            String fullName = fullNameField.getText();
            String email = emailField.getText();
            String password = invisiblePasswordField.isVisible() ? invisiblePasswordField.getText() : visiblePasswordField.getText();

            if (username.isEmpty() || fullName.isEmpty() || email.isEmpty() || password.isEmpty()) {
                showAlert("Lỗi", "Vui lòng điền đầy đủ thông tin", Alert.AlertType.ERROR);
                return;
            }

            if (password.length() < 6) {
                showAlert("Lỗi", "Mật khẩu phải có ít nhất 6 ký tự", Alert.AlertType.ERROR);
                return;
            }

            signupButton.setDisable(true);
            signupButton.setText("Đang tải...");

            new Thread(() -> {
                try {
                    authService.signup(username, fullName, email, password);

                    Platform.runLater(() -> {
                        showAlert("Đăng ký", "Đăng ký thành công!", Alert.AlertType.INFORMATION);
                        new LoginController().show(primaryStage);
                    });
                } catch (Exception ex) {
                    // Xử lý lỗi
                    String errorMessage = ex.getMessage();

                    // Mẹo nhỏ: Nếu lỗi có chứa JSON, thử làm sạch nó (tùy chọn)
                    if (errorMessage.contains("Request failed:")) {
                        // Cắt bớt chữ "Request failed:" cho đỡ dài
                        errorMessage = errorMessage.replace("Request failed:", "").trim();
                    }

                    final String finalMsg = errorMessage;
                    Platform.runLater(() -> {
                        showAlert("Đăng ký thất bại", finalMsg, Alert.AlertType.ERROR);
                        signupButton.setDisable(false);
                        signupButton.setText("Đăng ký");
                    });
                }
            }).start();
        });

        // xử lý khi người dùng nhấn vào link chuyển sang đăng nhập
        loginLink.setOnAction(e -> {
            new LoginController().show(primaryStage);
        });

        Scene scene = new Scene(mainContainer, 1000, 700);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.show();

        Platform.runLater(mainContainer::requestFocus);
    }

    // hàm tiện ích tạo cửa sổ nhỏ thông báo
    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        ThemeService.styleDialog(alert);
        alert.showAndWait();
    }
}

