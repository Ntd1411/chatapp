const mongoose = require('mongoose');

const publicKeySchema = new mongoose.Schema({
    userId: {
        type: String,
        required: true,
        unique: true,
        index: true,
        ref: "User"
    },
    publicKey: {
        type: String,
        required: true  // Base64 encoded RSA public key
    },
    algorithm: {
        type: String,
        default: 'RSA-2048'
    },
    createdAt: {
        type: Date,
        default: Date.now
    },
    updatedAt: {
        type: Date,
        default: Date.now
    }
});

// Update updatedAt before saving
publicKeySchema.pre('save', function(next) {
    this.updatedAt = Date.now();
    next();
});

module.exports = mongoose.model('PublicKey', publicKeySchema);
