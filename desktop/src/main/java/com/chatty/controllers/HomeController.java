package com.chatty.controllers;

import com.chatty.models.*;
import com.chatty.services.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

// trang chủ
public class HomeController {
    private final AuthService authService;
    private final ChatService chatService;
    private final GroupService groupService;
    private final SocketService socketService;
    private final UserService userService;
    private final User currentUser;

    // nhắn tin cá nhân
    private User selectedUser;
    private List<User> allUsers;
    private List<User> currentDisplayedUsers;
    private List<User> latestSearchResults;
    private List<Message> messages;

    // nhắn tin nhóm
    private Group selectedGroup;
    private List<Group> allGroups;
    private List<GroupMessage> groupMessages;

    private Set<String> onlineUserIds = new HashSet<>();
    private Timer typingTimer;
    private Timer groupTypingTimer;
    private Timer searchDebounceTimer;

    // các thành phần giao diện
    private VBox messageContainer;
    private ScrollPane messageScrollPane;
    private TextField messageInput;
    private ListView<User> userListView;
    private ListView<Group> groupListView;
    private Stage primaryStage;
    private BorderPane mainContainer;
    private HBox centerContent;
    private Scene scene;
    private Label onlineCountLabel;
    private CheckBox onlineOnlyCheck;
    private TextField searchField;
    private Label searchStatusLabel;
    private Label userStatus;
    private Label typingIndicator;

    // biến theo dõi tab đang hiện trên giao diện
    private String currentTab = "users"; // "users" hoặc "groups"

    public HomeController() {
        this.authService = new AuthService();
        this.socketService = new SocketService();
        this.chatService = new ChatService(socketService);
        this.groupService = new GroupService(socketService);
        this.userService = new UserService();
        this.messages = new ArrayList<>();
        this.groupMessages = new ArrayList<>();
        this.allUsers = new ArrayList<>();
        this.allGroups = new ArrayList<>();
        this.currentDisplayedUsers = new ArrayList<>();
        this.latestSearchResults = new ArrayList<>();
        this.currentUser = authService.getCurrentUser();
    }

    public void show(Stage primaryStage, User user) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("Kma Chatty");
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);
        primaryStage.setResizable(true);
        primaryStage.centerOnScreen();

        mainContainer = new BorderPane();
        mainContainer.getStyleClass().add("home-container");

        HBox navbar = createNavbar(primaryStage);
        mainContainer.setTop(navbar);

        centerContent = new HBox();
        centerContent.getStyleClass().add("chat-container");

        VBox sidebar = createSidebar();
        centerContent.getChildren().add(sidebar);

        VBox chatArea = createChatArea();
        centerContent.getChildren().add(chatArea);
        HBox.setHgrow(chatArea, Priority.ALWAYS);

        mainContainer.setCenter(centerContent);

        // tải người dùng và nhóm có liên quan
        loadUsers();
        loadGroups();

        if (currentUser != null) {
            setupSocketListeners();
            socketService.connect(currentUser.get_id());
        }

        scene = new Scene(mainContainer, 1200, 700);
        String themeStylesheet = ThemeService.getThemeStylesheet();
        scene.getStylesheets().add(getClass().getResource(themeStylesheet).toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.sizeToScene();
        primaryStage.centerOnScreen();
        primaryStage.show();

        Platform.runLater(mainContainer::requestFocus);
    }

    private void setupSocketListeners() {
        // danh sách online
        socketService.setOnOnlineListReceived(onlineIds -> {
            Platform.runLater(() -> {
                onlineUserIds.clear();
                onlineUserIds.addAll(onlineIds);

                if (allUsers != null) {
                    for (User u : allUsers) {
                        u.setOnline(onlineUserIds.contains(u.get_id()));
                    }

                    // vẽ giao diện để hiện danh sách người dùng
                    updateListViewBasedOnFilterAndSearch();

                    // cập nhật các nhãn người dùng có tin chưa đọc
                    updateOnlineCountLabel();
                }
            });
        });

        // có người dùng online
        socketService.setOnUserOnline(userId -> {
            Platform.runLater(() -> {
                onlineUserIds.add(userId);
                if(selectedUser != null && userStatus != null){
                    if (selectedUser.get_id().equals(userId)) userStatus.setText("Đang hoạt động");
                }
                if (allUsers != null) {
                    allUsers.stream()
                            .filter(u -> u.get_id().equals(userId))
                            .findFirst()
                            .ifPresent(u -> u.setOnline(true));

                    // cập nhật giao diện
                    updateListViewBasedOnFilterAndSearch();
                    updateOnlineCountLabel();
                }
            });
        });

        // có người dùng offline
        socketService.setOnUserOffline(userId -> {
            Platform.runLater(() -> {
                onlineUserIds.remove(userId);
                if(selectedUser != null && userStatus != null){
                    if (selectedUser.get_id().equals(userId)) userStatus.setText("Ngoại tuyến");
                }
                if (userListView.getItems() != null) {
                    userListView.getItems().stream()
                            .filter(u -> u.get_id().equals(userId))
                            .findFirst()
                            .ifPresent(u -> u.setOnline(false));

                    // cập nhật giao diện
                    updateListViewBasedOnFilterAndSearch();
                    updateOnlineCountLabel();
                }
            });
        });

        // gửi thông báo đang soạn tin (bắt đầu/kết thúc soạn)
        socketService.setOnTypingStart(senderId -> updateUserTypingStatus(senderId, true));
        socketService.setOnTypingStop(senderId -> updateUserTypingStatus(senderId, false));

        // có tin nhắn mới đến
        socketService.setOnNewMessage(message -> {
            if (selectedUser != null && message.getSenderId().equals(selectedUser.get_id())) {
                // nếu đang chat cùng mà có tin mới
                messages.add(message);
                Platform.runLater(() -> {

                    // vẽ lại giao diện chat để hiện tin mới
                    renderMessages();
                    socketService.emitSeenMessage(selectedUser.get_id()); // gửi thông báo đã xem tin nhắn
                });
            } else {
                // nếu đang không chat cùng mà có tin mới
                Platform.runLater(() -> {
                    String senderId = message.getSenderId();
                    this.allUsers.stream()
                            .filter(u -> u.get_id().equals(senderId))
                            .findFirst()
                            .ifPresent(u -> {
                                // cập nhật số tin chưa đọc và làm mới giao diện danh sách người dùng
                                u.setUnreadCount(u.getUnreadCount() + 1);
                                userListView.refresh();
                            });
                });
            }

            // trong trường hợp nào cũng vẽ lại giao diện để cập nhật nội dung preview tin nhắn mới nhất
            updateSidebarLastMessage(message);
        });

        // khi người khác xem tin nhắn của mình lúc đang chat
        socketService.setOnMessageSeen(data -> {
            Platform.runLater(() -> {
                String viewerId = data.get("viewerId").getAsString();
                if (selectedUser != null && selectedUser.get_id().equals(viewerId)) {
                    Label statusLabel = (Label) scene.lookup("#lastMessageStatus");
                    if (statusLabel != null) {
                        statusLabel.setText("Đã xem");
                    }
                }
            });
        });

        // ===== GROUP LISTENERS =====

        // nhận tin nhắn mới của nhóm
        socketService.setOnNewGroupMessage(message -> {
            Platform.runLater(() -> {
                // cập nhật danh sách nhóm
                loadGroups();

                // nếu đang mở giao diện chat của group, vẽ lại giao diện để hiện tin mới
                if (selectedGroup != null && message.getGroupId().equals(selectedGroup.get_id())) {
                    groupMessages.add(message);
                    renderGroupMessages();
                    socketService.emitSeenGroupMessage(message.get_id(), selectedGroup.get_id());
                } else {
                    // nếu không chat với group, cập nhật số tin chưa đọc
                    allGroups.stream()
                            .filter(g -> g.get_id().equals(message.getGroupId()))
                            .findFirst()
                            .ifPresent(g -> {
                                g.setUnreadCount(g.getUnreadCount() + 1);
                                groupListView.refresh();
                            });
                }
            });
        });

        // Group typing (chưa hoàn thiện)
        socketService.setOnGroupTypingStart(senderName -> {
            Platform.runLater(() -> {
                if (typingIndicator != null && selectedGroup != null) {
                    typingIndicator.setText(senderName + " đang soạn tin...");
                    typingIndicator.setVisible(true);
                }
            });
        });

        socketService.setOnGroupTypingStop(senderId -> {
            Platform.runLater(() -> {
                if (typingIndicator != null) {
                    typingIndicator.setVisible(false);
                }
            });
        });

        // có người xem tin nhắn đã gửi trong nhóm
        socketService.setOnGroupMessageSeen(data -> {
            Platform.runLater(() -> {
                try {
                    String messageId = data.get("messageId").getAsString();
                    String userId = data.get("userId").getAsString();

                    // cập nhật các biến giao diện để hiện người đã xem
                    boolean updated = false;
                    for (GroupMessage msg : groupMessages) {
                        if (msg.get_id().equals(messageId)) {
                            if (msg.getSeenBy() == null) {
                                msg.setSeenBy(new ArrayList<>());
                            }
                            if (!msg.getSeenBy().contains(userId)) {
                                msg.getSeenBy().add(userId);
                                updated = true;
                            }
                            break;
                        }
                    }

                    if (updated) {
                        // vẽ lại giao diện
                        renderGroupMessages();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        });

        // tải lại nhóm nếu có nhóm được tạo/bị xóa/bị kick
        socketService.setOnGroupCreated(data -> {
            Platform.runLater(this::loadGroups);
        });

        socketService.setOnGroupDeleted(data -> {
            Platform.runLater(() -> {
                String deletedGroupId = data.get("groupId").getAsString();
                if (selectedGroup != null && selectedGroup.get_id().equals(deletedGroupId)) {
                    // Close group chat
                    selectedGroup = null;
                    showNoChatView();
                }
                loadGroups();
            });
        });

        socketService.setOnReloadGroups(v -> {
            Platform.runLater(this::loadGroups);
        });
    }

    // tạo thanh điều hướng của ứng dụng (tên ứng dụng, nút cài đặt, nút xem profile, nút đăng xuất)
    private HBox createNavbar(Stage stage) {
        HBox navbar = new HBox(20);
        navbar.setPadding(new Insets(15, 30, 15, 30));
        navbar.setAlignment(Pos.CENTER_LEFT);
        navbar.getStyleClass().add("navbar");

        HBox logoContainer = new HBox(10);
        logoContainer.setAlignment(Pos.CENTER_LEFT);
        Label appName = new Label("Kma Chatty");
        appName.getStyleClass().add("navbar-title");
        logoContainer.getChildren().add(appName);

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox rightButtons = new HBox(10);
        rightButtons.setAlignment(Pos.CENTER_RIGHT);

        // nút cài đặt
        Button settingsBtn = new Button("Cài đặt");
        settingsBtn.getStyleClass().add("nav-button");
        FontIcon settingsIcon = new FontIcon("mdi2c-cog");
        settingsIcon.setIconSize(18);
        settingsBtn.setGraphic(settingsIcon);
        settingsBtn.setOnAction(e -> showSettings());

        // nút xem profile
        Button profileBtn = new Button("Thông tin cá nhân");
        profileBtn.getStyleClass().add("nav-button");
        FontIcon profileIcon = new FontIcon("mdi2a-account");
        profileIcon.setIconSize(18);
        profileBtn.setGraphic(profileIcon);
        profileBtn.setOnAction(e -> showProfile());

        // nút đăng xuất
        Button logoutBtn = new Button("Đăng xuất");
        logoutBtn.getStyleClass().add("nav-button");
        FontIcon logoutIcon = new FontIcon("mdi2l-logout");
        logoutIcon.setIconSize(18);
        logoutBtn.setGraphic(logoutIcon);
        logoutBtn.setOnAction(e -> {
            authService.logout();
            socketService.disconnect();
            new LoginController().show(stage);
        });

        rightButtons.getChildren().addAll(settingsBtn, profileBtn, logoutBtn);
        HBox.setHgrow(rightButtons, Priority.ALWAYS);
        navbar.getChildren().addAll(logoContainer, spacer, rightButtons);

        return navbar;
    }

    // tạo vùng bên trái (thanh chuyển tab người dùng/nhóm, thanh tìm kiếm, danh sách người dùng/nhóm)
    private VBox createSidebar() {
        VBox sidebar = new VBox();
        sidebar.setPrefWidth(280);
        sidebar.getStyleClass().add("sidebar");

        VBox sidebarHeader = new VBox(15);
        sidebarHeader.setPadding(new Insets(20));
        sidebarHeader.getStyleClass().add("sidebar-header");

        // tabs
        HBox tabButtons = new HBox(10);
        tabButtons.setAlignment(Pos.CENTER);

        Button usersTabBtn = new Button("Người dùng");
        usersTabBtn.getStyleClass().addAll("tab-button", "active");
        usersTabBtn.setOnAction(e -> switchTab("users", usersTabBtn));

        Button groupsTabBtn = new Button("Nhóm");
        groupsTabBtn.getStyleClass().add("tab-button");
        groupsTabBtn.setOnAction(e -> switchTab("groups", groupsTabBtn));

        tabButtons.getChildren().addAll(usersTabBtn, groupsTabBtn);

        // hộp tìm kiếm
        HBox searchBox = new HBox(10);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.getStyleClass().add("input-search");
        FontIcon searchIcon = new FontIcon("mdi2m-magnify");
        searchIcon.setIconSize(24);
        searchField = new TextField();
        searchField.setPromptText("Tìm kiếm...");
        searchField.getStyleClass().add("sidebar-title");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchBox.getChildren().addAll(searchIcon, searchField);

        searchStatusLabel = new Label("Đang tìm kiếm...");
        searchStatusLabel.getStyleClass().add("search-status-label");
        searchStatusLabel.setAlignment(Pos.CENTER);
        searchStatusLabel.setMaxWidth(Double.MAX_VALUE);
        searchStatusLabel.setVisible(false);

        // logic tìm kiếm được gọi bất kì khi nào nhận thấy sự thay đổi trong trường nhập liệu
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (searchDebounceTimer != null) {
                searchDebounceTimer.cancel();
            }

            searchDebounceTimer = new Timer();
            searchDebounceTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> {
                        String searchTerm = searchField.getText().trim();
                        if (searchTerm.isEmpty()) {
                            latestSearchResults.clear();
                            if (currentTab.equals("users")) {
                                updateListViewBasedOnFilterAndSearch();
                            } else {
                                updateGroupListView();
                            }
                            searchStatusLabel.setVisible(false);
                        } else {
                            if (currentTab.equals("users")) {
                                performSearch(searchTerm);
                            } else {
                                performGroupSearch(searchTerm);
                            }
                        }
                    });
                }
            }, 500);
        });

        // logic hiện (ẩn) người dùng online khi checkbox được chọn (chỉ có bên tab người dùng)
        onlineOnlyCheck = new CheckBox("Hiện người dùng online");
        onlineOnlyCheck.getStyleClass().add("filter-checkbox");
        onlineOnlyCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            updateListViewBasedOnFilterAndSearch();
        });

        onlineCountLabel = new Label("(0 người online)");
        onlineCountLabel.getStyleClass().add("online-count");

        HBox filterContainer = new HBox(10);
        filterContainer.getChildren().addAll(onlineOnlyCheck, onlineCountLabel);

        // nút tạo nhóm (chỉ có bên tab nhóm)
        Button createGroupBtn = new Button("+ Tạo nhóm");
        createGroupBtn.getStyleClass().add("btn-primary");
        createGroupBtn.setMaxWidth(Double.MAX_VALUE);
        createGroupBtn.setVisible(false);
        createGroupBtn.setManaged(false);
        createGroupBtn.setOnAction(e -> showCreateGroupDialog());

        sidebarHeader.getChildren().addAll(tabButtons, searchBox, filterContainer, createGroupBtn);
        sidebarHeader.setId("sidebarHeader");

        // danh sách người dùng
        userListView = new ListView<>();
        userListView.setCellFactory(list -> new UserListCell());
        userListView.getStyleClass().add("user-list");
        userListView.setOnMouseClicked(e -> {
            User selected = userListView.getSelectionModel().getSelectedItem();
            if (selected != null && !searchStatusLabel.isVisible()) {
                selectUser(selected);
            }
        });

        // danh sách nhóm
        groupListView = new ListView<>();
        groupListView.setCellFactory(list -> new GroupListCell());
        groupListView.getStyleClass().add("user-list");
        groupListView.setVisible(false);
        groupListView.setManaged(false);
        groupListView.setOnMouseClicked(e -> {
            Group selected = groupListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                selectGroup(selected);
            }
        });

        // tạo khung chứa chung để thay phiên ẩn hiện danh sách người dùng/nhóm mỗi khi chuyển tab
        StackPane listStack = new StackPane();
        VBox.setVgrow(listStack, Priority.ALWAYS);
        StackPane.setAlignment(searchStatusLabel, Pos.CENTER);
        listStack.getChildren().addAll(userListView, groupListView, searchStatusLabel);

        sidebar.getChildren().addAll(sidebarHeader, listStack);

        return sidebar;
    }

    // logic chuyển tab
    private void switchTab(String tab, Button clickedBtn) {
        currentTab = tab;

        // cập nhật trạng thái nút bấm khi được nhấn (vd: nhấn vào tab nhóm -> nút có khung xanh được chọn)
        VBox sidebarHeader = (VBox) clickedBtn.getParent().getParent();
        HBox tabButtons = (HBox) clickedBtn.getParent();
        tabButtons.getChildren().forEach(node -> {
            if (node instanceof Button) {
                node.getStyleClass().remove("active");
            }
        });
        clickedBtn.getStyleClass().add("active");

        // dọn dẹp nội dung trong thanh tìm kếm
        searchField.setText("");
        searchStatusLabel.setVisible(false);

        // ẩn hiện danh sách và nút bấm phù hợp
        Button createGroupBtn = (Button) sidebarHeader.lookup(".btn-primary");

        if (tab.equals("users")) {
            userListView.setVisible(true);
            userListView.setManaged(true);
            groupListView.setVisible(false);
            groupListView.setManaged(false);
            onlineOnlyCheck.setVisible(true);
            onlineOnlyCheck.setManaged(true);
            onlineCountLabel.setVisible(true);
            onlineCountLabel.setManaged(true);
            if (createGroupBtn != null) {
                createGroupBtn.setVisible(false);
                createGroupBtn.setManaged(false);
            }
            updateListViewBasedOnFilterAndSearch();
        } else {
            userListView.setVisible(false);
            userListView.setManaged(false);
            groupListView.setVisible(true);
            groupListView.setManaged(true);
            onlineOnlyCheck.setVisible(false);
            onlineOnlyCheck.setManaged(false);
            onlineCountLabel.setVisible(false);
            onlineCountLabel.setManaged(false);
            if (createGroupBtn != null) {
                createGroupBtn.setVisible(true);
                createGroupBtn.setManaged(true);
            }
            updateGroupListView();
        }
    }

    // tạo vùng nhắn tin (phần bên phải)
    private VBox createChatArea() {
        VBox chatArea = new VBox();
        chatArea.getStyleClass().add("chat-area");

        // thanh đầu mục chat (hiển thị thông tin người/nhóm đang nhắn, nút đóng)
        HBox chatHeader = new HBox(15);
        chatHeader.setPadding(new Insets(15, 20, 15, 20));
        chatHeader.getStyleClass().add("chat-header");
        chatHeader.setVisible(false);
        chatHeader.setManaged(false);
        chatHeader.setId("chatHeader");

        // khung giao diện chính hiển thị các tin nhắn
        messageScrollPane = new ScrollPane();
        messageScrollPane.setFitToWidth(true);
        messageScrollPane.getStyleClass().add("message-scroll-pane");

        messageContainer = new VBox(15);
        messageContainer.setPadding(new Insets(20));
        messageScrollPane.setContent(messageContainer);
        messageContainer.heightProperty().addListener((obs, oldVal, newVal) -> {
            messageScrollPane.setVvalue(1.0);
        });

        // Typing indicator (chưa phát triển)
        typingIndicator = new Label();
        typingIndicator.getStyleClass().add("typing-indicator");
        typingIndicator.setVisible(false);
        typingIndicator.setManaged(false);

        // giao diện khi chưa chọn nhóm/người dùng để chat
        VBox noChatView = new VBox(20);
        noChatView.setAlignment(Pos.CENTER);
        noChatView.getStyleClass().add("no-chat-view");
        ImageView logo = new ImageView();
        logo.setFitWidth(200);
        logo.setFitHeight(200);
        logo.setImage(new Image(getClass().getResource("/logo.png").toExternalForm()));
        Label welcomeLabel = new Label("Chào mừng đến với Kma Chatty!");
        welcomeLabel.getStyleClass().add("no-chat-title");
        Label subtitleLabel = new Label("Chọn một cuộc trò chuyện để bắt đầu");
        subtitleLabel.getStyleClass().add("no-chat-subtitle");
        noChatView.getChildren().addAll(logo, welcomeLabel, subtitleLabel);
        noChatView.setId("noChatView");

        // thanh nhập tin nhắn
        HBox messageInputContainer = new HBox(10);
        messageInputContainer.setPadding(new Insets(15, 20, 15, 20));
        messageInputContainer.getStyleClass().add("message-input-container");

        messageInput = new TextField();
        messageInput.setPromptText("Nhập tin nhắn...");
        messageInput.getStyleClass().add("message-input");
        HBox.setHgrow(messageInput, Priority.ALWAYS);

        Button sendButton = new Button();
        FontIcon sendIcon = new FontIcon("mdi2s-send");
        sendIcon.setIconSize(20);
        sendButton.setGraphic(sendIcon);
        sendButton.getStyleClass().add("send-button");

        // logic thực hiện khi đang nhập tin
        messageInput.textProperty().addListener((obs, oldVal, newVal) -> {
            if (selectedUser == null && selectedGroup == null) return;

            if (!newVal.isEmpty()) {
                if (selectedGroup != null) {
                    socketService.emitGroupTypingStart(selectedGroup.get_id());

                    if (groupTypingTimer != null) groupTypingTimer.cancel();
                    groupTypingTimer = new Timer();
                    groupTypingTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            socketService.emitGroupTypingStop(selectedGroup.get_id());
                        }
                    }, 2000);
                } else if (selectedUser != null) {
                    socketService.emitStartTyping(selectedUser.get_id());

                    if (typingTimer != null) typingTimer.cancel();
                    typingTimer = new Timer();
                    typingTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            socketService.emitStopTyping(selectedUser.get_id());
                        }
                    }, 2000);
                }
            }
        });

        // khi có sự kiện enter/bấm nút gửi ở thanh soạn tin -> gọi hàm gửi tin
        messageInput.setOnAction(e -> sendMessage());
        sendButton.setOnAction(e -> sendMessage());

        messageInputContainer.getChildren().addAll(messageInput, sendButton);
        messageInputContainer.setId("messageInputContainer");
        messageInputContainer.setVisible(false);
        messageInputContainer.setManaged(false);

        chatArea.getChildren().addAll(chatHeader, noChatView, messageScrollPane, typingIndicator, messageInputContainer);
        VBox.setVgrow(messageScrollPane, Priority.ALWAYS);
        VBox.setVgrow(noChatView, Priority.ALWAYS);

        return chatArea;
    }

    // ========== GROUP METHODS ==========

    // tải danh sách nhóm liên quan
    private void loadGroups() {
        new Thread(() -> {
            try {
                allGroups = groupService.getGroups();
                Platform.runLater(() -> {
                    // Join all group rooms
                    for (Group g : allGroups) {
                        socketService.joinGroup(g.get_id());
                        g.updateStatusPreview();
                    }
                    updateGroupListView();
                    searchStatusLabel.setVisible(false);
                });
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    showAlert("Lỗi", "Tải nhóm thất bại: " + e.getMessage(), Alert.AlertType.ERROR);
                });
            }
        }).start();
    }

    // cập nhật giao diện danh sách nhóm
    private void updateGroupListView() {
        Platform.runLater(() -> {
            String searchTerm = searchField != null ? searchField.getText().trim() : "";
            List<Group> groupsToShow = new ArrayList<>(allGroups);

            if (!searchTerm.isEmpty()) {
                groupsToShow = groupsToShow.stream()
                        .filter(g -> g.getName().toLowerCase().contains(searchTerm.toLowerCase()))
                        .collect(Collectors.toList());
            }

            groupListView.getItems().setAll(groupsToShow);

            if (groupsToShow.isEmpty() && !searchTerm.isEmpty()) {
                searchStatusLabel.setText("Không tìm thấy nhóm");
                searchStatusLabel.setVisible(true);
            } else {
                searchStatusLabel.setVisible(false);
            }
        });
    }

    // cập nhật giao diện khi tìm kiếm nhóm
    private void performGroupSearch(String searchTerm) {
        // Simple local filter for groups
        updateGroupListView();
    }

    // logic thực hiện khi chọn nhóm bất kì để chat
    private void selectGroup(Group groupFromList) {
        String groupId = groupFromList.get_id();
        this.selectedUser = null; // cập nhật biến người dùng đang chat về null

        groupFromList.setUnreadCount(0);
        groupListView.refresh();

        showChatView();
        messageContainer.getChildren().clear();
        Label loadingLabel = new Label("Đang tải thông tin nhóm...");
        loadingLabel.getStyleClass().add("no-chat-subtitle");
        messageContainer.getChildren().add(loadingLabel);

        HBox chatHeader = (HBox) ((VBox) messageScrollPane.getParent()).getChildren().get(0);
        chatHeader.setVisible(true);
        chatHeader.setManaged(true);
        chatHeader.getChildren().clear();
        Label tempGroupName = new Label(groupFromList.getName());
        tempGroupName.getStyleClass().add("chat-header-name");
        chatHeader.getChildren().add(tempGroupName);

        new Thread(() -> {
            try {
                // lấy thông tin chi tiết nhóm
                Group detailedGroup = groupService.getGroupInfo(groupId);

                // cập nhật UI ngay sau khi có dữ liệu
                Platform.runLater(() -> {
                    if (detailedGroup != null) {
                        // render dữ liệu đầy đủ
                        renderSelectedGroupUI(detailedGroup);
                    } else {
                        // lỗi
                        showNoChatView();
                        showAlert("Lỗi", "Không thể tải thông tin chi tiết của nhóm.", Alert.AlertType.ERROR);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    showNoChatView();
                    showAlert("Lỗi", "Đã xảy ra lỗi khi tải dữ liệu nhóm: " + e.getMessage(), Alert.AlertType.ERROR);
                });
            }
        }).start();
    }

    // logic vẽ giao diện nhóm được chọn để chuẩn bị chat
    private void renderSelectedGroupUI(Group detailedGroup) {
        this.selectedGroup = detailedGroup; // Cập nhật biến instance bằng dữ liệu chi tiết

        // reset số tin chưa đọc về 0
        detailedGroup.setUnreadCount(0);
        groupListView.refresh();

        // tham giao vào phòng chat nhóm
        socketService.joinGroup(detailedGroup.get_id());

        HBox chatHeader = (HBox) ((VBox) messageScrollPane.getParent()).getChildren().get(0);
        chatHeader.setVisible(true);
        chatHeader.setManaged(true);

        chatHeader.getChildren().clear();
        Node avatarNode = createAvatarNode(detailedGroup.getAvatar(), 40, 24);
        avatarNode.getStyleClass().add("chat-header-avatar");

        VBox groupInfo = new VBox(5);
        Label groupName = new Label(detailedGroup.getName());
        groupName.getStyleClass().add("chat-header-name");
        Label memberCount = new Label(detailedGroup.getMemberCount() + " thành viên");
        memberCount.getStyleClass().add("chat-header-status");
        groupInfo.getChildren().addAll(groupName, memberCount);

        Button groupMenuBtn = new Button();
        FontIcon menuIcon = new FontIcon("mdi2d-dots-vertical");
        menuIcon.setIconSize(20);
        groupMenuBtn.setGraphic(menuIcon);
        groupMenuBtn.getStyleClass().add("icon-button");
        groupMenuBtn.setOnAction(e -> showGroupMenu(detailedGroup));

        Button closeBtn = new Button();
        FontIcon closeIcon = new FontIcon("mdi2c-close");
        closeIcon.setIconSize(20);
        closeBtn.setGraphic(closeIcon);
        closeBtn.getStyleClass().add("close-button");
        closeBtn.setOnAction(e -> {
            selectedGroup = null;
            showNoChatView();
        });

        HBox.setHgrow(groupInfo, Priority.ALWAYS);
        chatHeader.getChildren().addAll(avatarNode, groupInfo, groupMenuBtn, closeBtn);

        // ẩn giao diện no chat view
        showChatView();

        // tải tin nhắn nhóm
        loadGroupMessages();
    }

    // logic tải tin nhắn nhóm
    private void loadGroupMessages() {
        if (selectedGroup == null) return;

        new Thread(() -> {
            try {
                groupMessages = groupService.getGroupMessages(selectedGroup.get_id());
                Platform.runLater(() -> {
                    // vẽ giao diện khi nhận được danh sách tin nhắn
                    renderGroupMessages();

                    // cập nhật trạng thái đã xem tin nhắn nhóm
                    for (GroupMessage msg : groupMessages) {
                        if (!msg.getSenderId().equals(currentUser.get_id()) && !msg.isSeenBy(currentUser.get_id())) {
                            socketService.emitSeenGroupMessage(msg.get_id(), selectedGroup.get_id());
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    showAlert("Lỗi", "Tải tin nhắn thất bại: " + e.getMessage(), Alert.AlertType.ERROR);
                });
            }
        }).start();
    }


    // logic vẽ giao diện tin nhắn nhóm
    private void renderGroupMessages() {
        messageContainer.getChildren().clear();

        for (GroupMessage msg : groupMessages) {
            // lặp để vẽ từng tin nhắn
            boolean isMyMessage = msg.getSenderId().equals(currentUser.get_id());

            HBox messageBox = new HBox(10);
            messageBox.getStyleClass().add(isMyMessage ? "message-box-right" : "message-box-left");

            VBox messageContent = new VBox(5);
            messageContent.setMaxWidth(400);
            messageContent.setFillWidth(false);

            if (!isMyMessage) {
                Label senderName = new Label(msg.getSenderName());
                senderName.getStyleClass().add("message-sender");
                messageContent.getChildren().add(senderName);
            }

            if (msg.getContent() != null && !msg.getContent().isEmpty()) {
                Label messageText = new Label(msg.getContent());
                messageText.getStyleClass().add("message-text");
                messageText.setWrapText(true);
                messageContent.getChildren().add(messageText);
            }

            Label timeLabel = new Label(formatTime(msg.getCreatedAt()));
            timeLabel.getStyleClass().add("message-time");
            messageContent.getChildren().add(timeLabel);

            if (isMyMessage) {
                Node myAvatar = createAvatarNode(currentUser.getAvatar(), 40, 24);

                VBox contentWithStatus = new VBox(2);
                contentWithStatus.setAlignment(Pos.BOTTOM_RIGHT);
                contentWithStatus.getChildren().add(messageContent);

                // kiểm tra trạng thái đã xem các tin
                if (msg.getSeenBy() != null && !msg.getSeenBy().isEmpty()) {
                    List<String> seenNames = new ArrayList<>();
                    for (String id : msg.getSeenBy()) {
                        if (!id.equals(currentUser.get_id())) {
                            seenNames.add(getUserNameById(id));
                        }
                    }

                    if (!seenNames.isEmpty()) {
                        String text = String.join(", ", seenNames);
                        Label seenLabel = new Label(text + " đã xem");
                        seenLabel.getStyleClass().add("message-status");
                        seenLabel.setStyle("-fx-font-size: 10px;");
                        contentWithStatus.getChildren().add(seenLabel);
                    } else {
                        Label sentLabel = new Label("Đã gửi");
                        sentLabel.getStyleClass().add("message-status");
                        contentWithStatus.getChildren().add(sentLabel);
                    }
                } else {
                    Label sentLabel = new Label("Đã gửi");
                    sentLabel.getStyleClass().add("message-status");
                    contentWithStatus.getChildren().add(sentLabel);
                }

                messageBox.getChildren().addAll(contentWithStatus, myAvatar);
            } else {
                Node senderAvatar = createAvatarNode(msg.getSenderAvatar(), 40, 24);
                messageBox.getChildren().addAll(senderAvatar, messageContent);
            }

            messageContainer.getChildren().add(messageBox);
        }

        messageScrollPane.setVvalue(1.0);
    }

    // mở giao diện tạo nhóm (gọi khi nút tạo nhóm được ấn)
    private void showCreateGroupDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Tạo nhóm mới");
        dialog.setHeaderText("Nhập thông tin nhóm và thêm thành viên");

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        TextField nameField = new TextField();
        nameField.setPromptText("Tên nhóm (bắt buộc)");

        TextArea descriptionArea = new TextArea();
        descriptionArea.setPromptText("Mô tả (tùy chọn)");
        descriptionArea.setWrapText(true);
        descriptionArea.setPrefRowCount(5);

        VBox memberSection = new VBox(10);
        Label membersLabel = new Label("Thêm thành viên (cần ít nhất 2 người)");

        TextField memberSearchField = new TextField();
        memberSearchField.setPromptText("Tìm kiếm thành viên...");

        ListView<User> searchResultsView = new ListView<>();
        searchResultsView.setPrefHeight(150);

        HBox selectedMembersBox = new HBox(5);
        selectedMembersBox.setStyle("-fx-padding: 5px; -fx-border-color: #ccc; -fx-border-width: 1; -fx-border-radius: 5;");
        selectedMembersBox.setPrefHeight(40);
        ScrollPane selectedMembersScrollPane = new ScrollPane(selectedMembersBox);
        selectedMembersScrollPane.setFitToHeight(true);

        List<User> selectedMembers = new ArrayList<>();

        // cập nhật danh sách người dùng để chọn thêm vào nhóm dựa theo kết quả tìm kiếm
        searchResultsView.setCellFactory(lv -> new ListCell<User>() {
            @Override
            protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setText(null);
                } else {
                    setText(user.getFullName());
                }
            }
        });

        // thêm người dùng vào danh sách thành viên nhóm sắp tạo khi ấn chọn
        searchResultsView.setOnMouseClicked(event -> {
            User user = searchResultsView.getSelectionModel().getSelectedItem();
            if (user != null && !selectedMembers.contains(user)) {
                selectedMembers.add(user);
                updateSelectedMembersUI(selectedMembers, selectedMembersBox);
                memberSearchField.clear();
                searchResultsView.getItems().clear();
            }
        });

        // logic thanh tìm kiếm người thêm vào nhóm
        memberSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.trim().isEmpty()) {
                searchResultsView.getItems().clear();
                return;
            }

            if (searchDebounceTimer != null) searchDebounceTimer.cancel();
            searchDebounceTimer = new Timer();
            searchDebounceTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        List<User> results = chatService.searchUser(newVal.trim());

                        // lọc những người đã chọn và chính mình sau khi lấy kết quả tìm kiếm
                        List<User> filteredResults = results.stream()
                                .filter(u -> !selectedMembers.contains(u) && !u.get_id().equals(currentUser.get_id()))
                                .collect(Collectors.toList());
                        Platform.runLater(() -> searchResultsView.getItems().setAll(filteredResults));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, 500);
        });

        memberSection.getChildren().addAll(membersLabel, memberSearchField, searchResultsView, selectedMembersScrollPane);
        content.getChildren().addAll(nameField, descriptionArea, memberSection);
        dialogPane.setContent(content);

        final Button okButton = (Button) dialogPane.lookupButton(ButtonType.OK);
        okButton.addEventFilter(
                javafx.event.ActionEvent.ACTION,
                event -> {
                    if (nameField.getText().trim().isEmpty() || selectedMembers.size() < 2) {
                        showAlert("Lỗi", "Tên nhóm không được để trống và phải có ít nhất 2 thành viên khác.", Alert.AlertType.ERROR);
                        event.consume(); // Ngăn dialog đóng lại
                    }
                });

        // logic thực hiện khi người dùng nhấn ok (để tạo nhóm) hoặc thoát (để hủy tạo nhóm)
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        List<String> memberIds = selectedMembers.stream().map(User::get_id).collect(Collectors.toList());
                        groupService.createGroup(nameField.getText().trim(), descriptionArea.getText().trim(), memberIds);
                        Platform.runLater(() -> {
                            loadGroups(); // tải lại danh sách nhóm
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> showAlert("Lỗi tạo nhóm", e.getMessage(), Alert.AlertType.ERROR));
                    }
                }).start();
            }
            return null;
        });

        ThemeService.styleDialog(dialog);
        dialog.showAndWait();
    }

    // hàm trợ giúp được gọi để vẽ lại giao diện danh sách người thêm vào nhóm dựa trên kết quả tìm kiếm
    private void updateSelectedMembersUI(List<User> members, HBox container) {
        container.getChildren().clear();
        for (User user : members) {
            HBox memberTag = new HBox(5);
            memberTag.setAlignment(Pos.CENTER);
            memberTag.setStyle("-fx-background-color: #e0e0e0; -fx-padding: 5; -fx-background-radius: 10;");
            Label nameLabel = new Label(user.getFullName());
            Button removeBtn = new Button("X");
            removeBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
            removeBtn.setOnAction(e -> {
                members.remove(user);
                updateSelectedMembersUI(members, container);
            });
            memberTag.getChildren().addAll(nameLabel, removeBtn);
            container.getChildren().add(memberTag);
        }
    }

    // hàm mở cửa sổ hiển thị thông tin nhóm và các thao tác với nhóm
    private void showGroupMenu(Group group) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Quản lý nhóm: " + group.getName());

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().add(ButtonType.CLOSE);

        // chia 2 tab quản lý thông tin cơ bản của nhóm và quản lý thông tin thành viên trong nhóm
        TabPane tabPane = new TabPane();
        Tab infoTab = new Tab("Thông tin", createGroupInfoTab(group, dialog));
        Tab membersTab = new Tab("Thành viên (" + group.getMemberCount() + ")", createGroupMembersTab(group, dialog));

        infoTab.setClosable(false);
        membersTab.setClosable(false);

        tabPane.getTabs().addAll(infoTab, membersTab);
        tabPane.getStyleClass().add("dialog-tab-pane");
        dialogPane.setContent(tabPane);
        dialogPane.setPrefSize(450, 500);
        ThemeService.styleDialog(dialog);
        dialog.showAndWait();
    }

    // hàm trợ giúp tạo tab quản lý thông tin cơ bản của nhóm
    private Node createGroupInfoTab(Group group, Dialog<?> parentDialog) {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.TOP_CENTER);

        Node avatarNode = createAvatarNode(group.getAvatar(), 100, 80);
        Label nameLabel = new Label(group.getName());
        nameLabel.getStyleClass().add("profile-name");

        Label descLabel = new Label(group.getDescription() != null && !group.getDescription().isEmpty() ? group.getDescription() : "Không có mô tả");
        descLabel.getStyleClass().add("profile-email");
        descLabel.setWrapText(true);

        // các nút thao tác
        HBox actionButtons = new HBox(10);
        actionButtons.setAlignment(Pos.CENTER);

        String currentUserId = currentUser.get_id();

        // kiểm tra ownerId không phải null TRƯỚC KHI gọi .equals()
        boolean isOwner = group.isUserOwner(currentUserId);

        // hàm isUserAdmin đã an toàn vì nó xử lý danh sách members
        boolean isAdmin = group.isUserAdmin(currentUserId);

        // nút sửa thông tin (dành cho Admin hoặc Owner)
        if (isAdmin || isOwner) {
            Button editGroupBtn = new Button("Chỉnh sửa");
            editGroupBtn.setOnAction(e -> {
                // khi nhấn nút sửa, lấy thông tin đầy đủ và gọi hàm vẽ giao diện
                new Thread(() -> {
                    try {
                        Group detailedGroup = groupService.getGroupInfo(group.get_id());
                        Platform.runLater(() -> {
                            // parentDialog.close(); (gọi sẽ lỗi mở cửa sổ con)

                            // mở cửa sổ con chỉnh sửa thông tin nhóm
                            showEditGroupDialog(detailedGroup);
                        });
                    } catch (Exception ex) {
                        Platform.runLater(() -> showAlert("Lỗi", "Không thể lấy thông tin chi tiết của nhóm.", Alert.AlertType.ERROR));
                    }
                }).start();
            });
            actionButtons.getChildren().add(editGroupBtn);
        }

        // nút rời/xóa nhóm
        Button leaveOrDeleteBtn = new Button();
        leaveOrDeleteBtn.getStyleClass().add("danger-button");
        if (isOwner) {
            leaveOrDeleteBtn.setText("Xóa nhóm");
        } else {
            leaveOrDeleteBtn.setText("Rời nhóm");
        }

        // logic rời/xóa nhóm
        leaveOrDeleteBtn.setOnAction(e -> {
            String confirmationText = isOwner ? "Bạn có chắc chắn muốn xóa vĩnh viễn nhóm này?" : "Bạn có chắc chắn muốn rời khỏi nhóm này?";
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION, confirmationText, ButtonType.YES, ButtonType.NO);
            confirmation.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    new Thread(() -> {
                        try {
                            groupService.deleteGroup(group.get_id());
                            Platform.runLater(() -> {
                                parentDialog.close();
                                showNoChatView();
                                loadGroups();
                            });
                        } catch (Exception ex) {
                            Platform.runLater(() -> showAlert("Lỗi", "Không thể thực hiện hành động này: " + ex.getMessage(), Alert.AlertType.ERROR));
                        }
                    }).start();
                }
            });
        });

        actionButtons.getChildren().add(leaveOrDeleteBtn);
        content.getChildren().addAll(avatarNode, nameLabel, descLabel, new Separator(), actionButtons);
        return content;
    }

    // hàm trợ giúp tạo tab quản lý thành viên của nhóm
    private Node createGroupMembersTab(Group group, Dialog<?> parentDialog) {
        VBox content = new VBox(15);
        content.setPadding(new Insets(10));

        // nút thêm thành viên
        Button addMemberBtn = new Button("Thêm thành viên");
        addMemberBtn.getStyleClass().add("primary-button");
        addMemberBtn.setGraphic(new FontIcon("mdi2a-account-plus"));
        addMemberBtn.setMaxWidth(Double.MAX_VALUE);
        addMemberBtn.setOnAction(e -> showAddMemberModal(group, parentDialog));
        Label headerLabel = new Label("Danh sách thành viên");
        headerLabel.getStyleClass().add("section-label");

        ListView<Group.GroupMember> membersListView = new ListView<>();
        membersListView.setCellFactory(lv -> new GroupMemberCell(group));
        membersListView.getStyleClass().add("group-member-list");

        // lấy danh sách thành viên chi tiết và gọi hàm vẽ giao diện
        new Thread(() -> {
            try {
                // ta cần thông tin chi tiết của User trong GroupMember
                Group detailedGroup = groupService.getGroupInfo(group.get_id());
                Platform.runLater(() -> {
                    membersListView.getItems().setAll(detailedGroup.getMembers());
                    group.setMembers(detailedGroup.getMembers());
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        HBox memberActions = new HBox(10);

        content.getChildren().addAll(addMemberBtn, headerLabel, membersListView, memberActions);
        VBox.setVgrow(membersListView, Priority.ALWAYS);
        return content;
    }

    // hàm trợ giúp được gọi để hiển thị cửa sổ thêm thành viên khi nhấn nút thêm
    private void showAddMemberModal(Group group, Dialog<?> parentDialog) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Thêm thành viên vào: " + group.getName());
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(15);
        content.setPadding(new Insets(15));
        content.setPrefSize(400, 500);

        Label searchLabel = new Label("Tìm kiếm người dùng:");
        searchLabel.getStyleClass().add("dialog-label");

        TextField searchField = new TextField();
        searchField.setPromptText("Nhập tên người dùng để tìm kiếm...");
        searchField.getStyleClass().add("search-box");

        ListView<User> userListView = new ListView<>();
        java.util.Set<String> selectedUserIds = new java.util.HashSet<>();

        // cấu hình vẽ từng item thành viên gồm checkbox thêm, tên người dùng
        userListView.setCellFactory(lv -> new ListCell<>() {
            private final CheckBox checkBox = new CheckBox();
            private final HBox root = new HBox(10);
            private final Label nameLabel = new Label();

            {
                root.setAlignment(Pos.CENTER_LEFT);
                root.getChildren().addAll(checkBox, nameLabel);
            }

            @Override
            protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setGraphic(null);
                } else {
                    Node avatar = createAvatarNode(user.getAvatar(), 30, 20);
                    if (root.getChildren().size() > 2)
                        root.getChildren().remove(1); // Remove old avatar
                    root.getChildren().add(1, avatar);

                    nameLabel.setText(user.getFullName());

                    boolean isMember = group.getMembers().stream()
                            .anyMatch(m -> m.getUser() != null && m.getUser().get_id().equals(user.get_id()));

                    checkBox.setOnAction(null); // Clear old listener

                    if (isMember) {
                        checkBox.setSelected(true);
                        checkBox.setDisable(true);
                        nameLabel.setStyle("-fx-text-fill: #999;");
                    } else {
                        checkBox.setDisable(false);
                        checkBox.setSelected(selectedUserIds.contains(user.get_id()));
                        nameLabel.setStyle("-fx-text-fill: #333;");

                        checkBox.setOnAction(e -> {
                            if (checkBox.isSelected())
                                selectedUserIds.add(user.get_id());
                            else
                                selectedUserIds.remove(user.get_id());
                        });
                    }

                    setGraphic(root);
                }
            }
        });

        // lắng nghe sự thay đổi nội dung nhập liệu của thanh tìm kiếm để vẽ lại danh sách người dùng
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.length() >= 1) {
                new Thread(() -> {
                    try {
                        List<User> results = chatService.searchUser(newVal);
                        Platform.runLater(() -> userListView.getItems().setAll(results));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }).start();
            } else {
                userListView.getItems().clear();
            }
        });

        // nút xác nhận thêm và logic của nó
        Button confirmBtn = new Button("Thêm vào nhóm");
        confirmBtn.getStyleClass().add("primary-button");
        confirmBtn.setMaxWidth(Double.MAX_VALUE);
        confirmBtn.setOnAction(e -> {
            if (selectedUserIds.isEmpty()) {
                showAlert("Thông báo", "Vui lòng chọn ít nhất một người dùng.", Alert.AlertType.WARNING);
                return;
            }

            new Thread(() -> {
                try {
                    List<String> idsObj = new ArrayList<>(selectedUserIds);
                    boolean success = groupService.addMembers(group.get_id(), idsObj);
                    if (success) {
                        Group updatedGroup = groupService.getGroupInfo(group.get_id());
                        Platform.runLater(() -> {
                            showAlert("Thành công", "Đã thêm thành viên mới!", Alert.AlertType.INFORMATION);
                            dialog.setResult(null);
                            dialog.close();

                            // Refresh Main UI Header
                            renderSelectedGroupUI(updatedGroup);

                            // Close parent dialog (optional, forcing user to reopen to see updated list)
                            if (parentDialog != null) {
                                parentDialog.setResult(null);
                                parentDialog.close();
                            }
                        });
                    }
                } catch (Exception ex) {
                    Platform.runLater(() -> showAlert("Lỗi", "Thêm thành viên thất bại: " + ex.getMessage(),
                            Alert.AlertType.ERROR));
                }
            }).start();
        });

        content.getChildren().addAll(searchLabel, searchField, userListView, confirmBtn);
        VBox.setVgrow(userListView, Priority.ALWAYS);

        dialogPane.setContent(content);
        dialog.showAndWait();
    }

    // --- hàm trợ giúp tạo các dialog cho từng chức năng con ---

    private void showEditGroupDialog(Group group) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Chỉnh sửa thông tin nhóm");

        // mảng một phần tử để lưu file được chọn từ bên trong lambda
        final File[] selectedFile = { null };

        // tạo các thành phần giao diện chính
        VBox content = createDialogLayout();
        ImageView avatarPreview = createAvatarPreview(group.getAvatar());
        Button selectAvatarBtn = createSelectAvatarButton(dialog, avatarPreview, selectedFile);
        TextField nameField = new TextField(group.getName());
        TextArea descArea = createDescriptionArea(group.getDescription());
        VBox fieldsBox = createFormFields(nameField, descArea);

        // thêm các thành phần vào layout chính
        content.getChildren().addAll(
                avatarPreview,
                selectAvatarBtn,
                new Separator(Orientation.HORIZONTAL),
                fieldsBox
        );

        // thiết lập DialogPane
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialogPane.setContent(content);

        // hàm logic cho nút OK
        setupOkButton(dialog, group, nameField, descArea, selectedFile);

        ThemeService.styleDialog(dialog);
        dialog.showAndWait();
    }

    // --- CÁC HÀM TRỢ GIÚP ĐỂ LÀM SẠCH CODE ---

    private VBox createDialogLayout() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.CENTER);
        content.setMinWidth(400);
        content.setMinHeight(500);
        return content;
    }

    private ImageView createAvatarPreview(String initialAvatarUrl) {
        ImageView avatarPreview = new ImageView();
        avatarPreview.setFitHeight(120);
        avatarPreview.setFitWidth(120);
        avatarPreview.setClip(new Circle(60, 60, 60)); // Bo tròn ảnh

        try {
            if (initialAvatarUrl != null && !initialAvatarUrl.isEmpty() && initialAvatarUrl.startsWith("http")) {
                avatarPreview.setImage(new Image(initialAvatarUrl, true));
            } else {
                throw new Exception("Avatar URL không hợp lệ hoặc rỗng.");
            }
        } catch (Exception e) {
            System.err.println("Lỗi tải avatar nhóm: " + e.getMessage() + ". Tải ảnh mặc định.");
            try {
                avatarPreview.setImage(new Image(getClass().getResource("/logo.png").toExternalForm()));
            } catch (Exception ex) {
                System.err.println("Không thể tải ảnh mặc định /logo.png: " + ex.getMessage());
            }
        }
        return avatarPreview;
    }

    private Button createSelectAvatarButton(Dialog<?> owner, ImageView preview, File[] selectedFile) {
        Button selectAvatarBtn = new Button("Thay đổi ảnh đại diện");
        selectAvatarBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Chọn ảnh đại diện");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
            File file = fileChooser.showOpenDialog(owner.getOwner());
            if (file != null) {
                selectedFile[0] = file;
                preview.setImage(new Image(file.toURI().toString()));
            }
        });
        return selectAvatarBtn;
    }

    private TextArea createDescriptionArea(String initialDescription) {
        TextArea descArea = new TextArea(initialDescription);
        descArea.setWrapText(true);
        descArea.setPrefRowCount(5);
        return descArea;
    }

    private VBox createFormFields(TextField nameField, TextArea descArea) {
        VBox fieldsBox = new VBox(5);
        fieldsBox.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label("Tên nhóm:");
        nameLabel.getStyleClass().add("dialog-label");

        Label descLabel = new Label("Mô tả:");
        descLabel.getStyleClass().add("dialog-label");

        fieldsBox.getChildren().addAll(
                nameLabel, nameField,
                descLabel, descArea
        );
        return fieldsBox;
    }

    private void setupOkButton(Dialog<?> dialog, Group group, TextField nameField, TextArea descArea, File[] selectedFile) {
        Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setDisable(nameField.getText().trim().isEmpty());
        nameField.textProperty().addListener((obs, oldV, newV) -> okButton.setDisable(newV.trim().isEmpty()));

        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                // Khi nhấn OK, gọi hàm xử lý logic cập nhật
                handleUpdateGroup(
                        group,
                        nameField.getText().trim(),
                        descArea.getText().trim(),
                        selectedFile[0] // File đã được chọn
                );
            }
            return null;
        });
    }

    // logic upload và cập nhật thông tin nhóm trên một luồng riêng
    private void handleUpdateGroup(Group group, String newName, String newDesc, File newAvatarFile) {
        new Thread(() -> {
            try {
                String finalAvatarUrl = group.getAvatar();

                // nếu có file mới được chọn, tải nó lên và lấy URL
                if (newAvatarFile != null) {
                    Platform.runLater(() -> showAlert("Thông báo", "Đang tải ảnh lên...", Alert.AlertType.INFORMATION));
                    finalAvatarUrl = groupService.uploadGroupAvatar(newAvatarFile);
                }

                // cập nhật thông tin nhóm với dữ liệu mới
                groupService.updateGroup(group.get_id(), newName, newDesc, finalAvatarUrl);

                // cập nhật lại giao diện trên luồng JavaFX
                Platform.runLater(() -> {
                    showAlert("Thành công", "Cập nhật thông tin nhóm thành công!", Alert.AlertType.INFORMATION);
                    loadGroups(); // tải lại danh sách nhóm

                    // nếu nhóm đang được chọn là nhóm vừa sửa, tải lại thông tin chi tiết
                    if (selectedGroup != null && selectedGroup.get_id().equals(group.get_id())) {
                        selectGroup(group);
                    }
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> showAlert("Lỗi", "Cập nhật thất bại: " + ex.getMessage(), Alert.AlertType.ERROR));
            }
        }).start();
    }

    // hàm hiển thị giao diện no chat view (không có người dùng/nhóm được chọn)
    private void showNoChatView() {
        HBox chatHeader = (HBox) mainContainer.lookup("#chatHeader");
        VBox noChatView = (VBox) mainContainer.lookup("#noChatView");
        HBox messageInputContainer = (HBox) mainContainer.lookup("#messageInputContainer");

        if (chatHeader != null) {
            chatHeader.setVisible(false);
            chatHeader.setManaged(false);
        }
        if (noChatView != null) {
            noChatView.setVisible(true);
            noChatView.setManaged(true);
        }
        if (messageInputContainer != null) {
            messageInputContainer.setVisible(false);
            messageInputContainer.setManaged(false);
        }
        if (typingIndicator != null) {
            typingIndicator.setVisible(false);
        }
        messageContainer.getChildren().clear();
    }

    // hàm hiển thị giao diện chat view (có người dùng/nhóm được chọn)
    private void showChatView() {
        HBox chatHeader = (HBox) mainContainer.lookup("#chatHeader");
        VBox noChatView = (VBox) mainContainer.lookup("#noChatView");
        HBox messageInputContainer = (HBox) mainContainer.lookup("#messageInputContainer");

        if (chatHeader != null) {
            chatHeader.setVisible(true);
            chatHeader.setManaged(true);
        }
        if (noChatView != null) {
            noChatView.setVisible(false);
            noChatView.setManaged(false);
        }
        if (messageInputContainer != null) {
            messageInputContainer.setVisible(true);
            messageInputContainer.setManaged(true);
        }
    }

    // hàm tải người dùng
    private void loadUsers() {
        new Thread(() -> {
            try {
                allUsers = chatService.getUsers();
                Platform.runLater(() -> {
                    // lặp qua từng người cập nhật trạng thái onl/off
                    for (User u : allUsers) {
                        if (onlineUserIds.contains(u.get_id())) {
                            u.setOnline(true);
                        }
                        u.updateStatusPreview();
                    }

                    userListView.getItems().clear();
                    userListView.getItems().addAll(allUsers);

                    // vẽ lại toàn bộ danh sách người dùng sau khi chuẩn bị
                    updateListViewBasedOnFilterAndSearch();
                    updateOnlineCountLabel();
                    searchStatusLabel.setVisible(false);
                });
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    showAlert("Lỗi", "Tải người dùng thất bại: " + e.getMessage(), Alert.AlertType.ERROR);
                });
            }
        }).start();
    }

    // logic hàm trợ giúp vẽ lại danh sách sau khi tải hoặc tìm kiếm người dùng
    private void updateListViewBasedOnFilterAndSearch() {
        Platform.runLater(() -> {
            String currentSearchTerm = searchField != null ? searchField.getText().trim() : "";
            List<User> usersToFilter;

            if (!currentSearchTerm.isEmpty() && !latestSearchResults.isEmpty()) {
                usersToFilter = new ArrayList<>(latestSearchResults);
            } else if (currentSearchTerm.isEmpty()) {
                usersToFilter = new ArrayList<>(this.allUsers);
            } else {
                userListView.getItems().clear();
                searchStatusLabel.setText("Không tìm thấy người dùng.");
                searchStatusLabel.setVisible(true);

                currentDisplayedUsers.clear();
                return;
            }

            for (User u : usersToFilter) {
                u.setOnline(onlineUserIds.contains(u.get_id()));
                u.updateStatusPreview();
            }

            List<User> finalUsersToDisplay = new ArrayList<>(usersToFilter);
            if (onlineOnlyCheck != null && onlineOnlyCheck.isSelected()) {
                finalUsersToDisplay = finalUsersToDisplay.stream()
                        .filter(User::isOnline)
                        .toList();
            }

            currentDisplayedUsers.clear();
            currentDisplayedUsers.addAll(finalUsersToDisplay);
            userListView.getItems().setAll(currentDisplayedUsers);

            if (finalUsersToDisplay.isEmpty() && !currentSearchTerm.isEmpty()) {
                searchStatusLabel.setText("Không tìm thấy người dùng phù hợp");
                searchStatusLabel.setVisible(true);
            } else if (finalUsersToDisplay.isEmpty() && currentSearchTerm.isEmpty() && onlineOnlyCheck.isSelected()) {
                searchStatusLabel.setText("Không người có người dùng nào online");
                searchStatusLabel.setVisible(true);
            } else {
                searchStatusLabel.setVisible(false);
            }
        });
    }

    // hàm thực thi tìm kiếm và quản lý giao diện liên quan
    private void performSearch(String searchTearm) {
        userListView.getItems().clear();
        userListView.refresh();
        searchStatusLabel.setText("Đang tìm kiếm...");
        searchStatusLabel.setVisible(true);

        new Thread(() -> {
            try {
                List<User> searchResults = chatService.searchUser(searchTearm);
                Platform.runLater(() -> {
                    latestSearchResults.clear();
                    latestSearchResults.addAll(searchResults);
                    updateListViewBasedOnFilterAndSearch();
                });
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    userListView.getItems().clear();
                    latestSearchResults.clear();
                    searchStatusLabel.setText("Lỗi khi tìm kiếm!");
                    searchStatusLabel.setVisible(true);
                });
            }
        }).start();
    }

    // logic thực hiện khi chọn người dùng
    private void selectUser(User user) {
        this.selectedUser = user;
        this.selectedGroup = null; // cập nhật biến nhóm đã chọn về null

        user.setUnreadCount(0);
        userListView.refresh();
        socketService.emitSeenMessage(user.get_id());

        // vẽ giao diện chat
        Platform.runLater(() -> {
            HBox chatHeader = (HBox) mainContainer.lookup("#chatHeader");
            chatHeader.setVisible(true);
            chatHeader.setManaged(true);

            chatHeader.getChildren().clear();
            Node avatarNode = createAvatarNode(user.getAvatar(), 40, 24);
            avatarNode.getStyleClass().add("chat-header-avatar");

            VBox userInfo = new VBox(5);
            Label userName = new Label(user.getFullName());
            userName.getStyleClass().add("chat-header-name");
            userStatus = new Label(selectedUser.isOnline() ? "Đang hoạt động" : "Ngoại tuyến");
            userStatus.getStyleClass().add("chat-header-status");
            userInfo.getChildren().addAll(userName, userStatus);

            Button closeBtn = new Button();
            FontIcon closeIcon = new FontIcon("mdi2c-close");
            closeIcon.setIconSize(20);
            closeBtn.setGraphic(closeIcon);
            closeBtn.getStyleClass().add("close-button");
            closeBtn.setOnAction(e -> {
                selectedUser = null;
                showNoChatView();
            });

            HBox.setHgrow(userInfo, Priority.ALWAYS);
            chatHeader.getChildren().addAll(avatarNode, userInfo, closeBtn);

            showChatView();
            loadMessages();
        });
    }

    // logic tải tin nhắn cá nhân
    private void loadMessages() {
        if (selectedUser == null) return;

        new Thread(() -> {
            try {
                messages = chatService.getMessages(selectedUser.get_id());
                Platform.runLater(this::renderMessages);
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> showAlert("Lỗi", "Tải tin nhắn thất bại: " + e.getMessage(), Alert.AlertType.ERROR));
            }
        }).start();
    }

    // logic render tin nhắn lên giao diện chat
    private void renderMessages() {
        messageContainer.getChildren().clear();

        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            boolean isMyMessage = message.getSenderId().equals(currentUser.get_id());

            HBox messageBox = new HBox(10);
            messageBox.getStyleClass().add(isMyMessage ? "message-box-right" : "message-box-left");

            VBox messageContent = new VBox(5);
            messageContent.setMaxWidth(400);
            messageContent.setFillWidth(false);

            if (message.getContent() != null && !message.getContent().isEmpty()) {
                Label messageText = new Label(message.getContent());
                messageText.getStyleClass().add("message-text");
                messageText.setWrapText(true);
                messageContent.getChildren().add(messageText);
            }

            Label timeLabel = new Label(formatTime(message.getCreatedAt()));
            timeLabel.getStyleClass().add("message-time");
            HBox statusContainer = new HBox(2);
            statusContainer.getChildren().add(timeLabel);
            messageContent.getChildren().add(statusContainer);

            if (isMyMessage) {
                messageContent.setAlignment(Pos.BOTTOM_RIGHT);
                statusContainer.setAlignment(Pos.BOTTOM_RIGHT);
                Node myAvatar = createAvatarNode(currentUser.getAvatar(), 40, 24);

                if (i == messages.size() - 1) {
                    Label statusLabel = new Label("Đã gửi");
                    statusLabel.getStyleClass().add("message-status");
                    statusLabel.setId("lastMessageStatus");

                    if (selectedUser != null && message.isSeenBy(selectedUser.get_id())) {
                        statusLabel.setText("Đã xem");
                    }
                    statusContainer.getChildren().add(statusLabel);
                }

                messageBox.getChildren().addAll(messageContent, myAvatar);
            } else {
                Node receiverAvatar = createAvatarNode(selectedUser.getAvatar(), 40, 24);
                messageBox.getChildren().addAll(receiverAvatar, messageContent);
            }

            messageContainer.getChildren().add(messageBox);
        }
        messageScrollPane.setVvalue(1.0);
    }

    // logic gửi tin nhắn chung
    private void sendMessage() {
        String content = messageInput.getText().trim();
        if (content.isEmpty()) return;

        if (selectedGroup != null) {
            // gửi tin nhắn nhóm
            groupService.sendGroupMessage(selectedGroup.get_id(), content);
            renderGroupMessages();
        } else if (selectedUser != null) {
            // gửi tin nhắn cá nhân
            chatService.sendMessage(currentUser.get_id(), selectedUser.get_id(), content);

            // tạo 1 tin nhắn tạm để cập nhật ngay lên giao diện
            Message localMsg = new Message();
            localMsg.setSenderId(currentUser.get_id());
            localMsg.setReceiverId(selectedUser.get_id());
            localMsg.setContent(content);
            localMsg.setCreatedAt(Instant.now().toString());
            messages.add(localMsg);
            renderMessages();
        }

        // xóa sạch nội dung trong trường nhập tin nhắn mỗi khi gửi đi
        messageInput.clear();
    }

    // cập nhật các tin nhắn cuối cùng ở vùng cạnh bên (chứa danh sách người dùng/nhóm)
    private void updateSidebarLastMessage(Message message) {
        Platform.runLater(() -> {
            String otherUserId = message.getSenderId().equals(authService.getCurrentUser().get_id())
                    ? message.getReceiverId()
                    : message.getSenderId();

            this.allUsers.stream()
                    .filter(u -> u.get_id().equals(otherUserId))
                    .findFirst()
                    .ifPresent(user -> {
                        User.LastMessage lastMsg = new User.LastMessage();
                        lastMsg.setContent(message.getContent());
                        lastMsg.setCreatedAt(message.getCreatedAt());
                        lastMsg.setIsMine(message.getSenderId().equals(authService.getCurrentUser().get_id()));
                        user.setLastMessage(lastMsg);

                        this.allUsers.remove(user);
                        this.allUsers.add(0, user);
                        updateListViewBasedOnFilterAndSearch();
                        if (selectedUser != null && userListView.getSelectionModel().getSelectedItems() != null) {
                            userListView.getSelectionModel().select(selectedUser);
                        }
                    });
        });
    }

    // logic cập nhật trạng thái đang soạn tin
    private void updateUserTypingStatus(String senderId, boolean isTyping) {
        if (allUsers == null) return;
        allUsers.stream()
                .filter(u -> u.get_id().equals(senderId))
                .findFirst()
                .ifPresent(u -> u.setTyping(isTyping));
    }

    // logic cập nhật số người online
    private void updateOnlineCountLabel() {
        Platform.runLater(() -> {
            if (allUsers == null || onlineCountLabel == null) return;
            long count = userListView.getItems().stream().filter(User::isOnline).count();
            onlineCountLabel.setText("(" + count + " người)");
        });
    }

    // hàm chuyển hướng sang trang thông tin cá nhân
    private void showProfile() {
        if (currentUser == null) {
            showAlert("Lỗi", "Không tìm thấy người dùng hiện tại", Alert.AlertType.ERROR);
            return;
        }
        VBox profileView = createProfileView(currentUser);
        mainContainer.setCenter(profileView);
    }

    // logic khởi tạo trang thông tin cá nhân
    private VBox createProfileView(User user) {
        VBox parentContainer = new VBox(20);
        parentContainer.setPadding(new Insets(15));
        parentContainer.getStyleClass().add("profile-container");
        parentContainer.setAlignment(Pos.TOP_CENTER);

        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_LEFT);
        Button backBtn = new Button("Quay lại");
        FontIcon backIcon = new FontIcon("mdi2a-arrow-left");
        backIcon.setIconSize(18);
        backBtn.setGraphic(backIcon);
        backBtn.getStyleClass().add("back-button");
        backBtn.setOnAction(e -> mainContainer.setCenter(centerContent));
        headerBox.getChildren().add(backBtn);

        // cột hiển thị thông tin cá nhân (ảnh đại diện, tên đầy đủ, email, tên đăng nhập)
        HBox profileContainer = new HBox(50);
        // profileContainer.setMaxWidth(1000);
        // profileContainer.setMinWidth(600);
        profileContainer.setAlignment(Pos.CENTER);
        profileContainer.setPadding(new Insets(20));

        VBox profileContent = new VBox(20);
        profileContent.setAlignment(Pos.TOP_CENTER);
        profileContent.setMinWidth(300);
        profileContent.setPrefWidth(400);

        Node avatarNode = createAvatarNode(user.getAvatar(), 150, 120);
        StackPane avatarContainer = new StackPane(avatarNode);
        avatarContainer.setMinSize(150, 150);

        Label fullnameLabel = new Label(user.getFullName() != null ? user.getFullName() : "N/A");
        fullnameLabel.getStyleClass().add("profile-name");
        Label emailLabel = new Label(user.getEmail() != null ? user.getEmail() : "N/A");
        emailLabel.getStyleClass().add("profile-email");

        Separator horizontalDivider = new Separator();
        horizontalDivider.setMaxWidth(200);

        VBox infoSection = new VBox(15);
        infoSection.setAlignment(Pos.CENTER);
        infoSection.setPadding(new Insets(20, 0, 0, 0));
        infoSection.setMaxWidth(Region.USE_PREF_SIZE);

        HBox usernameInfo = createInfoRow("Tên đăng nhập:", user.getUsername() != null ? user.getUsername() : "N/A");
        HBox fullnameInfo = createInfoRow("Họ tên:", user.getFullName() != null ? user.getFullName() : "N/A");
        HBox emailInfo = createInfoRow("Email:", user.getEmail() != null ? user.getEmail() : "N/A");

        // nút đổi ảnh đại diện và logic đổi ảnh đại diện
        Button changePhotoBtn = new Button("Đổi ảnh đại diện");
        changePhotoBtn.getStyleClass().add("primary-button");
        changePhotoBtn.setMaxWidth(Double.MAX_VALUE);
        changePhotoBtn.setOnAction(e -> {
            javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
            fileChooser.setTitle("Chọn ảnh đại diện");
            fileChooser.getExtensionFilters().add(
                    new javafx.stage.FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
            );
            java.io.File selectedFile = fileChooser.showOpenDialog(primaryStage);
            if (selectedFile != null) {
                changePhotoBtn.setDisable(true);
                changePhotoBtn.setText("Đang tải lên...");

                try {
                    // gọi API upload
                    String serverUrl = userService.updateUserAvatar(selectedFile);

                    // cập nhật User Model
                    user.setAvatar(serverUrl);

                    // cập nhật UI trên JavaFX Application Thread
                    Platform.runLater(() -> {
                        // tạo lại node ảnh mới từ URL server trả về (để đảm bảo link sống)
                        String localUrl = selectedFile.toURI().toString();
                        Node newAvatarNode = createAvatarNode(localUrl, 150, 120);

                        // thay thế nội dung trong Container -> Giao diện sẽ đổi avatar mới ngay lập tức
                        avatarContainer.getChildren().setAll(newAvatarNode);

                        showAlert("Thành công", "Cập nhật avatar thành công", Alert.AlertType.INFORMATION);
                        changePhotoBtn.setDisable(false);
                        changePhotoBtn.setText("Đổi ảnh đại diện");
                    });
                } catch (IOException ex) {
                    ex.printStackTrace();
                    Platform.runLater(() -> {
                        showAlert("Lỗi", "Không thể upload ảnh: " + ex.getMessage(), Alert.AlertType.ERROR);
                        changePhotoBtn.setDisable(false);
                        changePhotoBtn.setText("Đổi ảnh đại diện");
                    });
                }
            }
        });

        infoSection.getChildren().addAll(usernameInfo, fullnameInfo, emailInfo, changePhotoBtn);
        profileContent.getChildren().addAll(avatarContainer, fullnameLabel, emailLabel, horizontalDivider, infoSection);

        Separator verticalDivider = new Separator(Orientation.VERTICAL);
        // verticalDivider.setMaxHeight(500);

        // cột phục vụ đổi mật khẩu
        VBox changePasswordContent = new VBox(20);
        changePasswordContent.setAlignment(Pos.TOP_LEFT);
        changePasswordContent.setPrefWidth(400);
        changePasswordContent.setMinWidth(300);
        changePasswordContent.setPadding(new Insets(0, 0, 0, 30));

        Label changePassTitle = new Label("Đổi mật khẩu");
        changePassTitle.getStyleClass().add("settings-section-title");

        VBox passForm = new VBox(15);

        PasswordField oldPassField = new PasswordField();
        oldPassField.setPromptText("Nhập mật khẩu hiện tại");
        VBox oldPassGroup = createPasswordInputGroup("Mật khẩu cũ", oldPassField);

        PasswordField newPassField = new PasswordField();
        newPassField.setPromptText("Nhập mật khẩu mới");
        VBox newPassGroup = createPasswordInputGroup("Mật khẩu mới", newPassField);

        PasswordField confirmPassField = new PasswordField();
        confirmPassField.setPromptText("Nhập lại mật khẩu mới");
        VBox confirmPassGroup = createPasswordInputGroup("Xác nhận mật khẩu mới", confirmPassField);

        Button updatePassBtn = new Button("Cập nhật mật khẩu");
        updatePassBtn.getStyleClass().add("primary-button");
        updatePassBtn.setPrefWidth(Double.MAX_VALUE);
        updatePassBtn.setOnAction(e -> handleUpdatePassword(oldPassField, newPassField, confirmPassField));

        passForm.getChildren().addAll(oldPassGroup, newPassGroup, confirmPassGroup, updatePassBtn);
        changePasswordContent.getChildren().addAll(changePassTitle, passForm);

        HBox.setHgrow(profileContent, Priority.ALWAYS);
        HBox.setHgrow(changePasswordContent, Priority.ALWAYS);

        profileContainer.getChildren().addAll(profileContent, verticalDivider, changePasswordContent);
        parentContainer.getChildren().addAll(headerBox, profileContainer);

        return parentContainer;
    }

    // hàm hỗ trợ tạo cột hiển thị thông tin cá nhân
    private HBox createInfoRow(String label, String value) {
        HBox row = new HBox(15);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(5, 0, 5, 0));
        Label labelField = new Label(label);
        labelField.getStyleClass().add("profile-info-label");
        labelField.setMinWidth(100);
        Label valueField = new Label(value);
        valueField.getStyleClass().add("profile-info-value");
        row.getChildren().addAll(labelField, valueField);
        return row;
    }

    // hàm hỗ trợ tạo các trường nhập đổi mật khẩu
    private VBox createPasswordInputGroup(String labelText, PasswordField field) {
        VBox group = new VBox(5);
        Label label = new Label(labelText);
        HBox inputWrapper = new HBox();
        inputWrapper.getStyleClass().add("input-container");
        inputWrapper.setAlignment(Pos.CENTER_LEFT);
        inputWrapper.setPrefHeight(45);

        label.getStyleClass().add("form-label");
        field.getStyleClass().add("text-input");
        inputWrapper.getChildren().add(field);
        group.getChildren().addAll(label, inputWrapper);
        return group;
    }

    // logic xử lý đổi mật khẩu
    private void handleUpdatePassword(PasswordField oldF, PasswordField newF, PasswordField confirmF) {
        String oldPass = oldF.getText();
        String newPass = newF.getText();
        String confirmPass = confirmF.getText();

        if (oldPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
            showAlert("Lỗi", "Vui lòng vui lòng nhập đầy đủ thông tin", Alert.AlertType.ERROR);
            return;
        }

        if (!newPass.equals(confirmPass)) {
            showAlert("Lỗi", "Mật khẩu xác nhận không khớp", Alert.AlertType.ERROR);
            return;
        }

        new Thread(() -> {
            try {
                userService.changePassword(oldPass, newPass);
                Platform.runLater(() -> {
                    showAlert("Thành công", "Đổi mật khẩu thành công!", Alert.AlertType.INFORMATION);
                    oldF.clear();
                    newF.clear();
                    confirmF.clear();
                });
            } catch (IOException ex) {
                Platform.runLater(() -> {
                    String rawMsg = ex.getMessage();
                    String displayMsg = "Đổi mật khẩu thất bại";

                    if (rawMsg != null && rawMsg.contains("{")) {
                        try {
                            int jsonStart = rawMsg.indexOf("{");
                            String json = rawMsg.substring(jsonStart);
                            com.google.gson.JsonObject jsonObj = new com.google.gson.Gson().fromJson(json,
                                    com.google.gson.JsonObject.class);
                            if (jsonObj.has("message")) {
                                displayMsg = jsonObj.get("message").getAsString();
                            }
                        } catch (Exception e) {
                            displayMsg = rawMsg;
                        }
                    } else {
                        displayMsg = rawMsg;
                    }

                    showAlert("Lỗi", displayMsg, Alert.AlertType.ERROR);
                });
            }
        }).start();
    }

    // hàm chuyển hướng sang trang cài đặt
    private void showSettings() {
        VBox settingsView = createSettingsView();
        mainContainer.setCenter(settingsView);
    }

    // logic khởi tạo trang cài đặt
    private VBox createSettingsView() {
        VBox settingsContainer = new VBox(20);
        settingsContainer.setPadding(new Insets(30));
        settingsContainer.getStyleClass().add("settings-container");
        settingsContainer.setAlignment(Pos.TOP_CENTER);

        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_LEFT);
        Button backBtn = new Button("Quay lại");
        FontIcon backIcon = new FontIcon("mdi2a-arrow-left");
        backIcon.setIconSize(18);
        backBtn.setGraphic(backIcon);
        backBtn.getStyleClass().add("back-button");
        backBtn.setOnAction(e -> mainContainer.setCenter(centerContent));
        headerBox.getChildren().add(backBtn);

        VBox settingsContent = new VBox(30);
        settingsContent.setAlignment(Pos.TOP_CENTER);
        settingsContent.setMaxWidth(600);

        Label titleLabel = new Label("Cài đặt");
        titleLabel.getStyleClass().add("settings-title");

        VBox themeSection = new VBox(15);
        themeSection.setAlignment(Pos.CENTER_LEFT);
        themeSection.setPadding(new Insets(20));
        themeSection.getStyleClass().add("settings-section");

        Label themeLabel = new Label("Giao diện");
        themeLabel.getStyleClass().add("settings-section-title");

        ToggleGroup themeGroup = new ToggleGroup();
        RadioButton lightTheme = new RadioButton("Sáng (Light)");
        lightTheme.setToggleGroup(themeGroup);
        RadioButton darkTheme = new RadioButton("Tối (Dark)");
        darkTheme.setToggleGroup(themeGroup);

        ThemeService.Theme currentTheme = ThemeService.getTheme();
        if (currentTheme == ThemeService.Theme.DARK) darkTheme.setSelected(true);
        else lightTheme.setSelected(true);

        themeGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == lightTheme) {
                ThemeService.setTheme(ThemeService.Theme.LIGHT);
            } else if (newValue == darkTheme) {
                ThemeService.setTheme(ThemeService.Theme.DARK);
            }
        });

        VBox themeOptions = new VBox(10);
        themeOptions.getChildren().addAll(lightTheme, darkTheme);
        themeSection.getChildren().addAll(themeLabel, themeOptions);

        settingsContent.getChildren().addAll(titleLabel, themeSection);
        settingsContainer.getChildren().addAll(headerBox, settingsContent);
        return settingsContainer;
    }

    // ========== HELPERS ==========

    private String getUserNameById(String userId) {
        if (selectedGroup != null && selectedGroup.getMembers() != null) {
            for (Group.GroupMember member : selectedGroup.getMembers()) {
                if (member.getUser() != null && member.getUser().get_id().equals(userId)) {
                    return member.getUser().getFullName();
                }
            }
        }

        return "Người dùng";
    }

    // hàm định dạng thời gian, hỗ trợ khi render tin nhắn
    private String formatTime(String timeStamp) {
        if (timeStamp == null || timeStamp.isEmpty()) return "";
        try {
            Instant instant = Instant.parse(timeStamp);
            ZonedDateTime zonedDateTime = instant.atZone(ZoneId.systemDefault());
            return zonedDateTime.format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            return "";
        }
    }

    // hàm hiển thị cửa sổ thông báo
    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        ThemeService.styleDialog(alert);
        alert.showAndWait();
    }

    // hàm hỗ trợ hiển thị avatar
    private Node createAvatarNode(String photoUrl, double avatarNodeSize, int iconSize) {
        if (photoUrl != null && !photoUrl.isEmpty()) {
            try {
                ImageView avatar = new ImageView(new Image(photoUrl, true));
                avatar.setFitWidth(avatarNodeSize);
                avatar.setFitHeight(avatarNodeSize);
                avatar.getStyleClass().add("message-avatar");
                return avatar;
            } catch (Exception e) { /* fallback to default */ }
        }
        FontIcon defaultIcon = new FontIcon("mdi2a-account");
        defaultIcon.setIconSize(iconSize);
        defaultIcon.getStyleClass().add("avatar-icon");
        StackPane container = new StackPane(defaultIcon);
        container.setPrefSize(avatarNodeSize, avatarNodeSize);
        container.setMinSize(avatarNodeSize, avatarNodeSize);
        container.getStyleClass().add("default-avatar-container");
        return container;
    }

    // ========== CÁC LỚP LISTCELL (CẤU HÌNH CHO TỪNG ITEM HAY ĐƯỢC SỬ DỤNG TRONG CÁC DANH SÁCH) ==========

    private class UserListCell extends ListCell<User> {
        @Override
        protected void updateItem(User user, boolean empty) {
            super.updateItem(user, empty);
            if (empty || user == null) {
                setGraphic(null);
            } else {
                HBox cell = new HBox(15);
                cell.setPadding(new Insets(10));
                cell.setAlignment(Pos.CENTER_LEFT);

                Node avatarNode = createAvatarNode(user.getAvatar(), 40, 24);

                VBox userInfo = new VBox(2);
                HBox nameRow = new HBox(6);
                nameRow.setAlignment(Pos.CENTER_LEFT);
                Label userName = new Label(user.getFullName());
                userName.getStyleClass().add("user-name");
                Circle onlineDot = new Circle(4);
                onlineDot.getStyleClass().add("online-dot");
                onlineDot.visibleProperty().bind(user.isOnlineProperty());
                nameRow.getChildren().addAll(userName, onlineDot);

                Label statusLabel = new Label();
                statusLabel.getStyleClass().add("last-message-preview");
                statusLabel.setMaxWidth(180);
                statusLabel.textProperty().bind(user.statusPreviewProperty());

                user.isTypingProperty().addListener((obs, wasTyping, isTyping) -> {
                    if (isTyping) {
                        statusLabel.setStyle("-fx-text-fill: #31a24c; -fx-font-style: italic;");
                    } else {
                        statusLabel.setStyle("");
                    }
                });

                userInfo.getChildren().addAll(nameRow, statusLabel);

                if (user.getUnreadCount() > 0) {
                    Label badge = new Label(String.valueOf(user.getUnreadCount()));
                    badge.getStyleClass().add("unread-badge");
                    HBox.setHgrow(userInfo, Priority.ALWAYS);
                    cell.getChildren().addAll(avatarNode, userInfo, badge);
                } else {
                    cell.getChildren().addAll(avatarNode, userInfo);
                }
                setGraphic(cell);
            }
        }
    }

    private class GroupListCell extends ListCell<Group> {
        @Override
        protected void updateItem(Group group, boolean empty) {
            super.updateItem(group, empty);
            if (empty || group == null) {
                setGraphic(null);
            } else {
                HBox cell = new HBox(15);
                cell.setPadding(new Insets(10));
                cell.setAlignment(Pos.CENTER_LEFT);

                Node avatarNode = createAvatarNode(group.getAvatar(), 40, 24);

                VBox groupInfo = new VBox(2);
                Label groupName = new Label(group.getName());
                groupName.getStyleClass().add("user-name");

                Label statusLabel = new Label();
                statusLabel.getStyleClass().add("last-message-preview");
                statusLabel.setMaxWidth(180);
                statusLabel.textProperty().bind(group.statusPreviewProperty());

                groupInfo.getChildren().addAll(groupName, statusLabel);

                if (group.getUnreadCount() > 0) {
                    Label badge = new Label(String.valueOf(group.getUnreadCount()));
                    badge.getStyleClass().add("unread-badge");
                    HBox.setHgrow(groupInfo, Priority.ALWAYS);
                    cell.getChildren().addAll(avatarNode, groupInfo, badge);
                } else {
                    cell.getChildren().addAll(avatarNode, groupInfo);
                }

                setGraphic(cell);
            }
        }
    }

    private class GroupMemberCell extends ListCell<Group.GroupMember> {
        private final Group groupContext;

        public GroupMemberCell(Group group) {
            this.groupContext = group;
        }

        @Override
        protected void updateItem(Group.GroupMember member, boolean empty) {
            super.updateItem(member, empty);
            if (empty || member == null || member.getUser() == null || groupContext == null) {
                setGraphic(null);
            } else {
                HBox cell = new HBox(10);
                cell.setAlignment(Pos.CENTER_LEFT);
                Node avatar = createAvatarNode(member.getUser().getAvatar(), 40, 24);

                VBox info = new VBox(2);
                Label nameLabel = new Label(member.getUser().getFullName());
                nameLabel.getStyleClass().add("user-name");
                info.getChildren().add(nameLabel);

                boolean isCurrentUserOwner = groupContext.isUserOwner(currentUser.get_id());
                boolean isThisMemberTheOwner = groupContext.isUserOwner(member.getUser().get_id());

                // --- LOGIC UI HIỂN THỊ VAI TRÒ ---
                // nếu người dùng hiện tại là owner, hiển thị ComboBox để đổi vai trò
                if (isCurrentUserOwner && !isThisMemberTheOwner) {
                    ComboBox<String> roleComboBox = new ComboBox<>();
                    roleComboBox.getItems().addAll("Quản trị viên", "Thành viên");
                    roleComboBox.setValue(member.isAdmin() ? "Quản trị viên" : "Thành viên");
                    roleComboBox.getStyleClass().add("role-combo-box");

                    roleComboBox.setOnAction(e -> {
                        String selectedRole = roleComboBox.getValue();
                        String newRoleApi = "Quản trị viên".equals(selectedRole) ? "admin" : "member";
                        handleChangeRole(member, newRoleApi);
                    });

                    info.getChildren().add(roleComboBox);

                } else {
                    // ngược lại, chỉ hiển thị label
                    String roleText;
                    if (isThisMemberTheOwner) {
                        roleText = "Chủ nhóm";
                    } else if (member.isAdmin()) {
                        roleText = "Quản trị viên";
                    } else {
                        roleText = "Thành viên";
                    }
                    Label roleLabel = new Label(roleText);
                    roleLabel.getStyleClass().add("last-message-preview");
                    info.getChildren().add(roleLabel);
                }

                HBox.setHgrow(info, Priority.ALWAYS);
                cell.getChildren().addAll(avatar, info);

                // --- NÚT XÓA THÀNH VIÊN ---
                // quyền xóa: owner hoặc admin
                boolean isCurrentUserAdmin = groupContext.isUserAdmin(currentUser.get_id());
                boolean isMe = currentUser.get_id().equals(member.getUser().get_id());
                boolean canDelete = (isCurrentUserOwner || isCurrentUserAdmin) && !isMe && !isThisMemberTheOwner;

                // admin không thể xóa admin khác (chỉ owner làm được)
                if (isCurrentUserAdmin && !isCurrentUserOwner && member.isAdmin()) {
                    canDelete = false;
                }

                if (canDelete) {
                    Button deleteBtn = new Button();
                    FontIcon deleteIcon = new FontIcon("mdi2t-trash-can-outline");
                    deleteIcon.setIconColor(javafx.scene.paint.Color.RED);
                    deleteIcon.setIconSize(20);
                    deleteBtn.setGraphic(deleteIcon);
                    deleteBtn.getStyleClass().add("icon-button");
                    deleteBtn.setTooltip(new Tooltip("Xóa thành viên khỏi nhóm"));

                    deleteBtn.setOnAction(e -> handleDeleteMember(member));

                    cell.getChildren().add(deleteBtn);
                }

                setGraphic(cell);
            }
        }

        // logic xử lý đổi vai trò cho thành viên
        private void handleChangeRole(Group.GroupMember member, String newRole) {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                    "Bạn có chắc muốn đổi vai trò của " + member.getUser().getFullName() + " thành " + newRole + "?",
                    ButtonType.YES, ButtonType.NO);

            ThemeService.styleDialog(confirmation);
            confirmation.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    new Thread(() -> {
                        try {
                            groupService.changeRole(groupContext.get_id(), member.getUser().get_id(), newRole);
                            // lấy lại thông tin mới
                            Group updatedGroup = groupService.getGroupInfo(groupContext.get_id());

                            Platform.runLater(() -> {
                                showAlert("Thành công", "Đã cập nhật vai trò thành công.", Alert.AlertType.INFORMATION);

                                // cập nhật lại danh sách thành viên với quyền mới
                                groupContext.setMembers(updatedGroup.getMembers());

                                // hiển thị lại danh sách thành viên
                                getListView().getItems().setAll(updatedGroup.getMembers());
                            });
                        } catch (Exception ex) {
                            Platform.runLater(() -> showAlert("Lỗi", "Không thể đổi vai trò: " + ex.getMessage(), Alert.AlertType.ERROR));
                        }
                    }).start();
                } else {
                    // nếu người dùng chọn NO, reset lại ComboBox
                    getListView().refresh();
                }
            });
        }

        // logic xử lý xóa thành viên
        private void handleDeleteMember(Group.GroupMember member) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "Bạn có chắc chắn muốn xóa thành viên " + member.getUser().getFullName() + " khỏi nhóm?",
                    ButtonType.YES, ButtonType.NO);

            alert.showAndWait().ifPresent(type -> {
                if (type == ButtonType.YES) {
                    new Thread(() -> {
                        try {
                            boolean success = groupService.removeMember(groupContext.get_id(),
                                    member.getUser().get_id());
                            if (success) {
                                // lấy lại dữ liệu nhóm với danh sách thành viên mới
                                Group updatedGroup = groupService.getGroupInfo(groupContext.get_id());

                                Platform.runLater(() -> {
                                    // xóa thành viên khỏi danh sách hiện tại
                                    getListView().getItems().remove(member);
                                    groupContext.setMembers(updatedGroup.getMembers());

                                    // cập nhật lại header để hiển thị đúng số thành viên nhóm
                                    renderSelectedGroupUI(updatedGroup);

                                    showAlert("Thành công", "Đã xóa thành viên khỏi nhóm.",
                                            Alert.AlertType.INFORMATION);
                                });
                            }
                        } catch (Exception ex) {
                            Platform.runLater(() -> showAlert("Lỗi", "Xóa thành viên thất bại: " + ex.getMessage(),
                                    Alert.AlertType.ERROR));
                        }
                    }).start();
                }
            });
        }
    }
}