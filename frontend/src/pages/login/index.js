import authService from '../../services/auth.service.js';

// DOM Elements
const loginForm = document.getElementById('loginForm');
const signupForm = document.getElementById('signupForm');
const tabButtons = document.querySelectorAll('.tab-btn');
const loginFormDiv = document.getElementById('login-form');
const signupFormDiv = document.getElementById('signup-form');
const loginError = document.getElementById('login-error');
const signupError = document.getElementById('signup-error');
const signupSuccess = document.getElementById('signup-success');

// Tab switching
tabButtons.forEach((btn) => {
  btn.addEventListener('click', () => {
    const tab = btn.dataset.tab;

    tabButtons.forEach((b) => b.classList.remove('active'));
    btn.classList.add('active');

    if (tab === 'login') {
      loginFormDiv.classList.add('active');
      signupFormDiv.classList.remove('active');
      loginError.textContent = '';
      signupError.textContent = '';
      signupSuccess.textContent = '';
    } else {
      loginFormDiv.classList.remove('active');
      signupFormDiv.classList.add('active');
      loginError.textContent = '';
      signupError.textContent = '';
      signupSuccess.textContent = '';
    }
  });
});

// Login form handler
loginForm.addEventListener('submit', async (e) => {
  e.preventDefault();
  loginError.textContent = '';

  const username = document.getElementById('login-username').value;
  const password = document.getElementById('login-password').value;

  const submitBtn = loginForm.querySelector('button[type="submit"]');
  submitBtn.disabled = true;
  submitBtn.textContent = 'Đang đăng nhập...';

  const result = await authService.login(username, password);

  submitBtn.disabled = false;
  submitBtn.textContent = 'Đăng nhập';

  if (result.success) {
    // Redirect to chat page
    window.location.href = '/chat.html';
  } else {
    loginError.textContent = result.message;
  }
});

// Signup form handler
signupForm.addEventListener('submit', async (e) => {
  e.preventDefault();
  signupError.textContent = '';
  signupSuccess.textContent = '';

  const username = document.getElementById('signup-username').value;
  const password = document.getElementById('signup-password').value;
  const fullName = document.getElementById('signup-fullname').value;
  const email = document.getElementById('signup-email').value;

  const submitBtn = signupForm.querySelector('button[type="submit"]');
  submitBtn.disabled = true;
  submitBtn.textContent = 'Đang đăng ký...';

  const result = await authService.signup(username, password, fullName, email);

  submitBtn.disabled = false;
  submitBtn.textContent = 'Đăng ký';

  if (result.success) {
    signupSuccess.textContent = result.message;
    signupForm.reset();

    // Chuyển sang tab login sau 1 giây
    setTimeout(() => {
      document.querySelector('.tab-btn[data-tab="login"]').click();
      document.getElementById('login-username').value = username;
    }, 1000);
  } else {
    signupError.textContent = result.message;
  }
});

// Check authentication on page load
async function init() {
  const user = await authService.checkAuth();
  if (user) {
    // If already logged in, redirect to chat page
    window.location.href = '/chat.html';
  }
}

// Initialize
init();
