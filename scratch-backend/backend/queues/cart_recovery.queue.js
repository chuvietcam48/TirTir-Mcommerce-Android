const { Queue } = require('bullmq');

const connection = {
  host: process.env.REDIS_HOST || '127.0.0.1',
  port: process.env.REDIS_PORT || 6379,
};

const emailRecoveryQueue = new Queue('cart-recovery-email', {
  connection,
  defaultJobOptions: {
    attempts: 3,
    backoff: {
      type: 'exponential',      
      delay: 1000 * 60 * 5, // 5 minutes
    },
    removeOnComplete: { 
      age: 24 * 3600, // 24h
      count: 1000     
    },
    removeOnFail: { 
      age: 7 * 24 * 3600 // 7 days
    }
  }
});

async function cancelRecoveryJobsForCart(cartId) {
  const jobIdsToCancel = [
    `email_1_${cartId}`,
    `email_2_${cartId}`,
    `email_3_${cartId}`
  ];

  for (const jobId of jobIdsToCancel) {
    const job = await emailRecoveryQueue.getJob(jobId);
    if (job) {
      const state = await job.getState();
      if (['waiting', 'delayed'].includes(state)) {
        await job.remove(); 
      }
    }
  }
}

module.exports = {
  emailRecoveryQueue,
  connection,
  cancelRecoveryJobsForCart
};
