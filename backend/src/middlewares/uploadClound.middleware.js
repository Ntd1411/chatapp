const cloudinary = require('cloudinary').v2
const streamifier = require('streamifier')

const cloud = require('../configs/cloud.config');

// Cloudinary
cloud.configCloud(cloudinary);

// End Cloudinary

exports.upload = async (req, res, next) => {
    console.log("Uploading")
    try {
      if (!req.file) {
        console.log("No file provided");
        return next();
      }

      console.log("File received:", req.file.fieldname, "Size:", req.file.size);

      let result = await new Promise((resolve, reject) => {
        let stream = cloudinary.uploader.upload_stream(
          (error, result) => {
            if (error) {
              console.error("Cloudinary upload error:", error);
              reject(error);
            } else {
              console.log("Cloudinary upload success:", result.secure_url);
              resolve(result);
            }
          }
        );
        streamifier.createReadStream(req.file.buffer).pipe(stream);
      });

      if (result && result.secure_url) {
        req.body[req.file.fieldname] = result.secure_url;
        console.log("Avatar URL set:", result.secure_url);
      } else {
        throw new Error("Invalid Cloudinary response");
      }

      next();
    } catch (error) {
      console.error("Upload middleware error:", error.message);
      return res.status(500).json({ 
        message: "Upload file failed",
        error: error.message 
      });
    }
  }