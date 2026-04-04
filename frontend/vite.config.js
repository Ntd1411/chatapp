import { defineConfig } from 'vite';
// proxy chỉ dùng được trong development (trong production proxy sẽ biến mất)
// trong production cần dùng đầy đủ domain của backend 
export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_URL || 'http://localhost:3000',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  },
  build: {
    rollupOptions: {
      input: {
        main: 'index.html',
        chat: 'chat.html'
      }
    }
  }
});

