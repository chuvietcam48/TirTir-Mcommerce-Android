const { emailRecoveryQueue } = require('../queues/cart_recovery.queue');

async function scheduleRecoveryEmails(cart, userEmail = null, guestEmail = null) {
  try {
    const emailToUse = cart.user?.email || userEmail || guestEmail;
    if (!emailToUse) {
      console.log(`[ABANDONMENT] Cart ${cart._id} has no email, aborting schedule.`);
      return;
    }

    const t1 = 30 * 60 * 1000; // 30 phút
    const t2 = 24 * 60 * 60 * 1000; // 24 giờ
    const t3 = 72 * 60 * 60 * 1000; // 72 giờ

    const jobData1 = { cartId: cart._id, email: emailToUse, emailSequence: 1, cartLastUpdatedAt: new Date() };
    const jobData2 = { ...jobData1, emailSequence: 2 };
    const jobData3 = { ...jobData1, emailSequence: 3 };

    await emailRecoveryQueue.add('email-seq-1', jobData1, { delay: t1, jobId: `email_1_${cart._id}` });
    await emailRecoveryQueue.add('email-seq-2', jobData2, { delay: t2, jobId: `email_2_${cart._id}` });
    await emailRecoveryQueue.add('email-seq-3', jobData3, { delay: t3, jobId: `email_3_${cart._id}` });

    console.log(`[ABANDONMENT] Scheduled 3 recovery emails for Cart ${cart._id}`);
  } catch (error) {
    console.error(`[ABANDONMENT] Failed to schedule emails for cart ${cart._id}:`, error.message);
  }
}

module.exports = { scheduleRecoveryEmails };
