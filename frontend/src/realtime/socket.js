import { io } from 'socket.io-client';
import { config } from '../config/index.js';

export function createSocket() {
  return io(config.socketUrl, {
    auth: {
      token: localStorage.getItem('token'),
    },
  });
}
