const admin = require('firebase-admin');
const path = require('path');

let storageEnabled = false;
let bucket = null;

function initStorage() {
    if (storageEnabled) return;
    try {
        const bucketName = process.env.FIREBASE_STORAGE_BUCKET;
        if (!bucketName) {
            console.warn('Firebase Storage: FIREBASE_STORAGE_BUCKET not set. Image upload to Storage disabled.');
            return;
        }
        // admin.app() throws if not yet initialized
        admin.app();
        bucket = admin.storage().bucket(bucketName);
        storageEnabled = true;
        console.log(`Firebase Storage initialized with bucket: ${bucketName}`);
    } catch (e) {
        console.warn('Firebase Storage: Firebase Admin not ready yet:', e.message);
    }
}

/**
 * Upload a file buffer to Firebase Storage.
 * @param {Buffer} buffer - File buffer from multer memoryStorage
 * @param {string} destPath - Destination path in bucket, e.g. products/PRD-001/thumb.jpg
 * @param {string} mimetype - MIME type of the file
 * @returns {Promise<string>} Public download URL
 */
async function uploadToStorage(buffer, destPath, mimetype = 'image/jpeg') {
    if (!storageEnabled) initStorage();
    if (!storageEnabled || !bucket) {
        throw new Error('Firebase Storage is not configured. Set FIREBASE_STORAGE_BUCKET in .env');
    }

    const file = bucket.file(destPath);
    await file.save(buffer, {
        metadata: { contentType: mimetype },
        resumable: false,
    });

    // Make file publicly readable and return URL
    await file.makePublic();
    const publicUrl = `https://storage.googleapis.com/${bucket.name}/${destPath}`;
    return publicUrl;
}

/**
 * Delete a file from Firebase Storage by its public URL or dest path.
 * Silently fails if file does not exist.
 * @param {string} filePathOrUrl
 */
async function deleteFromStorage(filePathOrUrl) {
    if (!storageEnabled) initStorage();
    if (!storageEnabled || !bucket || !filePathOrUrl) return;

    try {
        let destPath = filePathOrUrl;
        // If full URL, extract path after bucket name
        const urlPrefix = `https://storage.googleapis.com/${bucket.name}/`;
        if (filePathOrUrl.startsWith(urlPrefix)) {
            destPath = filePathOrUrl.slice(urlPrefix.length);
        }
        await bucket.file(destPath).delete({ ignoreNotFound: true });
    } catch (e) {
        console.warn('Firebase Storage: delete failed silently:', e.message);
    }
}

/**
 * Upload multiple files for a product.
 * @param {Express.Multer.File[]} files - Array of multer file objects
 * @param {string} productId - Product_ID used as folder name
 * @returns {Promise<string[]>} Array of public URLs
 */
async function uploadProductFiles(files, productId) {
    const urls = [];
    for (const file of files) {
        const ext = path.extname(file.originalname) || '.jpg';
        const filename = `${Date.now()}-${Math.random().toString(36).slice(2)}${ext}`;
        const destPath = `products/${productId}/${filename}`;
        const url = await uploadToStorage(file.buffer, destPath, file.mimetype);
        urls.push(url);
    }
    return urls;
}

module.exports = { uploadToStorage, deleteFromStorage, uploadProductFiles, initStorage };
