import { groupAPI } from '../../api/group.api.js';
import { authAPI } from '../../api/auth.api.js';
import { userAPI } from '../../api/user.api.js';
import { escapeHtml } from '../../utils/escapeHtml.js';

// Format time as HH:mm (24-hour format)
function formatTime(date) {
  const dateObj = date instanceof Date ? date : new Date(date);
  if (Number.isNaN(dateObj.getTime())) return '--:--';
  const hours = String(dateObj.getHours()).padStart(2, '0');
  const minutes = String(dateObj.getMinutes()).padStart(2, '0');
  return `${hours}:${minutes}`;
}

export const initGroupUI = (socket) => {
  let currentGroupId = null;
  let currentUser = null;
  let socketHandlersSetup = false; // Track if handlers already setup

  // Get elements
  const groupsList = document.getElementById('groups-list');
  const createGroupModal = document.getElementById('create-group-modal');
  const createGroupForm = document.getElementById('create-group-form');
  const createGroupOverlay = document.getElementById('create-group-modal-overlay');
  const editGroupModal = document.getElementById('edit-group-modal');
  const editGroupForm = document.getElementById('edit-group-form');
  const editGroupOverlay = document.getElementById('edit-group-modal-overlay');
  const editGroupBtn = document.getElementById('edit-group-info-btn');
  const noChatSelected = document.getElementById('no-chat-selected');
  const chatMessagesContainer = document.getElementById('chat-messages-container');

  if (!groupsList) {
    console.error('[GroupUI] groups-list element not found');
    return;
  }

  // Current group info
  let currentGroupInfo = null;

  // Fetch current user
  async function initCurrentUser() {
    try {
      const response = await authAPI.getMe();
      currentUser = response.data?.user || response.data;
      return currentUser;
    } catch (error) {
      console.error('[GroupUI] Error fetching current user:', error);
      return null;
    }
  }

  // Setup modal
  setupGroupModal();
  setupEditGroupModal();
  
  let selectedMemberIds = [];
  let editGroupAvatarBase64 = null; // Store avatar for edit

  function setupEditGroupModal() {
    if (editGroupForm) {
      editGroupForm.addEventListener('submit', handleEditGroup);
    }

    if (editGroupOverlay) {
      editGroupOverlay.addEventListener('click', (e) => {
        if (e.target === editGroupOverlay) {
          closeEditModal();
        }
      });
    }

    // Attach close button
    const closeBtn = editGroupModal?.querySelector('.modal-close-btn');
    if (closeBtn) {
      closeBtn.addEventListener('click', closeEditModal);
    }

    // Edit button in header
    if (editGroupBtn) {
      editGroupBtn.addEventListener('click', openEditModal);
    }

    // Avatar upload handler
    const avatarPreview = document.getElementById('edit-group-avatar-preview');
    const avatarInput = document.getElementById('edit-group-avatar-input');

    if (avatarPreview && avatarInput) {
      avatarPreview.addEventListener('click', () => {
        avatarInput.click();
      });

      avatarInput.addEventListener('change', (e) => {
        const file = e.target.files[0];
        if (file) {
          const reader = new FileReader();
          reader.onload = (event) => {
            editGroupAvatarBase64 = event.target.result;
            avatarPreview.style.backgroundImage = `url(${editGroupAvatarBase64})`;
            avatarPreview.style.backgroundSize = 'cover';
            avatarPreview.style.backgroundPosition = 'center';
            avatarPreview.textContent = '';
          };
          reader.readAsDataURL(file);
        }
      });
    }
  }

  function setupGroupModal() {
    if (createGroupForm) {
      createGroupForm.addEventListener('submit', handleCreateGroup);
    }

    if (createGroupOverlay) {
      createGroupOverlay.addEventListener('click', (e) => {
        if (e.target === createGroupOverlay) {
          closeModal();
        }
      });
    }
    
    // Attach close button
    const closeBtn = createGroupModal?.querySelector('.modal-close-btn');
    if (closeBtn) {
      closeBtn.addEventListener('click', closeModal);
    }
    
    // Setup search members
    setupMemberSearch();
  }

  function setupMemberSearch() {
    const searchMemberInput = document.getElementById('search-member-input');
    const searchResultsList = document.getElementById('search-results-list');
    const selectedMembersList = document.getElementById('selected-members-list');
    
    if (!searchMemberInput || !searchResultsList || !selectedMembersList) {
      console.warn('[GroupUI] Search member elements not found');
      return;
    }
    
    let searchTimeout = null;
    
    searchMemberInput.addEventListener('input', async (e) => {
      const keyword = e.target.value.trim();
      
      clearTimeout(searchTimeout);
      
      if (!keyword) {
        searchResultsList.innerHTML = '';
        return;
      }
      
      searchTimeout = setTimeout(async () => {
        try {
          const response = await userAPI.searchs(keyword);
          const users = response.data?.users || [];
          
          if (users.length === 0) {
            searchResultsList.innerHTML = '<div class="search-result empty">Không tìm thấy người dùng</div>';
            return;
          }
          
          searchResultsList.innerHTML = users.map(user => `
            <div class="search-result-item" data-user-id="${user._id}">
              <div class="result-avatar">${user.avatar ? `<img src="${user.avatar}">` : user.fullName?.charAt(0).toUpperCase() || '?'}</div>
              <div class="result-info">
                <p class="result-name">${escapeHtml(user.fullName || user.username)}</p>
                <p class="result-username">@${escapeHtml(user.username)}</p>
              </div>
              <button type="button" class="btn-add-member" data-user-id="${user._id}" data-user-name="${escapeHtml(user.fullName || user.username)}">Thêm</button>
            </div>
          `).join('');
          
          // Add event listeners to add buttons
          document.querySelectorAll('.btn-add-member').forEach(btn => {
            btn.addEventListener('click', () => {
              const userId = btn.dataset.userId;
              const userName = btn.dataset.userName;
              addMember(userId, userName);
              btn.disabled = true;
              btn.textContent = 'Đã thêm';
            });
          });
        } catch (error) {
          console.error('[GroupUI] Search error:', error);
          searchResultsList.innerHTML = '<div class="search-result error">Lỗi tìm kiếm</div>';
        }
      }, 500);
    });
  }
  
  function addMember(userId, userName) {
    if (selectedMemberIds.includes(userId)) {
      return;
    }
    
    selectedMemberIds.push(userId);
    
    const selectedMembersList = document.getElementById('selected-members-list');
    if (selectedMembersList) {
      const memberEl = document.createElement('div');
      memberEl.className = 'selected-member';
      memberEl.innerHTML = `
        <span>${escapeHtml(userName)}</span>
        <button type="button" class="btn-remove-member" onclick="this.parentElement.remove()">×</button>
      `;
      memberEl.dataset.userId = userId;
      selectedMembersList.appendChild(memberEl);
      
      // Update hidden input
      updateMembersInput();
    }
  }
  
  function updateMembersInput() {
    const membersInput = document.getElementById('group-members-input');
    if (membersInput) {
      membersInput.value = selectedMemberIds.join(', ');
    }
  }

  function openModal() {
    if (createGroupModal && createGroupOverlay) {
      createGroupModal.classList.add('show');
      createGroupOverlay.classList.add('show');
      selectedMemberIds = [];
      document.getElementById('selected-members-list').innerHTML = '';
      document.getElementById('search-member-input').value = '';
      document.getElementById('search-results-list').innerHTML = '';
    }
  }

  function closeModal() {
    if (createGroupModal && createGroupOverlay) {
      createGroupModal.classList.remove('show');
      createGroupOverlay.classList.remove('show');
    }
  }

  function openEditModal() {
    if (!currentGroupInfo) {
      alert('Vui lòng chọn nhóm trước');
      return;
    }

    const editNameInput = document.getElementById('edit-group-name-input');
    const editDescInput = document.getElementById('edit-group-desc-input');
    const avatarPreview = document.getElementById('edit-group-avatar-preview');
    const avatarInput = document.getElementById('edit-group-avatar-input');

    if (editNameInput) editNameInput.value = currentGroupInfo.name || '';
    if (editDescInput) editDescInput.value = currentGroupInfo.description || '';

    // Reset avatar
    editGroupAvatarBase64 = null;
    if (avatarInput) avatarInput.value = '';

    // Show current avatar or initial
    if (avatarPreview) {
      const groupName = currentGroupInfo.name || 'Group';
      if (currentGroupInfo.avatar) {
        avatarPreview.style.backgroundImage = `url(${currentGroupInfo.avatar})`;
        avatarPreview.style.backgroundSize = 'cover';
        avatarPreview.style.backgroundPosition = 'center';
        avatarPreview.textContent = '';
      } else {
        avatarPreview.style.backgroundImage = 'none';
        avatarPreview.textContent = groupName.charAt(0).toUpperCase();
      }
    }

    if (editGroupModal && editGroupOverlay) {
      editGroupModal.classList.add('show');
      editGroupOverlay.classList.add('show');
    }
  }

  function closeEditModal() {
    if (editGroupModal && editGroupOverlay) {
      editGroupModal.classList.remove('show');
      editGroupOverlay.classList.remove('show');
    }
  }

  // Convert base64 to File object
  function dataURLtoFile(dataurl, filename) {
    const arr = dataurl.split(',');
    const mime = arr[0].match(/:(.*?);/)[1];
    const bstr = atob(arr[1]);
    let n = bstr.length;
    const u8arr = new Uint8Array(n);
    while (n--) {
      u8arr[n] = bstr.charCodeAt(n);
    }
    return new File([u8arr], filename, { type: mime });
  }

  async function handleEditGroup(e) {
    e.preventDefault();

    if (!currentGroupId) {
      alert('Không có nhóm được chọn');
      return;
    }

    const nameInput = document.getElementById('edit-group-name-input');
    const descInput = document.getElementById('edit-group-desc-input');

    const name = nameInput?.value.trim();
    const description = descInput?.value.trim();

    if (!name) {
      alert('Tên nhóm không được để trống');
      return;
    }

    try {
      const updateData = { name, description };
      
      // Step 1: Upload avatar if selected and get URL
      if (editGroupAvatarBase64) {
        const file = dataURLtoFile(editGroupAvatarBase64, 'group-avatar.jpg');
        const uploadResponse = await groupAPI.uploadGroupAvatar(file);
        
        if (uploadResponse.data?.url) {
          updateData.avatar = uploadResponse.data.url;
        }
      }

      // Step 2: Update group with new data
      const response = await groupAPI.updateGroup(currentGroupId, updateData);
      
      if (response.data?.message) {
        closeEditModal();

        // Update current group info
        if (response.data?.group) {
          currentGroupInfo = response.data.group;
        }

        // Reload groups list
        await loadGroups();

        // Update header
        const chatNameText = document.querySelector('#chat-with-name .chat-name-text');
        if (chatNameText) {
          chatNameText.textContent = escapeHtml(name);
        }
      }
    } catch (error) {
      console.error('[GroupUI] Edit group error:', error);
      const errorMsg = error.response?.data?.message || error.message || 'Lỗi sửa nhóm';
      alert('Lỗi: ' + errorMsg);
    }
  }

  // Store all groups for search
  let allGroups = [];

  // Render groups list
  function renderGroupsList(groups) {
    if (groups.length === 0) {
      groupsList.innerHTML = `
        <button id="create-group-btn" class="btn btn-primary btn-create-group">+ Tạo nhóm</button>
        <div class="empty-state">Chưa tham gia nhóm nào</div>
      `;
      attachCreateGroupBtn();
      return;
    }

    const groupsHTML = groups.map(group => {
      // Format last message
      let lastMessageText = 'Chưa có tin nhắn';
      if (group.lastMessage && group.lastMessage.content) {
        const prefix = group.lastMessage.isMine ? 'Bạn: ' : `${group.lastMessage.senderName}: `;
        lastMessageText = prefix + group.lastMessage.content;
      }
      
      return `
      <div class="group-item" data-group-id="${group._id}">
        <div class="group-avatar"${group.avatar ? ` style="background-image: url(${group.avatar}); background-size: cover; background-position: center;"` : ''}>${group.avatar ? '' : group.name.charAt(0).toUpperCase()}</div>
        <div class="group-info">
          <p class="group-name">${escapeHtml(group.name)}</p>
          <p class="group-last-message">${escapeHtml(lastMessageText)}</p>
          <span class="group-unread">${group.unreadCount || 0} chưa đọc</span>
        </div>
      </div>
    `;
    }).join('');

    groupsList.innerHTML = `
      <button id="create-group-btn" class="btn btn-primary btn-create-group">+ Tạo nhóm</button>
      ${groupsHTML}
    `;
    
    attachCreateGroupBtn();

    // Add click handlers for group items
    document.querySelectorAll('.group-item').forEach(item => {
      item.addEventListener('click', () => {
        const groupId = item.dataset.groupId;
        selectGroup(groupId);
      });
    });
  }

  // Load groups
  async function loadGroups() {
    try {
      groupsList.innerHTML = '<div class="loading">Đang tải nhóm...</div>';
      const response = await groupAPI.getGroups();
      
      allGroups = response.data?.groups || response.data || [];

      // Clear search input
      const searchInput = document.getElementById('search-input');
      if (searchInput) {
        searchInput.value = '';
      }

      // Join all group rooms to receive notifications
      if (socket && allGroups.length > 0) {
        allGroups.forEach(group => {
          socket.emit('join-group', { groupId: group._id });
        });
      }

      if (allGroups.length === 0) {
        groupsList.innerHTML = `
          <button id="create-group-btn" class="btn btn-primary btn-create-group">+ Tạo nhóm</button>
          <div class="empty-state">Chưa tham gia nhóm nào</div>
        `;
        attachCreateGroupBtn();
        return;
      }

      renderGroupsList(allGroups);

    } catch (error) {
      console.error('[GroupUI] Error loading groups:', error);
      const errorMsg = error.response?.data?.message || error.message || 'Lỗi tải nhóm';
      groupsList.innerHTML = `
        <button id="create-group-btn" class="btn btn-primary btn-create-group">+ Tạo nhóm</button>
        <div class="error-message">${escapeHtml(errorMsg)}</div>
      `;
      attachCreateGroupBtn();
    }
  }

  function attachCreateGroupBtn() {
    const btn = document.getElementById('create-group-btn');
    if (btn) {
      btn.removeEventListener('click', openModal);
      btn.addEventListener('click', () => {
        openModal();
      });
    }
  }

  async function selectGroup(groupId) {
    currentGroupId = groupId;
    
    // Join group room on socket
    if (socket) {
      socket.emit('join-group', groupId);
    }

    // Update UI
    document.querySelectorAll('.group-item').forEach(item => {
      item.classList.remove('active');
    });
    document.querySelector(`[data-group-id="${groupId}"]`)?.classList.add('active');

    // Show chat container and hide menu
    if (noChatSelected) noChatSelected.style.display = 'none';
    if (chatMessagesContainer) chatMessagesContainer.style.display = 'flex';
    
    const groupMenuPanel = document.getElementById('group-menu-panel');
    const toggleMenuBtn = document.getElementById('toggle-group-menu-btn');
    if (groupMenuPanel) groupMenuPanel.style.display = 'none';
    if (toggleMenuBtn) toggleMenuBtn.style.display = 'block';

    // Load group info
    await loadGroupInfo(groupId);
    await loadGroupMessages(groupId);
    
    // Update menu after loading group info
    updateGroupMenu();
  }

  async function loadGroupInfo(groupId) {
    const chatNameText = document.querySelector('#chat-with-name .chat-name-text');
    const chatAvatar = document.getElementById('chat-avatar');
    
    try {
      const response = await groupAPI.getInfoGroup(groupId);
      const group = response.data?.group || response.data || {};
      
      // Store current group info
      currentGroupInfo = group;

      // Show edit button only if user is admin
      const isAdmin = group.members?.some(m => 
        m.userId._id?.toString() === currentUser?._id?.toString() && m.role === 'admin'
      );

      if (editGroupBtn) {
        editGroupBtn.style.display = isAdmin ? 'block' : 'none';
      }
      
      if (chatNameText) {
        chatNameText.textContent = escapeHtml(group.name || 'Nhóm');
      }

      // Display group avatar
      if (chatAvatar) {
        if (group.avatar) {
          chatAvatar.style.backgroundImage = `url(${group.avatar})`;
          chatAvatar.textContent = '';
        } else {
          chatAvatar.style.backgroundImage = 'none';
          chatAvatar.textContent = group.name?.charAt(0).toUpperCase() || 'G';
        }
      }
      
      window.currentGroup = group;
    } catch (error) {
      console.error('[GroupUI] Error loading group info:', error);
      if (chatNameText) {
        chatNameText.textContent = 'Nhóm';
      }
    }
  }

  async function loadGroupMessages(groupId) {
    const messagesList = document.getElementById('messages-list');
    if (!messagesList) {
      console.error('[GroupUI] messages-list not found');
      return;
    }

    try {
      messagesList.innerHTML = '<div class="loading">Đang tải tin nhắn...</div>';
      const response = await groupAPI.getGroupMessages(groupId);
      
      const messages = response.data?.messages || [];

      if (messages.length === 0) {
        messagesList.innerHTML = '<div class="empty-state">Chưa có tin nhắn</div>';
        return;
      }

      const currentUserId = currentUser?._id || currentUser?.id;
      
      messagesList.innerHTML = messages.map((msg, index) => {
        // Get senderId safely
        const senderId = typeof msg.senderId === 'object' 
          ? msg.senderId._id || msg.senderId.id 
          : msg.senderId;
        
        // Compare IDs as strings
        const isSent = senderId?.toString() === currentUserId?.toString();
        
        // Emit seen for received messages
        if (!isSent && msg._id && socket) {
          socket.emit('seen-group-message', {
            messageId: msg._id,
            groupId: groupId
          });
        }
        
        return `
        <div class="message ${isSent ? 'sent' : 'received'}" data-message-id="${msg._id}">
          <div class="message-avatar">${msg.senderId?.avatar ? `<img src="${msg.senderId.avatar}">` : msg.senderId?.fullName?.charAt(0).toUpperCase() || '?'}</div>
          <div class="message-content">
            <p class="message-sender">${escapeHtml(msg.senderId?.fullName || msg.senderId?.username || 'Unknown')}</p>
            <p class="message-text">${escapeHtml(msg.content)}</p>
            <span class="message-time">${formatTime(msg.createdAt)}</span>
            ${msg.seenBy && msg.seenBy.length > 0 ? `<span class="message-seen-status">Đã xem (${msg.seenBy.length})</span>` : ''}
          </div>
        </div>
      `;
      }).join('');

      messagesList.scrollTop = messagesList.scrollHeight;
    } catch (error) {
      console.error('[GroupUI] Error loading messages:', error);
      const errorMsg = error.response?.data?.message || error.message || 'Lỗi tải tin nhắn';
      messagesList.innerHTML = `<div class="error-message">${escapeHtml(errorMsg)}</div>`;
    }
  }

  // Send group message via Socket.IO
  function sendGroupMessage(content) {
    if (!socket) {
      console.error('[GroupUI] Socket not available');
      return;
    }

    if (!currentGroupId) {
      console.error('[GroupUI] No group selected');
      return;
    }

    if (!content.trim()) {
      return;
    }

    socket.emit('send-group-message', {
      groupId: currentGroupId,
      content: content.trim(),
      replyTo: null,
      fileUrl: null
    }, (response) => {
      if (response?.success) {
      } else {
        console.error('[GroupUI] Failed to send message:', response?.message);
        alert('Lỗi: ' + (response?.message || 'Không thể gửi tin nhắn'));
      }
    });
  }

  // Setup Socket.IO handlers for group messages (once at init)
  function setupGroupSocketHandlers() {
    if (!socket) {
      console.warn('[GroupUI] Socket not available for handlers');
      return;
    }

    // Receive group message
    socket.on('receive-group-message', (data) => {
      // Reload groups to update lastMessage and unreadCount
      loadGroups();

      // Only display message if it's from the current active group
      if (data.groupId !== currentGroupId) {
        return;
      }

      const messagesList = document.getElementById('messages-list');
      if (!messagesList) return;

      const senderId = typeof data.senderId === 'object' 
        ? data.senderId._id || data.senderId.id 
        : data.senderId;
      
      const currentUserId = currentUser?._id || currentUser?.id;
      const isSent = senderId?.toString() === currentUserId?.toString();

      const senderName = typeof data.senderId === 'object'
        ? (data.senderId.fullName || data.senderId.username || 'Unknown')
        : 'Unknown';

      const senderAvatar = typeof data.senderId === 'object'
        ? (data.senderId.avatar || senderName.charAt(0).toUpperCase())
        : '?';

      const messageHTML = `
        <div class="message ${isSent ? 'sent' : 'received'}">
          <div class="message-avatar">${typeof data.senderId === 'object' && data.senderId.avatar ? `<img src="${data.senderId.avatar}">` : senderAvatar}</div>
          <div class="message-content">
            <p class="message-sender">${escapeHtml(senderName)}</p>
            <p class="message-text">${escapeHtml(data.content)}</p>
            <span class="message-time">${formatTime(data.createdAt)}</span>
          </div>
        </div>
      `;

      const messageDiv = document.createElement('div');
      messageDiv.innerHTML = messageHTML;
      messagesList.appendChild(messageDiv.firstElementChild);
      messagesList.scrollTop = messagesList.scrollHeight;
    });

    // User seen message
    socket.on('user-seen-message', (data) => {      
      // Update UI to show seen status
      const messageElement = document.querySelector(`[data-message-id="${data.messageId}"]`);
      if (messageElement) {
        const seenStatus = messageElement.querySelector('.message-seen-status');
        if (seenStatus) {
          seenStatus.textContent = `Đã xem (${data.seenBy?.length || 0})`;
        }
      }
    });

    // Group typing start
    socket.on('group-typing-start', (data) => {      
      if (data.senderId === (currentUser?._id || currentUser?.id)?.toString()) {
        return; // Don't show your own typing indicator
      }

      const typingIndicator = document.getElementById('typing-indicator');
      if (typingIndicator) {
        const typingText = typingIndicator.querySelector('.typing-text');
        if (typingText) {
          typingText.textContent = `${data.senderName} đang gõ...`;
          typingIndicator.style.display = 'block';
        }
      }
    });

    // Group typing stop
    socket.on('group-typing-stop', (data) => {      
      const typingIndicator = document.getElementById('typing-indicator');
      if (typingIndicator) {
        typingIndicator.style.display = 'none';
      }
    });

    // Reload groups when new group created by other members
    socket.on('reload-groups', () => {
      loadGroups();
    });
  }

  async function handleCreateGroup(e) {
    e.preventDefault();
    
    const nameInput = document.getElementById('group-name-input');
    const descInput = document.getElementById('group-desc-input');
    const membersInput = document.getElementById('group-members-input');

    const name = nameInput?.value.trim();
    const description = descInput?.value.trim();
    const memberIds = membersInput?.value.split(',').map(id => id.trim()).filter(id => id);

    if (!name || (memberIds?.length || 0) < 2) {
      alert('Nhập tên nhóm và ít nhất 2 thành viên');
      return;
    }

    try {
      const response = await groupAPI.createGroup({ name, description, members: memberIds });
      
      // Clear form
      createGroupForm?.reset();
      closeModal();

      // Reload groups
      await loadGroups();

      // Notify members to reload groups
      if (socket && response.data?.group) {
        socket.emit('group-created', {
          groupId: response.data.group._id,
          members: memberIds
        });
      }
    } catch (error) {
      console.error('[GroupUI] Create group error:', error);
      const errorMsg = error.response?.data?.message || error.message || 'Lỗi tạo nhóm';
      alert('Lỗi tạo nhóm: ' + errorMsg);
    }
  }

  // Group Menu Functions
  function setupGroupMenu() {
    const toggleMenuBtn = document.getElementById('toggle-group-menu-btn');
    const closeMenuBtn = document.getElementById('close-group-menu-btn');
    const groupMenuPanel = document.getElementById('group-menu-panel');
    const editGroupMenuBtn = document.getElementById('btn-edit-group-in-menu');
    const addMemberBtn = document.getElementById('btn-add-member');
    const changeRoleBtn = document.getElementById('btn-change-role');
    const deleteGroupBtn = document.getElementById('btn-delete-group');
    const addMemberModal = document.getElementById('add-member-modal');
    const changeRoleModal = document.getElementById('change-role-modal');

    // Toggle menu
    if (toggleMenuBtn) {
      toggleMenuBtn.addEventListener('click', () => {
        groupMenuPanel.style.display = groupMenuPanel.style.display === 'none' ? 'flex' : 'none';
      });
    }

    // Close menu
    if (closeMenuBtn) {
      closeMenuBtn.addEventListener('click', () => {
        groupMenuPanel.style.display = 'none';
      });
    }

    // Edit group (in menu)
    if (editGroupMenuBtn) {
      editGroupMenuBtn.addEventListener('click', () => {
        groupMenuPanel.style.display = 'none';
        openEditModal();
      });
    }

    // Add member
    if (addMemberBtn) {
      addMemberBtn.addEventListener('click', () => {
        groupMenuPanel.style.display = 'none';
        openAddMemberModal();
      });
    }

    // Change role
    if (changeRoleBtn) {
      changeRoleBtn.addEventListener('click', () => {
        groupMenuPanel.style.display = 'none';
        openChangeRoleModal();
      });
    }

    // Delete group
    if (deleteGroupBtn) {
      deleteGroupBtn.addEventListener('click', () => {
        const isOwner = currentGroupInfo.owner._id?.toString() === currentUser?._id?.toString();
        const message = isOwner 
          ? 'Bạn chắc chắn muốn xóa nhóm này? Hành động này không thể hoàn tác.'
          : 'Bạn chắc chắn muốn rời nhóm này?';
        if (confirm(message)) {
          handleDeleteGroup();
        }
      });
    }

    // Add member modal setup
    const addMemberForm = document.getElementById('add-member-form');
    const addMemberSearchInput = document.getElementById('add-member-search-input');
    const addMemberModalOverlay = document.getElementById('add-member-modal-overlay');

    if (addMemberForm) {
      addMemberForm.addEventListener('submit', handleAddMembers);
    }

    if (addMemberModalOverlay) {
      addMemberModalOverlay.addEventListener('click', (e) => {
        if (e.target === addMemberModalOverlay) {
          addMemberModal.style.display = 'none';
        }
      });
    }

    const addMemberCloseBtn = addMemberModal?.querySelector('.modal-close-btn');
    if (addMemberCloseBtn) {
      addMemberCloseBtn.addEventListener('click', () => {
        addMemberModal.style.display = 'none';
      });
    }

    // Member search
    if (addMemberSearchInput) {
      let searchTimeout;
      addMemberSearchInput.addEventListener('input', (e) => {
        clearTimeout(searchTimeout);
        const query = e.target.value.trim();
        
        if (query.length < 2) {
          document.getElementById('add-member-search-results').innerHTML = '';
          return;
        }

        searchTimeout = setTimeout(() => {
          searchUsersForAdd(query);
        }, 300);
      });
    }

    // Change role modal
    const changeRoleModalOverlay = document.getElementById('change-role-modal-overlay');

    if (changeRoleModalOverlay) {
      changeRoleModalOverlay.addEventListener('click', (e) => {
        if (e.target === changeRoleModalOverlay) {
          changeRoleModal.style.display = 'none';
        }
      });
    }

    const changeRoleCloseBtn = changeRoleModal?.querySelector('.modal-close-btn');
    if (changeRoleCloseBtn) {
      changeRoleCloseBtn.addEventListener('click', () => {
        changeRoleModal.style.display = 'none';
      });
    }
  }

  async function searchUsersForAdd(query) {
    try {
      const response = await userAPI.searchs(query);
      const users = response.data?.users || [];
      
      // Filter out existing members
      const existingMemberIds = currentGroupInfo.members.map(m => m.userId._id);
      const filteredUsers = users.filter(u => !existingMemberIds.includes(u._id));

      const resultsDiv = document.getElementById('add-member-search-results');
      if (filteredUsers.length === 0) {
        resultsDiv.innerHTML = '<p style="padding: 10px; color: #999;">Không tìm thấy người dùng</p>';
        return;
      }

      resultsDiv.innerHTML = filteredUsers.map(user => `
        <div class="search-result-item">
          <div class="result-avatar">${user.avatar ? `<img src="${user.avatar}">` : user.fullName?.charAt(0).toUpperCase() || '?'}</div>
          <div class="result-info">
            <p class="result-name">${escapeHtml(user.fullName || user.username)}</p>
            <span class="result-username">@${escapeHtml(user.username)}</span>
          </div>
          <button type="button" class="btn-select-user" data-user-id="${user._id}" data-user-name="${escapeHtml(user.fullName || user.username)}">+</button>
        </div>
      `).join('');

      // Add select handlers
      resultsDiv.querySelectorAll('.btn-select-user').forEach(btn => {
        btn.addEventListener('click', (e) => {
          e.preventDefault();
          const userId = btn.dataset.userId;
          const userName = btn.dataset.userName;
          addSelectedMemberToList(userId, userName);
          btn.disabled = true;
          btn.textContent = '✓';
        });
      });
    } catch (error) {
      console.error('[GroupUI] Search users error:', error);
    }
  }

  let addMemberIds = [];

  function addSelectedMemberToList(userId, userName) {
    if (!addMemberIds.includes(userId)) {
      addMemberIds.push(userId);

      const selectedList = document.getElementById('add-member-selected-list');
      const memberDiv = document.createElement('div');
      memberDiv.className = 'selected-member-item';
      memberDiv.dataset.userId = userId;
      memberDiv.innerHTML = `
        <span>${userName}</span>
        <button type="button" class="btn-remove">✕</button>
      `;

      memberDiv.querySelector('.btn-remove').addEventListener('click', () => {
        addMemberIds = addMemberIds.filter(id => id !== userId);
        memberDiv.remove();
      });

      selectedList.appendChild(memberDiv);
    }
  }

  async function handleAddMembers(e) {
    e.preventDefault();

    if (addMemberIds.length === 0) {
      alert('Vui lòng chọn ít nhất một thành viên');
      return;
    }

    try {
      await groupAPI.addMembers(currentGroupId, addMemberIds);
      
      const addMemberModal = document.getElementById('add-member-modal');
      addMemberModal.style.display = 'none';
      
      // Reset form
      addMemberIds = [];
      document.getElementById('add-member-search-input').value = '';
      document.getElementById('add-member-search-results').innerHTML = '';
      document.getElementById('add-member-selected-list').innerHTML = '';

      // Reload group info
      await loadGroupInfo(currentGroupId);
      loadGroupMembers();

      alert('Đã thêm thành viên thành công');
    } catch (error) {
      console.error('[GroupUI] Add members error:', error);
      alert('Lỗi: ' + (error.response?.data?.message || error.message));
    }
  }

  async function openChangeRoleModal() {
    const changeRoleModal = document.getElementById('change-role-modal');
    const changeRoleList = document.getElementById('change-role-list');

    changeRoleList.innerHTML = '<div class="loading">Đang tải...</div>';
    changeRoleModal.style.display = 'block';

    try {
      if (!currentGroupInfo?.members) {
        await loadGroupInfo(currentGroupId);
      }

      const members = currentGroupInfo.members || [];
      changeRoleList.innerHTML = members.map(m => {
        // Only disable for current user (can't change own role)
        // Owner can change any member including other admins
        const isCurrentUser = m.userId._id?.toString() === currentUser?._id?.toString();
        return `
          <div class="change-role-item">
            <div class="member-info">
              <div class="member-name">${escapeHtml(m.userId.fullName || m.userId.username)}${isCurrentUser ? ' (Bạn)' : ''}</div>
            </div>
            <select class="change-role-select" data-member-id="${m.userId._id}" data-member-name="${escapeHtml(m.userId.fullName || m.userId.username)}" ${isCurrentUser ? 'disabled' : ''}>
              <option value="member" ${m.role === 'member' ? 'selected' : ''}>Thành viên</option>
              <option value="admin" ${m.role === 'admin' ? 'selected' : ''}>Admin</option>
            </select>
          </div>
        `;
      }).join('');

      // Add change listeners to all selects
      changeRoleList.querySelectorAll('.change-role-select').forEach(select => {
        select.addEventListener('change', async (e) => {
          const memberId = select.dataset.memberId;
          const memberName = select.dataset.memberName;
          const newRole = e.target.value;

          if (confirm(`Bạn muốn đổi ${memberName} thành ${newRole === 'admin' ? 'Admin' : 'Thành viên'}?`)) {
            try {
              await groupAPI.changeRole(currentGroupId, memberId, newRole);
              
              // Reload group info
              await loadGroupInfo(currentGroupId);
              loadGroupMembers();
              
              alert('Đã cập nhật quyền thành công');
              
              // Refresh the modal
              openChangeRoleModal();
            } catch (error) {
              console.error('[GroupUI] Change role error:', error);
              alert('Lỗi: ' + (error.response?.data?.message || error.message));
              // Reset to previous value
              const currentRole = currentGroupInfo.members.find(m => m.userId._id?.toString() === memberId)?.role;
              select.value = currentRole;
            }
          } else {
            // Reset to previous value
            const currentRole = currentGroupInfo.members.find(m => m.userId._id?.toString() === memberId)?.role;
            select.value = currentRole;
          }
        });
      });
    } catch (error) {
      console.error('[GroupUI] Load members error:', error);
      changeRoleList.innerHTML = '<p>Lỗi tải dữ liệu</p>';
    }
  }

  async function openAddMemberModal() {
    const addMemberModal = document.getElementById('add-member-modal');
    addMemberIds = [];
    document.getElementById('add-member-search-input').value = '';
    document.getElementById('add-member-search-results').innerHTML = '';
    document.getElementById('add-member-selected-list').innerHTML = '';
    addMemberModal.style.display = 'block';
  }


  async function handleDeleteGroup() {
    try {
      const isOwner = currentGroupInfo.owner._id?.toString() === currentUser?._id?.toString();
      const actionType = isOwner ? 'xóa nhóm' : 'rời nhóm';
      
      // Get members before deletion to notify them
      const memberIds = currentGroupInfo.members?.map(m => m.userId._id || m.userId) || [];
      
      await groupAPI.deleteGroup(currentGroupId);
      
      // Notify all members to reload groups
      if (socket && memberIds.length > 0) {
        socket.emit('group-deleted', { groupId: currentGroupId, members: memberIds });
      }
      
      // Leave group room on socket
      if (socket) {
        socket.emit('leave-group', currentGroupId);
      }
      
      // Hide chat
      const chatMessagesContainer = document.getElementById('chat-messages-container');
      const noChatSelected = document.getElementById('no-chat-selected');
      const groupMenuPanel = document.getElementById('group-menu-panel');
      
      chatMessagesContainer.style.display = 'none';
      noChatSelected.style.display = 'flex';
      if (groupMenuPanel) groupMenuPanel.style.display = 'none';

      // Reload groups
      await loadGroups();

      alert(`Đã ${actionType} thành công`);
    } catch (error) {
      console.error('[GroupUI] Delete group error:', error);
      alert('Lỗi: ' + (error.response?.data?.message || error.message));
    }
  }

  function loadGroupMembers() {
    if (!currentGroupInfo?.members) return;

    const membersList = document.getElementById('group-members-list');
    
    // Check user's role and status
    const userMember = currentGroupInfo.members?.find(m => 
      m.userId._id?.toString() === currentUser?._id?.toString()
    );
    
    const isOwner = currentGroupInfo.owner._id?.toString() === currentUser?._id?.toString();
    const isAdmin = userMember?.role === 'admin';
    
    // Permission: Owner + Admin OR Admin can kick members
    const canKickMembers = (isOwner && isAdmin) || (isAdmin && !isOwner);

    membersList.innerHTML = currentGroupInfo.members.map(m => `
      <div class="member-item">
        <div class="member-avatar">${m.userId.avatar ? `<img src="${m.userId.avatar}">` : m.userId.fullName?.charAt(0).toUpperCase() || '?'}</div>
        <div class="member-info">
          <div class="member-name">${escapeHtml(m.userId.fullName || m.userId.username)}${currentGroupInfo.owner._id?.toString() === m.userId._id?.toString() ? ' 👑' : ''}</div>
          <div class="member-role">${m.role === 'admin' ? '👑 Admin' : '👤 Thành viên'}</div>
        </div>
        ${canKickMembers && m.userId._id?.toString() !== currentUser?._id?.toString() && currentGroupInfo.owner._id?.toString() !== m.userId._id?.toString() ? `
          <div class="member-actions">
            <button class="member-btn danger" data-member-id="${m.userId._id}" onclick="this.dispatchEvent(new CustomEvent('kick-member'))">Kick</button>
          </div>
        ` : ''}
      </div>
    `).join('');

    // Add kick handlers
    membersList.querySelectorAll('[data-member-id]').forEach(btn => {
      btn.addEventListener('kick-member', async () => {
        const memberId = btn.dataset.memberId;
        if (confirm('Bạn chắc chắn muốn xóa thành viên này?')) {
          try {
            await groupAPI.deleteMember(currentGroupId, memberId);
            await loadGroupInfo(currentGroupId);
            loadGroupMembers();
            alert('Đã xóa thành viên');
          } catch (error) {
            alert('Lỗi: ' + error.message);
          }
        }
      });
    });
  }

  function updateGroupMenu() {
    if (!currentGroupInfo) return;

    const memberActionsSection = document.getElementById('member-actions-section');
    const adminActionsSection = document.getElementById('admin-actions-section');
    const editGroupMenuBtn = document.getElementById('btn-edit-group-in-menu');
    const changeRoleBtn = document.getElementById('btn-change-role');
    const deleteGroupBtn = document.getElementById('btn-delete-group');

    // Check user's role and status
    const userMember = currentGroupInfo.members?.find(m => 
      m.userId._id?.toString() === currentUser?._id?.toString()
    );
    
    const isOwner = currentGroupInfo.owner._id?.toString() === currentUser?._id?.toString();
    const isAdmin = userMember?.role === 'admin';
    
    // Permission hierarchy (higher tier has all permissions of lower tier):
    // Tier 0 (Member): Send messages, view info, add members, leave group
    // Tier 1 (Admin non-owner): Tier 0 + Edit group, kick members, leave group
    // Tier 2 (Owner + Admin): Tier 1 + Change roles, delete group
    
    const hasAllPermissions = isOwner && isAdmin;
    const hasAdminPermissions = isAdmin && !isOwner; // Admin without owner
    
    // Show/hide member actions section (visible for all members)
    if (memberActionsSection) {
      memberActionsSection.style.display = 'block';
    }
    
    // Show/hide admin section (contains edit group, change roles)
    if (adminActionsSection) {
      adminActionsSection.style.display = (hasAllPermissions || hasAdminPermissions) ? 'block' : 'none';
    }
    
    // Edit group button in menu (Owner + Admin OR Admin non-owner)
    if (editGroupMenuBtn) {
      editGroupMenuBtn.style.display = (hasAllPermissions || hasAdminPermissions) ? 'block' : 'none';
    }
    
    // Change role button (Owner + Admin only - only owner can change roles)
    if (changeRoleBtn) {
      changeRoleBtn.style.display = hasAllPermissions ? 'block' : 'none';
    }
    
    // Delete/Leave group button (Owner can delete, others can leave)
    if (deleteGroupBtn) {
      if (isOwner) {
        deleteGroupBtn.textContent = '🗑️ Xóa nhóm';
        deleteGroupBtn.classList.add('danger');
      } else {
        deleteGroupBtn.textContent = '👋 Rời nhóm';
        deleteGroupBtn.classList.remove('danger');
      }
    }

    // Update group info display
    document.getElementById('menu-group-name').textContent = escapeHtml(currentGroupInfo.name || '-');
    document.getElementById('menu-group-desc').textContent = escapeHtml(currentGroupInfo.description || 'Chưa có mô tả');
    document.getElementById('menu-group-member-count').textContent = currentGroupInfo.members?.length || 0;

    loadGroupMembers();
  }

  // Call setup after all other setup
  setupGroupMenu();
  setupGroupSocketHandlers(); // Setup socket handlers once at init

  // Export functions
  return {
    initCurrentUser,
    loadGroups,
    selectGroup,
    loadGroupMessages,
    sendGroupMessage,
    getCurrentGroupId: () => currentGroupId,
    setCurrentGroupId: (id) => { currentGroupId = id; },
    getCurrentUser: () => currentUser,
    getAllGroups: () => allGroups,
    renderGroupsDirectly: renderGroupsList,
    openGroupModal: openModal
  };
};
