const mongoose = require('mongoose')

const groupSchema = new mongoose.Schema({
  name: {
    type: String, required: true
  },
  avatar: {
    type: String
  },
  description: {
    type: String
  },
  owner: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true
  },
  members: [{
    userId: { 
      type: mongoose.Schema.Types.ObjectId, 
      ref: 'User' 
    },
    role: { 
      type: String, 
      enum: ['admin', 'member'], 
      default: 'member' 
    },
    joinedAt: { 
      type: Date, 
      default: Date.now 
    }
  }],
  isActive: { 
    type: Boolean, 
    default: true 
  }
}, { timestamps: true });

const Group = mongoose.model("Group", groupSchema)

module.exports = Group