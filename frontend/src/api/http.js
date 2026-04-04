import axios from 'axios';
import { config } from '../config/index.js';

// Tạo axios instance với base URL từ config
const api = axios.create({
  baseURL: config.apiBaseUrl,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Interceptor để thêm token vào mỗi request
api.interceptors.request.use(
  (requestConfig) => {
    const token = localStorage.getItem('token');
    if (token) {
      requestConfig.headers.Authorization = `Bearer ${token}`;
    }
    return requestConfig;
  },
  (error) => Promise.reject(error)
);

// Interceptor để xử lý lỗi 401 (unauthorized)
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Token không hợp lệ hoặc hết hạn
      localStorage.removeItem('token');
      localStorage.removeItem('user');

      // Chỉ reload nếu đang ở trang chat (giữ logic cũ)
      if (document.getElementById('chat-page')?.style.display !== 'none') {
        window.location.reload();
      }
    }
    return Promise.reject(error);
  }
);

export default api;
