const admin = require('firebase-admin');

// Ensure bucket is initialized
const bucket = admin.storage().bucket();

exports.uploadImage = async (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ success: false, message: 'Vui lòng chọn một file ảnh.' });
    }

    // 1. Generate unique file name
    const timestamp = Date.now();
    // Replace spaces and special characters from original name for safe URL
    const safeOriginalName = req.file.originalname.replace(/[^a-zA-Z0-9.]/g, '_');
    const fileName = `uploads/${timestamp}_${safeOriginalName}`;
    const file = bucket.file(fileName);

    // 2. Define file type and metadata
    const blobStream = file.createWriteStream({
      metadata: {
        contentType: req.file.mimetype
      }
    });

    blobStream.on('error', (error) => {
      console.error("Firebase upload error:", error);
      res.status(500).json({ success: false, message: 'Lỗi khi upload lên Firebase: ' + error.message });
    });

    blobStream.on('finish', async () => {
      try {
        // 3. Make file public
        await file.makePublic();

        // 4. Get the public URL
        const publicUrl = `https://storage.googleapis.com/${bucket.name}/${fileName}`;
        
        // Return URL to frontend
        res.status(200).json({ 
            success: true,
            message: 'Upload thành công!',
            imageUrl: publicUrl 
        });
      } catch (err) {
        console.error("Error making file public:", err);
        res.status(500).json({ success: false, message: 'Lỗi khi chia sẻ file public.' });
      }
    });

    // Start upload
    blobStream.end(req.file.buffer);

  } catch (err) {
    console.error("Upload controller error:", err);
    res.status(500).json({ success: false, message: 'Lỗi server nội bộ.' });
  }
};
