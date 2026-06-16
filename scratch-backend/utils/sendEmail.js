const nodemailer = require('nodemailer');

/**
 * ===== EMAIL UTILITY =====
 * Send emails using Nodemailer with Mailtrap (development) or production SMTP
 * Includes timeout protection to prevent hanging
 * 
 * @param {Object} options - Email configuration
 * @param {String} options.email - Recipient email address
 * @param {String} options.subject - Email subject line
 * @param {String} options.message - Plain text message
 * @param {String} options.html - HTML formatted message (optional)
 */
const sendEmail = async (options) => {
    // ===== VALIDATION =====
    if (!options.email || !options.subject || !options.message) {
        throw new Error('Email requires: email, subject, and message');
    }

    // ===== CHECK ENVIRONMENT VARIABLES =====
    if (!process.env.EMAIL_HOST || !process.env.EMAIL_USERNAME || !process.env.EMAIL_PASSWORD) {
        throw new Error('Email configuration incomplete. Check EMAIL_HOST, EMAIL_USERNAME, and EMAIL_PASSWORD in .env');
    }

    // ===== CREATE TRANSPORTER =====
    // Configure nodemailer with SMTP settings and timeout protection
    const port = parseInt(process.env.EMAIL_PORT) || 587;
    const transporter = nodemailer.createTransport({
        host: process.env.EMAIL_HOST,
        port: port,
        secure: port === 465, // true for 465, false for other ports (587)
        auth: {
            user: process.env.EMAIL_USERNAME,
            pass: process.env.EMAIL_PASSWORD
        },
        // ===== TIMEOUT PROTECTION =====
        // Prevent email sending from hanging indefinitely
        connectionTimeout: 10000,  // 10 seconds to establish connection
        greetingTimeout: 10000,    // 10 seconds for server greeting
        socketTimeout: 10000,      // 10 seconds for socket inactivity

        // ===== ADDITIONAL SETTINGS =====
        pool: false, // Don't pool connections (simpler for dev)
        maxConnections: 1,
        maxMessages: 1
    });

    // ===== VERIFY TRANSPORTER CONFIGURATION =====
    try {
        await transporter.verify();
        console.log('📧 SMTP server ready to send emails');
    } catch (verifyError) {
        console.error('⚠️ SMTP verification failed:', verifyError.message);
        throw new Error(`Email server unreachable: ${verifyError.message}`);
    }

    // ===== PREPARE MESSAGE =====
    const mailOptions = {
        from: `${process.env.EMAIL_FROM_NAME || 'TirTir Cosmetics'} <${process.env.EMAIL_FROM || process.env.EMAIL_USERNAME}>`,
        to: options.email,
        subject: options.subject,
        text: options.message,
        html: options.html || options.message.replace(/\n/g, '<br>')
    };

    // ===== SEND EMAIL =====
    try {
        const info = await transporter.sendMail(mailOptions);

        console.log('✅ Email sent successfully');
        console.log('   Message ID:', info.messageId);
        console.log('   To:', options.email);
        console.log('   Subject:', options.subject);

        return info;

    } catch (sendError) {
        console.error('❌ Email sending failed:', sendError.message);
        throw new Error(`Failed to send email: ${sendError.message}`);
    }
};

module.exports = sendEmail;
