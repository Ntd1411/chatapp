// Configuration for API and Socket.io URLs
// Sử dụng environment variables hoặc fallback về localhost

const getApiBaseUrl = () => {
  if (import.meta.env.VITE_API_URL) {
    return import.meta.env.VITE_API_URL;
  }

  return 'http://localhost:3000';
};

const getSocketUrl = () => {
  if (import.meta.env.VITE_SOCKET_URL) {
    return import.meta.env.VITE_SOCKET_URL;
  }

  return 'http://localhost:3000';
};

export const config = {
  apiBaseUrl: getApiBaseUrl(),
  socketUrl: getSocketUrl(),
};

if (import.meta.env.NODE_ENV === 'development') {
  console.log('Frontend Config:', config);
}
