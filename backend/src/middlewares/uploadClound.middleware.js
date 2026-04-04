const cloudinary = require('cloudinary').v2
const streamifier = require('streamifier')

const cloud = require('../configs/cloud.config');

// Cloudinary
cloud.configCloud(cloudinary);

// End Cloudinary

exports.upload = async (req, res, next) => {
    if (req.file) {
      let streamUpload = (req) => {
        return new Promise((resolve, reject) => {
          let stream = cloudinary.uploader.upload_stream(
            (error, result) => {
              if (result) {
                resolve(result);
              } else {
                reject(error);
              }
            }
          );
          streamifier.createReadStream(req.file.buffer).pipe(stream);
        });
      };

      async function upload(req) {
        let result = await streamUpload(req);
        req.body[req.file.fieldname] = result.secure_url;
      }

      await upload(req);

    }
    next();

  }