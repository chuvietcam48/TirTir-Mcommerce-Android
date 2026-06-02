const { Server } = require('socket.io');
const jwt = require('jsonwebtoken');

let io;

/**
 * Initialise Socket.io on the given http.Server.
 * Call once at startup, after http.createServer(app).
 */
exports.init = (httpServer) => {
    io = new Server(httpServer, {
        cors: {
            origin: process.env.ADMIN_FRONTEND_URL || 'http://localhost:4201',
            credentials: true
        }
    });

    // Middleware — only allow verified admins to connect
    io.use((socket, next) => {
        const token = socket.handshake.auth?.token;
        if (!token) return next(new Error('No token'));
        try {
            const decoded = jwt.verify(token, process.env.JWT_SECRET);
            if (decoded.role !== 'admin') return next(new Error('Not admin'));
            socket.userId = decoded.id;
            next();
        } catch {
            next(new Error('Invalid token'));
        }
    });

    io.on('connection', (socket) => {
        socket.join('admins');
        console.log(`[Socket] Admin connected: ${socket.userId}`);
        socket.on('disconnect', () => {
            console.log(`[Socket] Admin disconnected: ${socket.userId}`);
        });
    });

    return io;
};

/**
 * Emit a real-time event to all connected admin sockets.
 * Safe to call even before init() (noop if socket not ready).
 */
exports.emitToAdmins = (event, payload) => {
    if (!io) return;
    io.to('admins').emit(event, {
        ...payload,
        timestamp: new Date().toISOString()
    });
};
