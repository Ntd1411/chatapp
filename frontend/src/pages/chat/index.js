import authService from '../../services/auth.service.js';
import chatService from '../../services/chat.service.js';
import { userAPI } from '../../api/user.api.js';
import { groupAPI } from '../../api/group.api.js';
import { createSocket } from '../../realtime/socket.js';
import { escapeHtml } from '../../utils/escapeHtml.js';
import { initGroupUI } from './group.module.js';

// DOM Elements
const logoutBtn = document.getElementById('logout-btn');
const usersList = document.getElementById('users-list');
const searchInput = document.getElementById('search-input');
const noChatSelected = document.getElementById('no-chat-selected');
const chatMessagesContainer = document.getElementById('chat-messages-container');
const chatWithName = document.getElementById('chat-with-name');
const chatNameText = chatWithName?.querySelector('.chat-name-text');
const chatOnlineIndicator = document.getElementById('chat-online-indicator');
const messagesList = document.getElementById('messages-list');
const messageInput = document.getElementById('message-input');
const sendBtn = document.getElementById('send-btn');
const profileName = document.getElementById('profile-name');
const profileEmail = document.getElementById('profile-email');
const profileAvatar = document.getElementById('profile-avatar');
const profileCard = document.getElementById('profile-card');
const profileModal = document.getElementById('profile-modal');
const profileModalOverlay = document.getElementById('profile-modal-overlay');
const profileModalClose = document.getElementById('profile-modal-close');
const profileModalAvatar = document.getElementById('profile-modal-avatar');
const profileModalName = document.getElementById('profile-modal-name');
const profileModalUsername = document.getElementById('profile-modal-username');
const profileModalEmail = document.getElementById('profile-modal-email');
const profileModalFullName = document.getElementById('profile-modal-fullname');
const profileModalUsernameDetail = document.getElementById('profile-modal-username-detail');
const profileModalEmailDetail = document.getElementById('profile-modal-email-detail');
const profileEditForm = document.getElementById('profile-edit-form');
const profileEditFullName = document.getElementById('profile-edit-fullname');
const profileEditUsername = document.getElementById('profile-edit-username');
const profileEditEmail = document.getElementById('profile-edit-email');
const profileSaveBtn = document.getElementById('profile-save-btn');
const profileUpdateMessage = document.getElementById('profile-update-message');
const avatarUploadBtn = document.getElementById('avatar-upload-btn');
const avatarFileInput = document.getElementById('avatar-file-input');
const avatarUploadStatus = document.getElementById('avatar-upload-status');
const typingIndicator = document.getElementById('typing-indicator');

let currentProfile = null;

// Map to store online status of users: userId -> boolean
const onlineUsers = new Map();

// Typing timeout reference
let typingTimeout = null;
let groupTypingTimeout = null;
const TYPING_TIMEOUT = 1000; // 1 giây

// Search debounce timeout
let searchTimeout = null;
const SEARCH_DEBOUNCE = 500; // 500ms

// Current active tab
let currentActiveTab = 'users';

if (profileEditForm) {
  setProfileFormDisabled(true);
  profileEditForm.addEventListener('submit', handleProfileUpdate);
}

if (avatarUploadBtn && avatarFileInput) {
  avatarUploadBtn.addEventListener('click', () => {
    avatarFileInput.click();
  });
  avatarFileInput.addEventListener('change', handleAvatarFileChange);
}

if (profileCard) {
  profileCard.addEventListener('click', (event) => {
    event.preventDefault();
    openProfileModal();
  });
}

profileModalOverlay?.addEventListener('click', () => closeProfileModal());
profileModalClose?.addEventListener('click', () => closeProfileModal());

document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape' && profileModal?.classList.contains('show')) {
    closeProfileModal();
  }
});

// Logout handler
logoutBtn.addEventListener('click', async () => {
  await authService.logout();
  window.location.href = '/index.html';
});

// Search handler - works for both users and groups
if (searchInput) {
  searchInput.addEventListener('input', async (e) => {
    const searchTerm = e.target.value.trim();
    
    // Clear previous timeout
    if (searchTimeout) {
      clearTimeout(searchTimeout);
    }
    
    // Debounce search
    searchTimeout = setTimeout(async () => {
      if (searchTerm === '') {
        // Nếu không có search term, load tất cả items của active tab
        if (currentActiveTab === 'users') {
          await loadUsers();
        } else if (currentActiveTab === 'groups') {
          await groupUI.loadGroups();
        }
      } else {
        // Gọi API search dựa trên tab hiện tại
        if (currentActiveTab === 'users') {
          usersList.innerHTML = '<div class="loading">Đang tìm kiếm...</div>';
          try {
            const response = await userAPI.searchs(searchTerm);
            const users = response.data?.users || [];
            renderUsers(users);
          } catch (error) {
            const message = error.response?.data?.message || 'Lỗi khi tìm kiếm';
            usersList.innerHTML = `<div class="error-message">${message}</div>`;
          }
        } else if (currentActiveTab === 'groups') {
          // Filter groups based on search
          const groupsList = document.getElementById('groups-list');
          const searchInput = document.getElementById('search-input');
          
          // Trigger search in groupUI by simulating input event
          // We'll search through allGroups in the groupUI
          const allGroups = groupUI.getAllGroups ? groupUI.getAllGroups() : [];
          const filteredGroups = allGroups.filter(group =>
            group.name.toLowerCase().includes(searchTerm.toLowerCase())
          );
          
          if (filteredGroups.length === 0) {
            groupsList.innerHTML = `
              <button id="create-group-btn" class="btn btn-primary btn-create-group">+ Tạo nhóm</button>
              <div class="empty-state">Không tìm thấy nhóm nào</div>
            `;
            const btn = document.getElementById('create-group-btn');
            if (btn) {
              btn.addEventListener('click', () => groupUI.openGroupModal());
            }
          } else {
            groupUI.renderGroupsDirectly(filteredGroups);
          }
        }
      }
    }, SEARCH_DEBOUNCE);
  });
}

// Load users list
async function loadUsers() {
  usersList.innerHTML = '<div class="loading">Đang tải...</div>';

  const result = await chatService.loadUsers();

  if (result.success) {
    renderUsers(result.users);
  } else {
    usersList.innerHTML = `<div class="error-message">${result.message}</div>`;
  }
}

// Render users list
function renderUsers(users) {
  if (users.length === 0) {
    usersList.innerHTML = '<div class="loading">Không có người dùng nào</div>';
    return;
  }

  usersList.innerHTML = users
    .map((user) => {
      const displayName = user.fullName || user.username || 'Người dùng';
      const username = user.username || '';
      const initials =
        displayName
          .split(' ')
          .filter(Boolean)
          .map((part) => part[0])
          .join('')
          .slice(0, 2)
          .toUpperCase() || 'U';
      const avatarUrl = user.avatar || '';

      const isOnline = onlineUsers.get(user._id.toString()) || false;
      const avatarClass = avatarUrl ? 'user-item-avatar has-image' : 'user-item-avatar';
      const avatarStyle = avatarUrl ? `style="background-image: url('${avatarUrl}');"` : '';
      const itemClass = isOnline ? 'user-item online' : 'user-item';

      // Unread badge
      const unreadBadge = user.unreadCount > 0 
        ? `<span class="unread-badge">${user.unreadCount}</span>` 
        : '';

      // Last message
      let lastMessageText = '';
      if (user.lastMessage) {
        const prefix = user.lastMessage.isMine ? 'Bạn: ' : '';
        const content = user.lastMessage.content || '';
        const truncated = content.length > 30 
          ? content.slice(0, 30) + '...' 
          : content;
        lastMessageText = prefix + truncated;
      }

      return `
      <div class="${itemClass}" data-user-id="${user._id}" data-user-name="${displayName}">
        <div class="${avatarClass}" ${avatarStyle}>
          ${avatarUrl ? '' : initials}
        </div>
        <div class="user-item-meta">
          <h4>
            ${displayName}
            <span class="user-item-online-dot"></span>
            ${unreadBadge}
          </h4>
          <p class="last-message">${lastMessageText || '@' + username}</p>
        </div>
      </div>
    `;
    })
    .join('');

  // Add click event to user items
  document.querySelectorAll('.user-item').forEach((item) => {
    item.addEventListener('click', () => {
      const userId = item.dataset.userId;
      const userName = item.dataset.userName;

      // Remove active class from all items
      document.querySelectorAll('.user-item').forEach((i) => i.classList.remove('active'));
      // Add active class to clicked item
      item.classList.add('active');

      // Clear unread badge when opening chat
      const badge = item.querySelector('.unread-badge');
      if (badge) {
        badge.remove();
      }

      selectUser(userId, userName);
    });
  });
}

// Select user and load messages
async function selectUser(userId, userName) {
  chatService.selectUser(userId, userName);

  noChatSelected.style.display = 'none';
  chatMessagesContainer.style.display = 'flex';

  // Hide group menu for 1-on-1 chats
  const groupMenuPanel = document.getElementById('group-menu-panel');
  const toggleMenuBtn = document.getElementById('toggle-group-menu-btn');
  if (groupMenuPanel) groupMenuPanel.style.display = 'none';
  if (toggleMenuBtn) toggleMenuBtn.style.display = 'none';

  // Update chat header with name and online status
  if (chatNameText) {
    chatNameText.textContent = userName;
  } else {
    chatWithName.textContent = userName;
  }

  // Update online indicator in header
  updateChatHeaderOnlineStatus(userId);

  messagesList.innerHTML = '<div class="loading">Đang tải tin nhắn...</div>';

  const result = await chatService.loadMessages(userId);

  if (result.success) {
    renderMessages(result.messages);

    // Emit seen-message event để thông báo đã xem tin nhắn từ friend
    socket.emit('seen-message', {
      senderId: userId, // ID của người gửi tin (friend)
    });
  } else {
    messagesList.innerHTML = `<div class="error-message">${result.message}</div>`;
  }

  // Scroll to bottom
  messagesList.scrollTop = messagesList.scrollHeight;
}

// Scroll to the latest message
function scrollToLatestMessage() {
  if (!messagesList) return;
  messagesList.scrollTop = messagesList.scrollHeight;
}

function setProfileFormDisabled(isDisabled) {
  [profileEditFullName, profileEditUsername, profileEditEmail].forEach((input) => {
    if (input) {
      input.disabled = isDisabled;
    }
  });
  if (profileSaveBtn) {
    profileSaveBtn.disabled = isDisabled;
  }
}

function populateProfileForm(user) {
  if (!profileEditFullName || !profileEditUsername || !profileEditEmail) return;
  profileEditFullName.value = user?.fullName || '';
  profileEditUsername.value = user?.username || '';
  profileEditEmail.value = user?.email || '';
}

function showProfileUpdateMessage(message, type = '') {
  if (!profileUpdateMessage) return;
  profileUpdateMessage.textContent = message;
  if (!message) {
    profileUpdateMessage.removeAttribute('data-type');
  } else {
    profileUpdateMessage.dataset.type = type || 'info';
  }
}

function resetProfileUpdateMessage() {
  showProfileUpdateMessage('', '');
}

function showAvatarStatus(message, type = '') {
  if (!avatarUploadStatus) return;
  avatarUploadStatus.textContent = message;
  if (!message) {
    avatarUploadStatus.removeAttribute('data-type');
  } else {
    avatarUploadStatus.dataset.type = type || 'info';
  }
}

function resetAvatarStatus() {
  showAvatarStatus('', '');
}

function applyAvatarVisual(avatarUrl, fallbackInitials = '?') {
  const elements = [profileAvatar, profileModalAvatar];
  elements.forEach((el) => {
    if (!el) return;
    if (avatarUrl) {
      el.classList.add('has-image');
      el.style.backgroundImage = `url("${avatarUrl}")`;
      el.textContent = '';
    } else {
      el.classList.remove('has-image');
      el.style.backgroundImage = '';
      el.textContent = fallbackInitials;
    }
  });
}

function normalizeDate(value) {
  const date = value instanceof Date ? value : new Date(value);
  return Number.isNaN(date.getTime()) ? new Date() : date;
}

function formatShortTime(date) {
  return normalizeDate(date).toLocaleTimeString('en-GB', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  });
}

function formatFullTime(date) {
  return normalizeDate(date).toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

function buildStatusTooltip(action, date) {
  return `${action} lúc ${formatFullTime(date)}`;
}

function applyStatusMetadata(statusDiv, labelText, action, timestamp) {
  const safeDate = normalizeDate(timestamp || statusDiv.dataset.baseTimestamp);
  statusDiv.dataset.baseTimestamp = safeDate.toISOString();
  statusDiv.dataset.action = action;
  statusDiv.title = buildStatusTooltip(action, safeDate);

  let labelEl = statusDiv.querySelector('.message-status-label');
  if (labelText) {
    if (!labelEl) {
      labelEl = document.createElement('span');
      labelEl.classList.add('message-status-label');
      statusDiv.appendChild(labelEl);
    }
    labelEl.textContent = labelText;
  } else if (labelEl) {
    labelEl.remove();
  }

  let timeEl = statusDiv.querySelector('.message-status-time');
  if (!timeEl) {
    timeEl = document.createElement('span');
    timeEl.classList.add('message-status-time');
    statusDiv.appendChild(timeEl);
  }
  const separator = labelText ? ' · ' : '';
  timeEl.textContent = `${separator}${formatShortTime(safeDate)}`;
}

function removeRetryButton(statusDiv) {
  const retryBtn = statusDiv.querySelector('.retry-btn');
  if (retryBtn) {
    retryBtn.remove();
  }
}

function buildStatusHtml(labelText, action, timestamp) {
  const safeDate = normalizeDate(timestamp);
  const tooltip = buildStatusTooltip(action, safeDate);
  const shortTime = formatShortTime(safeDate);
  const isoString = safeDate.toISOString();
  const hasLabel = Boolean(labelText);
  const separator = hasLabel ? ' · ' : '';
  const labelSpan = hasLabel
    ? `<span class="message-status-label">${labelText}</span>`
    : '';

  return `
    <div class="message-status" data-base-timestamp="${isoString}" data-action="${action}" title="${tooltip}">
      ${labelSpan}
      <span class="message-status-time">${separator}${shortTime}</span>
    </div>
  `;
}

// Clear "Đã gửi" và "Đã xem" status from old messages, keep "Đang gửi..." và "Gửi thất bại"
function clearDeliveredStatuses() {
  const statusElements = messagesList?.querySelectorAll('.message-status');
  if (!statusElements) return;

  statusElements.forEach((statusEl) => {
    const labelText = statusEl.querySelector('.message-status-label')?.textContent?.trim();
    if (labelText === 'Đã gửi' || labelText === 'Đã xem') {
      statusEl.innerHTML = '';
      statusEl.removeAttribute('title');
      delete statusEl.dataset.baseTimestamp;
      delete statusEl.dataset.action;
    }
  });
}

// Render messages
function renderMessages(messages) {
  const currentUser = authService.getUser();
  const currentUserId = currentUser?._id;
  const friendId = chatService.selectedUserId;

  if (messages.length === 0) {
    messagesList.innerHTML = '<div class="loading">Chưa có tin nhắn nào</div>';
    return;
  }
  messagesList.innerHTML = messages
    .map((message) => {
      // Handle both string and object senderId
      const senderId =
        typeof message.senderId === 'object'
          ? message.senderId._id || message.senderId.id || message.senderId.toString()
          : message.senderId.toString();

      const isSent = senderId === currentUserId?.toString() || senderId === currentUserId;
      
      // Check if message has been seen by friend
      let statusLabel = '';
      if (isSent) {
        if (message.seenBy && Array.isArray(message.seenBy) && message.seenBy.length > 0) {
          // Check if friendId is in seenBy array
          const seenByIds = message.seenBy.map(id => 
            typeof id === 'object' ? (id._id || id.toString()) : id.toString()
          );
          const isSeen = seenByIds.includes(friendId?.toString());
          statusLabel = isSeen ? 'Đã xem' : 'Đã gửi';
        } else {
          statusLabel = 'Đã gửi';
        }
      }
      const statusAction = isSent ? 'Gửi' : 'Nhận';

      return `
      <div class="message ${isSent ? 'sent' : 'received'}">
        <div class="message-content">${escapeHtml(message.content)}</div>
        ${buildStatusHtml(statusLabel, statusAction, message.createdAt)}
      </div>
    `;
    })
    .join('');

  // Scroll to bottom after rendering history
  scrollToLatestMessage();
}

// Send message handler
sendBtn.addEventListener('click', sendMessage);
messageInput.addEventListener('keypress', (e) => {
  if (e.key === 'Enter') {
    sendMessage();
  }
});

// Typing indicator handler
messageInput.addEventListener('input', () => {
  const currentGroupId = groupUI.getCurrentGroupId();
  
  // Handle group typing
  if (currentGroupId) {
    socket.emit('group-typing-start', { groupId: currentGroupId });
    
    if (groupTypingTimeout) {
      clearTimeout(groupTypingTimeout);
    }
    
    groupTypingTimeout = setTimeout(() => {
      socket.emit('group-typing-stop', { groupId: currentGroupId });
    }, TYPING_TIMEOUT);
    return;
  }

  // Handle user typing (original logic)
  if (!chatService.selectedUserId) return;

  // Emit typing-start event
  socket.emit('typing-start', {
    receiverId: chatService.selectedUserId,
  });

  // Clear previous timeout
  if (typingTimeout) {
    clearTimeout(typingTimeout);
  }

  // Set new timeout to emit typing-stop
  typingTimeout = setTimeout(() => {
    socket.emit('typing-stop', {
      receiverId: chatService.selectedUserId,
    });
  }, TYPING_TIMEOUT);
});

function updateChatHeaderOnlineStatus(userId) {
  if (!chatOnlineIndicator) return;
  const isOnline = onlineUsers.get(userId?.toString()) || false;
  if (isOnline) {
    chatOnlineIndicator.classList.add('online');
  } else {
    chatOnlineIndicator.classList.remove('online');
  }
}

function showTypingIndicator(senderName) {
  if (!typingIndicator) return;
  const text = typingIndicator.querySelector('.typing-text');
  if (text) {
    text.textContent = `${senderName || 'Người dùng'} đang nhập...`;
  }
  typingIndicator.style.display = 'block';
}

function hideTypingIndicator() {
  if (!typingIndicator) return;
  typingIndicator.style.display = 'none';
}

// Send message function
async function sendMessage() {
  const message = messageInput.value.trim();
  if (!message) return;

  // Check if sending group message
  const currentGroupId = groupUI.getCurrentGroupId();
  if (currentGroupId) {
    // Send group message
    groupUI.sendGroupMessage(message);
    messageInput.value = '';
    return;
  }

  // Send user message (original logic)
  if (!chatService.selectedUserId) return;

  const currentUser = authService.getUser();
  const currentUserId = currentUser?._id;

  // Add message to UI immediately with pending status
  const mess = document.createElement('div');
  mess.classList.add('message', 'sent');

  const content = document.createElement('div');
  content.classList.add('message-content');
  content.textContent = message; // tránh XSS

  // Add status
  const statusDiv = document.createElement('div');
  statusDiv.classList.add('message-status');
  applyStatusMetadata(statusDiv, 'Đang gửi...', 'Gửi', new Date());

  mess.appendChild(content);
  mess.appendChild(statusDiv);
  messagesList.appendChild(mess);

  // Clear input
  messageInput.value = '';

  // Scroll to bottom
  scrollToLatestMessage();

  // Emit message to server with callback
  socket.emit('send-message', {
    receiverId: chatService.selectedUserId,
    content: message,
  }, (response) => {
    if (response && response.success) {
      // Update status to sent
      applyStatusMetadata(statusDiv, 'Đã gửi', 'Gửi', new Date());
      
      // Update last message in user list (tin mình gửi)
      updateUserItemLastMessage(chatService.selectedUserId, message, true);
    } else {
      // Update status to failed
      applyStatusMetadata(statusDiv, 'Gửi thất bại', 'Lỗi', new Date());
      statusDiv.classList.add('failed');
    }
  });
}

async function handleProfileUpdate(event) {
  event.preventDefault();

  resetProfileUpdateMessage();

  const payload = {
    fullName: profileEditFullName?.value?.trim() || '',
    username: profileEditUsername?.value?.trim() || '',
    email: profileEditEmail?.value?.trim() || '',
  };

  if (!payload.fullName || !payload.username || !payload.email) {
    showProfileUpdateMessage('Vui lòng nhập đầy đủ thông tin', 'error');
    return;
  }

  setProfileFormDisabled(true);
  showProfileUpdateMessage('Đang lưu thay đổi...', 'info');

  try {
    await userAPI.updateAccount(payload);
    const updatedUser = authService.updateStoredUser(payload);
    renderProfile(updatedUser);
    showProfileUpdateMessage('Cập nhật thành công', 'success');
  } catch (error) {
    const message = error.response?.data?.message || 'Cập nhật thất bại, vui lòng thử lại';
    showProfileUpdateMessage(message, 'error');
  } finally {
    setProfileFormDisabled(false);
  }
}

function renderProfile(user) {
  currentProfile = user;
  if (!user) {
    profileName.textContent = 'Khách';
    profileEmail.textContent = '';
    applyAvatarVisual(null, '?');
    profileModalName.textContent = 'Khách';
    profileModalUsername.textContent = '@guest';
    profileModalEmail.textContent = '';
    profileModalFullName.textContent = '-';
    profileModalUsernameDetail.textContent = '-';
    profileModalEmailDetail.textContent = '-';
    applyAvatarVisual(null, '?');
    populateProfileForm(null);
    setProfileFormDisabled(true);
    return;
  }

  const displayName = user.fullName || user.username || 'Người dùng';
  const username = user.username || '';
  const displayEmail = user.email || (username ? `@${username}` : '');
  const initials =
    displayName
      .split(' ')
      .filter(Boolean)
      .map((part) => part[0])
      .join('')
      .slice(0, 2)
      .toUpperCase() || 'U';

  profileName.textContent = displayName;
  profileEmail.textContent = displayEmail;
  applyAvatarVisual(user.avatar || null, initials);
  profileModalAvatar.textContent = initials;
  profileModalName.textContent = displayName;
  profileModalUsername.textContent = username ? `@${username}` : '';
  profileModalEmail.textContent = displayEmail;
  profileModalFullName.textContent = displayName;
  profileModalUsernameDetail.textContent = username || '-';
  profileModalEmailDetail.textContent = user.email || '-';
  populateProfileForm(user);
  setProfileFormDisabled(false);
}

function openProfileModal() {
  if (!currentProfile || !profileModal) return;
  populateProfileForm(currentProfile);
  resetProfileUpdateMessage();
  profileModal.classList.add('show');
  profileModal.setAttribute('aria-hidden', 'false');
}

function closeProfileModal() {
  if (!profileModal) return;
  profileModal.classList.remove('show');
  profileModal.setAttribute('aria-hidden', 'true');
}

async function handleAvatarFileChange(event) {
  const file = event.target.files?.[0];
  if (!file) return;

  resetAvatarStatus();
  showAvatarStatus('Đang tải ảnh lên...', 'info');

  try {
    const response = await userAPI.uploadAvatar(file);
    const avatarUrl = response?.data?.avatarUrl;

    if (avatarUrl) {
      const updatedUser = authService.updateStoredUser({ avatar: avatarUrl });
      renderProfile(updatedUser);
      showAvatarStatus('Cập nhật ảnh đại diện thành công', 'success');
    } else {
      showAvatarStatus('Tải ảnh thành công nhưng không nhận được URL', 'error');
    }
  } catch (error) {
    const message = error.response?.data?.message || 'Upload avatar thất bại, vui lòng thử lại';
    showAvatarStatus(message, 'error');
  } finally {
    // Clear file input so user can re-select same file
    if (avatarFileInput) avatarFileInput.value = '';
  }
}

// Initialize chat page
async function init() {
  // Check authentication
  const user = await authService.checkAuth();
  if (!user) {
    // Not authenticated, redirect to login
    window.location.href = '/index.html';
    return;
  }

  renderProfile(user);

  // Load users list
  await loadUsers();
}

const socket = createSocket();

socket.on('receive-message', (message) => {
  const mess = document.createElement('div');
  mess.classList.add('message', 'received');

  const content = document.createElement('div');
  content.classList.add('message-content');
  const messageContent = typeof message === 'string' ? message : message?.content || '';
  content.textContent = messageContent; // tránh XSS

  const statusDiv = document.createElement('div');
  statusDiv.classList.add('message-status');
  applyStatusMetadata(statusDiv, '', 'Nhận', new Date());

  mess.appendChild(content);
  mess.appendChild(statusDiv);
  messagesList.appendChild(mess);

  // Tự động cuộn xuống tin nhắn mới nhất khi nhận tin
  scrollToLatestMessage();

  // Emit seen-message nếu đang xem chat với người gửi
  const senderId = message.senderId || message.sender;
  if (
    senderId &&
    chatService.selectedUserId &&
    senderId.toString() === chatService.selectedUserId.toString()
  ) {
    // Gửi thông báo đã xem tin nhắn về cho người gửi
    socket.emit('seen-message', {
      senderId: senderId.toString(), // ID người gửi tin
    });
  }

  // Update user list: lastMessage và unreadCount
  updateUserItemOnNewMessage(senderId, messageContent);
});

socket.on('connect_error', (err) => {
  console.log('Auth error:', err.message);
});

socket.on('connect', () => {
  console.log('Connected to server');
  // thông báo tôi đang on
  socket.emit('entering');
});

socket.on('disconnect', () => {
  console.log('Disconnected from server');
  // thông báo tôi đang off
  socket.emit('leaving');
});

// Update online status for a specific user in the UI
function updateUserOnlineStatus(userId, isOnline) {
  if (!userId) return;

  const userIdStr = userId.toString();
  onlineUsers.set(userIdStr, isOnline);

  // Find the user item in the sidebar
  const userItem = document.querySelector(`.user-item[data-user-id="${userIdStr}"]`);
  if (userItem) {
    if (isOnline) {
      userItem.classList.add('online');
    } else {
      userItem.classList.remove('online');
    }
  }

  // Update chat header if this is the currently selected user
  const currentSelectedUserId = chatService.selectedUserId?.toString();
  if (currentSelectedUserId === userIdStr) {
    updateChatHeaderOnlineStatus(userIdStr);
  }
}

// Update user item when receiving new message
function updateUserItemOnNewMessage(senderId, messageContent) {
  if (!senderId) return;
  
  const senderIdStr = senderId.toString();
  const userItem = document.querySelector(`.user-item[data-user-id="${senderIdStr}"]`);
  
  if (!userItem) return;
  
  // Update last message
  const lastMessageEl = userItem.querySelector('.last-message');
  if (lastMessageEl) {
    const truncated = messageContent.length > 30 
      ? messageContent.slice(0, 30) + '...' 
      : messageContent;
    lastMessageEl.textContent = truncated;
  }
  
  // Update or create unread badge nếu không đang xem chat với người này
  const isCurrentChat = chatService.selectedUserId?.toString() === senderIdStr;
  
  if (!isCurrentChat) {
    const h4 = userItem.querySelector('.user-item-meta h4');
    if (!h4) return;
    
    let badge = h4.querySelector('.unread-badge');
    
    if (badge) {
      // Tăng số count
      const currentCount = parseInt(badge.textContent) || 0;
      badge.textContent = currentCount + 1;
    } else {
      // Tạo badge mới
      badge = document.createElement('span');
      badge.classList.add('unread-badge');
      badge.textContent = '1';
      h4.appendChild(badge);
    }
  }
  
  // Di chuyển user lên đầu danh sách
  const parentList = userItem.parentElement;
  if (parentList) {
    parentList.insertBefore(userItem, parentList.firstChild);
  }
}

// Update last message in user item (for sent messages)
function updateUserItemLastMessage(userId, messageContent, isMine = false) {
  if (!userId) return;
  
  const userIdStr = userId.toString();
  const userItem = document.querySelector(`.user-item[data-user-id="${userIdStr}"]`);
  
  if (!userItem) return;
  
  // Update last message
  const lastMessageEl = userItem.querySelector('.last-message');
  if (lastMessageEl) {
    const prefix = isMine ? 'Bạn: ' : '';
    const truncated = messageContent.length > 30 
      ? messageContent.slice(0, 30) + '...' 
      : messageContent;
    lastMessageEl.textContent = prefix + truncated;
  }
  
  // Di chuyển user lên đầu danh sách
  const parentList = userItem.parentElement;
  if (parentList) {
    parentList.insertBefore(userItem, parentList.firstChild);
  }
}

socket.on('noti-online', (data) => {
  updateUserOnlineStatus(data?.id, true);
});

socket.on('noti-offline', (data) => {
  updateUserOnlineStatus(data?.id, false);
});

socket.on('noti-onlineList-toMe', (list) => {
  if (!Array.isArray(list)) return;
  list.forEach((id) => updateUserOnlineStatus(id, true));
});

socket.on('typing-start', (data) => {
  const senderId = data?.senderId;
  const senderName = data?.senderName;
  if (!senderId || !chatService.selectedUserId) return;

  if (senderId.toString() === chatService.selectedUserId.toString()) {
    showTypingIndicator(senderName);
  }
});

socket.on('typing-stop', (data) => {
  const senderId = data?.senderId;
  if (!senderId || !chatService.selectedUserId) return;

  if (senderId.toString() === chatService.selectedUserId.toString()) {
    hideTypingIndicator();
  }
});

socket.on('seen-message', (data) => {
  const viewerId = data?.viewerId; // Người xem tin nhắn (người bên kia)
  const seenAt = data?.seenAt;
  if (!viewerId || !chatService.selectedUserId) return;

  // Nếu người xem là người đang chat với mình, mark messages as seen
  if (viewerId.toString() === chatService.selectedUserId.toString()) {
    // Clear previous delivered statuses
    clearDeliveredStatuses();

    // Mark all sent messages to this user as seen
    const sentMessages = messagesList?.querySelectorAll('.message.sent');
    if (!sentMessages || sentMessages.length === 0) return;

    const lastSent = sentMessages[sentMessages.length - 1];
    const statusDiv = lastSent.querySelector('.message-status');
    if (statusDiv) {
      removeRetryButton(statusDiv);
      applyStatusMetadata(statusDiv, 'Đã xem', 'Xem', seenAt || new Date());
    }
  }
});

// Initialize group UI
const groupUI = initGroupUI(socket);

// Init current user for group UI
await groupUI.initCurrentUser();

// Setup sidebar tabs
const tabBtns = document.querySelectorAll('.tab-btn');
const tabContents = document.querySelectorAll('.tab-content');

tabBtns.forEach(btn => {
  btn.addEventListener('click', () => {
    const tab = btn.dataset.tab;
    
    // Update current active tab
    currentActiveTab = tab;
    
    // Clear search input when switching tabs
    if (searchInput) {
      searchInput.value = '';
    }
    
    // Update active button
    tabBtns.forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    
    // Update active content
    tabContents.forEach(content => content.style.display = 'none');
    document.getElementById(`${tab}-list`).style.display = 'block';
    
    // Load groups if switching to groups tab
    if (tab === 'groups') {
      groupUI.loadGroups();
    } else if (tab === 'users') {
      loadUsers();
    }
  });
});

// Start
init();
