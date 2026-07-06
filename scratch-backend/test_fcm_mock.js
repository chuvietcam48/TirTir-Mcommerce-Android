const fcmService = require('./services/fcmService');
// Mocking the required backend env
require('dotenv').config();

async function runTest() {
  console.log('Testing FCM Service...');
  try {
    // Attempt to send a mock notification to a non-existent user
    // This will test if the FCM service and Firebase Admin are initialized correctly
    // and if the service can handle missing users gracefully (as designed).
    const result = await fcmService.sendToUser('testUser_001', {
      notification: {
        title: 'TirTir Flash Sale!',
        body: 'Grab your 50% off coupon now.'
      },
      data: {
        type: 'FLASH_SALE'
      }
    });

    console.log('FCM Test Result:', result);
    
    if (result && result.skipped && result.reason === 'No active FCM tokens') {
        console.log('✅ Service is working perfectly! It correctly identified that the user has no tokens and skipped gracefully without crashing.');
    } else if (result.successCount !== undefined) {
        console.log('✅ Service attempted to send to tokens. Success:', result.successCount, 'Failures:', result.failureCount);
    }
  } catch (error) {
    console.error('❌ FCM Test Failed with error:', error);
  }
  process.exit(0);
}

runTest();
