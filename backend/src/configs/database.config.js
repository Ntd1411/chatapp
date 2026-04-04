const mongoose = require('mongoose');
const { MongoMemoryServer } = require('mongodb-memory-server');

let memoryServer;

const connectToMemory = async () => {
  memoryServer = await MongoMemoryServer.create();
  const memUri = memoryServer.getUri('realtimechatapp');
  await mongoose.connect(memUri, { serverSelectionTimeoutMS: 5000 });
  console.warn('Connected to in-memory MongoDB (dev fallback)');
};

module.exports.connect = async () => {
  const uri = process.env.MONGO_URI || process.env.MONGODB_URI;

  if (!uri) {
    console.warn('MONGO_URI not set; starting in-memory MongoDB.');
    try {
      await connectToMemory();
      return;
    } catch (error) {
      console.error('Failed to start in-memory MongoDB:', error.message);
      return;
    }
  }

  try {
    await mongoose.connect(uri, { serverSelectionTimeoutMS: 5000 });
    console.log('database connected successfully');
  } catch (error) {
    console.error('Failed to connect to MongoDB:', error.message);
    console.warn('Falling back to in-memory MongoDB.');
    try {
      await connectToMemory();
    } catch (memoryError) {
      console.error('Failed to start in-memory MongoDB:', memoryError.message);
    }
  }
};
