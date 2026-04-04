import api from './http.js';

export const groupAPI = {
  // Tạo nhóm mới
  createGroup: (payload) => api.post('/groups/create', payload),

  // Lấy danh sách nhóm
  getGroups: () => api.get('/groups/getGroups'),

  // Xem thông tin nhóm
  getInfoGroup: (groupId) => api.get(`/groups/${groupId}`),

  // Cập nhật thông tin nhóm
  updateGroup: (groupId, payload) => api.patch(`/groups/update/${groupId}`, payload),

  // Xóa nhóm
  deleteGroup: (groupId) => api.delete(`/groups/delete/${groupId}`),

  // Thêm thành viên
  addMembers: (groupId, memberIds) => api.post(`/groups/${groupId}/addMembers`, { memberIds }),

  // Lấy danh sách thành viên
  getMembers: (groupId) => api.get(`/groups/${groupId}/getMembers`),

  // Xóa thành viên
  deleteMember: (groupId, memberId) => api.delete(`/groups/${groupId}/deleteMembers/${memberId}`),

  // Đổi role thành viên
  changeRole: (groupId, memberId, role) => api.patch(`/groups/${groupId}/changeRole/${memberId}`, { newRole: role }),

  // Lấy tin nhắn trong nhóm
  getGroupMessages: (groupId) => api.get(`/groups/${groupId}/messages`),

  // Upload avatar to get URL
  uploadGroupAvatar: (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return api.post('/messages/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  },
};
