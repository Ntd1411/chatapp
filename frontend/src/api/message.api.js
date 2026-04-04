import api from './http.js';

export const messageAPI = {
  getUsers: () => api.get('/messages/users'),

  getMessages: (friendId) => api.get(`/messages/${friendId}`),
};
