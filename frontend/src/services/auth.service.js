import { authAPI } from '../api/auth.api.js';

class AuthService {
  constructor() {
    this.currentUser = null;
  }

  // Lưu token và user vào localStorage
  setAuth(token, user) {
    localStorage.setItem('token', token);
    localStorage.setItem('user', JSON.stringify(user));
    this.currentUser = user;
  }

  // Lấy token từ localStorage
  getToken() {
    return localStorage.getItem('token');
  }

  // Lấy user từ localStorage
  getUser() {
    if (!this.currentUser) {
      const userStr = localStorage.getItem('user');
      if (userStr) {
        this.currentUser = JSON.parse(userStr);
      }
    }
    return this.currentUser;
  }

  // Kiểm tra đã đăng nhập chưa
  isAuthenticated() {
    return !!this.getToken();
  }

  // Xóa token và user
  clearAuth() {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    this.currentUser = null;
  }

  // Cập nhật thông tin user đang lưu
  updateStoredUser(updates = {}) {
    const current = this.getUser() || {};
    const updatedUser = { ...current, ...updates };
    localStorage.setItem('user', JSON.stringify(updatedUser));
    this.currentUser = updatedUser;
    return updatedUser;
  }

  // Đăng nhập
  async login(username, password) {
    try {
      const response = await authAPI.login(username, password);
      const { token } = response.data;

      // Lưu token trước để interceptor có thể sử dụng
      localStorage.setItem('token', token);

      // Lấy thông tin user
      const meResponse = await authAPI.getMe();
      const { user } = meResponse.data;

      // Lưu user và cập nhật currentUser
      localStorage.setItem('user', JSON.stringify(user));
      this.currentUser = user;

      return { success: true, user };
    } catch (error) {
      // Xóa token nếu có lỗi
      localStorage.removeItem('token');
      const message =
        error.response?.data?.message ||
        error.response?.data?.error ||
        'Đăng nhập thất bại';
      return { success: false, message };
    }
  }

  // Đăng ký
  async signup(username, password, fullName, email) {
    try {
      await authAPI.signup(username, password, fullName, email);
      return { success: true, message: 'Đăng ký thành công' };
    } catch (error) {
      const message =
        error.response?.data?.message ||
        error.response?.data?.error ||
        'Đăng ký thất bại';
      return { success: false, message };
    }
  }

  // Đăng xuất
  async logout() {
    try {
      await authAPI.logout();
    } catch (error) {
      console.error('Logout error:', error);
    } finally {
      this.clearAuth();
    }
  }

  // Kiểm tra và lấy thông tin user hiện tại
  async checkAuth() {
    if (!this.isAuthenticated()) {
      return null;
    }

    try {
      const response = await authAPI.getMe();
      const { user } = response.data;
      this.setAuth(this.getToken(), user);
      return user;
    } catch (error) {
      this.clearAuth();
      return null;
    }
  }
}

export default new AuthService();
