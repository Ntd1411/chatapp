import api from './http.js';

export const authAPI = {
  login: (username, password) => api.post('/auth/login', { username, password }),

  signup: (username, password, fullName, email) =>
    api.post('/auth/signup', { username, password, fullName, email }),

  logout: () => api.post('/auth/logout'),

  getMe: () => api.get('/auth/me'),
};
