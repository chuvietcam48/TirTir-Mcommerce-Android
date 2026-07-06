'use strict';
const { randomUUID } = require('crypto');
const { getFirestore } = require('../services/firebaseAdmin.service');
const { matchDataset, getDataset } = require('../services/chatDataset.service');
const User = require('../models/user.model');
const Order = require('../models/order.model');

const DEFAULT_RETENTION_HOURS = 24;

// ── Config helper ─────────────────────────────────────────────────────────────

async function fetchConfig(db) {
    if (!db) return null;
    try {
        const doc = await db.collection('chatConfig').doc('default').get();
        return doc.exists ? doc.data() : null;
    } catch (_) { return null; }
}

const CONFIG_DEFAULTS = {
    welcomeMessage: 'Xin chào, {name}! Tôi là {botName} của TIRTIR.\nTôi có thể giúp bạn với:\n• Routine chăm sóc da\n• Tư vấn thành phần sản phẩm\n• Gợi ý sản phẩm phù hợp\n• Hỗ trợ đơn hàng\n\nBạn muốn hỏi về điều gì?',
    hotline: '',
    retentionHours: DEFAULT_RETENTION_HOURS,
    botName: 'TIRTIR Beauty Advisor',
    botAvatarUrl: null,
    quickChips: []
};

// ── Session helpers ───────────────────────────────────────────────────────────

async function getOrCreateSession(db, uid, retentionHours) {
    const sessRef = db.collection('users').doc(uid).collection('chatSessions');
    let snap;
    try {
        snap = await sessRef.where('status', '==', 'active').get();
    } catch (err) {
        console.error('[CHAT SESSION] query error:', err.message);
        return null;
    }

    const now = new Date();

    if (!snap.empty) {
        // Sort by startedAt desc in code — avoids composite index requirement
        const sorted = snap.docs.sort((a, b) => {
            const at = a.data().startedAt?.toMillis ? a.data().startedAt.toMillis() : 0;
            const bt = b.data().startedAt?.toMillis ? b.data().startedAt.toMillis() : 0;
            return bt - at;
        });
        const doc = sorted[0];
        const data = doc.data();
        const expiresAt = data.expiresAt?.toDate ? data.expiresAt.toDate() : null;

        if (!expiresAt || now < expiresAt) {
            // Still active — bump lastMessageAt
            doc.ref.update({ lastMessageAt: now }).catch(() => {});
            return data.sessionId;
        }
        // Expired — close it
        doc.ref.update({ status: 'closed' }).catch(() => {});
    }

    // Create new session
    const sessionId = randomUUID();
    const expiresAt = new Date(now.getTime() + retentionHours * 60 * 60 * 1000);
    try {
        await sessRef.add({ sessionId, status: 'active', startedAt: now, lastMessageAt: now, expiresAt });
    } catch (err) {
        console.error('[CHAT SESSION] create error:', err.message);
        return null;
    }
    return sessionId;
}

// ── Firestore persistence (fire-and-forget) ───────────────────────────────────

function saveMessages(db, uid, sessionId, userText, botText, intentCode, recommendations) {
    if (!db || !uid || !sessionId) return;
    const histRef = db.collection('users').doc(uid).collection('chat_history');
    const now = new Date();
    const batch = db.batch();
    batch.set(histRef.doc(), {
        sender: 'user',
        text: userText,
        sessionId,
        intentCode: intentCode || null,
        productData: null,
        createdAt: now
    });
    batch.set(histRef.doc(), {
        sender: 'bot',
        text: botText,
        sessionId,
        intentCode: intentCode || null,
        productData: recommendations && recommendations.length > 0 ? { recommendations } : null,
        createdAt: new Date(now.getTime() + 1) // +1ms ensures ordering
    });
    batch.commit().catch(err => console.error('[CHAT SAVE]', err.message));
}

// ── SSE response helpers ──────────────────────────────────────────────────────

function startSSE(res) {
    res.writeHead(200, {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        'Connection': 'keep-alive',
        'X-Accel-Buffering': 'no'
    });
}

function streamTextAsSSE(res, text) {
    const CHUNK_SIZE = 30;
    for (let i = 0; i < text.length; i += CHUNK_SIZE) {
        res.write(`event: chunk\ndata: ${JSON.stringify({ text: text.slice(i, i + CHUNK_SIZE) })}\n\n`);
    }
}

function buildActions(hotline) {
    return [
        { type: 'choose_topic',   label: 'Chọn chủ đề câu hỏi' },
        { type: 'contact_staff',  label: 'Nhắn tin với nhân viên' },
        ...(hotline ? [{ type: 'call_hotline', label: `Gọi ${hotline}` }] : [])
    ];
}

// ── GET /api/v1/chat/config ───────────────────────────────────────────────────

exports.getChatConfig = async (req, res) => {
    try {
        const db = getFirestore();
        const config = await fetchConfig(db);
        return res.json({ success: true, data: { ...CONFIG_DEFAULTS, ...(config || {}) } });
    } catch (err) {
        console.error('[CHAT CONFIG]', err.message);
        return res.json({ success: true, data: CONFIG_DEFAULTS });
    }
};

// ── GET /api/v1/chat/suggested-questions ─────────────────────────────────────
// Returns ONLY UI-safe fields — no answerText, aliases, or keywords.

exports.getSuggestedQuestions = async (req, res) => {
    try {
        const dataset = await getDataset();
        const safe = dataset.map(item => ({
            id: item.id,
            intentCode: item.intentCode,
            category: item.category,
            question: item.question,
            priority: item.priority || 99
        }));
        return res.json({ success: true, data: safe });
    } catch (err) {
        console.error('[CHAT SUGGESTED]', err.message);
        return res.json({ success: true, data: [] });
    }
};

// ── GET /api/v1/chat/history ──────────────────────────────────────────────────
// Backend-authoritative: only messages within the active session (≤ retentionHours).

exports.getChatHistory = async (req, res) => {
    const db = getFirestore();
    const uid = req.user?.firebaseUid;

    if (!uid || !db) {
        return res.json({ success: true, data: [], sessionExpired: false });
    }

    try {
        const sessRef = db.collection('users').doc(uid).collection('chatSessions');
        let snap;
        try {
            snap = await sessRef.where('status', '==', 'active').get();
        } catch (_) {
            return res.json({ success: true, data: [], sessionExpired: false });
        }

        if (snap.empty) {
            return res.json({ success: true, data: [], sessionExpired: true });
        }

        // Most recent active session
        const sorted = snap.docs.sort((a, b) => {
            const at = a.data().startedAt?.toMillis ? a.data().startedAt.toMillis() : 0;
            const bt = b.data().startedAt?.toMillis ? b.data().startedAt.toMillis() : 0;
            return bt - at;
        });
        const sessionDoc = sorted[0];
        const session = sessionDoc.data();
        const expiresAt = session.expiresAt?.toDate ? session.expiresAt.toDate() : null;

        if (expiresAt && new Date() > expiresAt) {
            sessionDoc.ref.update({ status: 'closed' }).catch(() => {});
            return res.json({ success: true, data: [], sessionExpired: true });
        }

        // Fetch messages for this session
        let histSnap;
        try {
            histSnap = await db.collection('users').doc(uid)
                .collection('chat_history')
                .where('sessionId', '==', session.sessionId)
                .get();
        } catch (_) {
            return res.json({ success: true, data: [], sessionExpired: false });
        }

        const messages = histSnap.docs
            .map(doc => {
                const d = doc.data();
                return {
                    _sort: d.createdAt?.toMillis ? d.createdAt.toMillis() : 0,
                    sender: d.sender,
                    text: d.text,
                    createdAt: d.createdAt?.toDate ? d.createdAt.toDate().toISOString() : '',
                    sessionId: d.sessionId || null,
                    productData: d.productData || null
                };
            })
            .sort((a, b) => a._sort - b._sort)
            .map(({ _sort, ...rest }) => rest);

        return res.json({ success: true, data: messages, sessionExpired: false });
    } catch (err) {
        console.error('[CHAT HISTORY]', err.message);
        return res.json({ success: true, data: [], sessionExpired: false });
    }
};

// ── POST /api/v1/chat ─────────────────────────────────────────────────────────
// Dataset-only answering: alias → keyword → semantic classifier → OOD fallback.
// Gemini is ONLY used as an intent classifier — it never generates free answers.

exports.chatWithBot = async (req, res) => {
    const { message, selectedQuestionId, intentCode: reqIntentCode } = req.body;

    if (!message && !selectedQuestionId && !reqIntentCode) {
        return res.status(400).json({ success: false, message: 'message is required' });
    }

    const isSSE = req.headers.accept === 'text/event-stream';
    const db = getFirestore();
    const uid = req.user?.firebaseUid;

    // Fetch config for hotline and retention
    const config = await fetchConfig(db);
    const retentionHours = config?.retentionHours || DEFAULT_RETENTION_HOURS;
    const hotline = config?.hotline || '';

    // Session management (authenticated users only)
    let sessionId = null;
    if (uid && db) {
        sessionId = await getOrCreateSession(db, uid, retentionHours);
    }

    // ── Direct chip tap: selectedQuestionId or intentCode provided ────────────
    if (selectedQuestionId || reqIntentCode) {
        const dataset = await getDataset();
        const matched = selectedQuestionId
            ? dataset.find(d => d.id === selectedQuestionId)
            : dataset.find(d => d.intentCode === reqIntentCode);

        if (matched) {
            const displayMsg = message || matched.question;
            const botText = matched.answerText || '';
            const recs = (matched.suggestProductIds || []).map(id => ({ id, name: '' }));

            saveMessages(db, uid, sessionId, displayMsg, botText, matched.intentCode, recs);

            if (isSSE) {
                startSSE(res);
                streamTextAsSSE(res, botText);
                res.write(`event: done\ndata: ${JSON.stringify({ message: botText, intentCode: matched.intentCode, matchMethod: 'direct', suggestions: recs })}\n\n`);
                return res.end();
            }
            return res.json({ success: true, reply: botText, intentCode: matched.intentCode, matchMethod: 'direct', suggestions: recs });
        }
        // If ID not found in dataset, fall through to text matching below
    }

    // ── Text-based message: run 3-layer matching ──────────────────────────────
    if (message) {
        const matchResult = await matchDataset(message);

        if (matchResult) {
            const { item, method } = matchResult;
            const botText = item.answerText || '';
            const recs = (item.suggestProductIds || []).map(id => ({ id, name: '' }));

            saveMessages(db, uid, sessionId, message, botText, item.intentCode, recs);

            if (isSSE) {
                startSSE(res);
                streamTextAsSSE(res, botText);
                res.write(`event: done\ndata: ${JSON.stringify({ message: botText, intentCode: item.intentCode, matchMethod: method, suggestions: recs })}\n\n`);
                return res.end();
            }
            return res.json({ success: true, reply: botText, intentCode: item.intentCode, matchMethod: method, suggestions: recs });
        }

        // ── Out-of-dataset: structured fallback, NO free Gemini answering ────
        const oodText = 'Xin lỗi, tôi chưa có câu trả lời cho câu hỏi này trong cơ sở dữ liệu. Bạn có thể chọn một trong các chủ đề bên dưới, hoặc liên hệ nhân viên hỗ trợ để được giải đáp trực tiếp.';
        const actions = buildActions(hotline);

        saveMessages(db, uid, sessionId, message, oodText, null, []);

        if (isSSE) {
            startSSE(res);
            streamTextAsSSE(res, oodText);
            res.write(`event: done\ndata: ${JSON.stringify({ message: oodText, isOutOfDataset: true, actions, suggestions: [] })}\n\n`);
            return res.end();
        }
        return res.json({ success: true, reply: oodText, isOutOfDataset: true, actions, suggestions: [] });
    }

    return res.status(400).json({ success: false, message: 'message is required' });
};

// ── DELETE /api/v1/chat/history ───────────────────────────────────────────────

exports.clearChatHistory = async (req, res) => {
    const db = getFirestore();
    const uid = req.user?.firebaseUid;
    if (!uid || !db) return res.json({ success: true });

    try {
        const histSnap = await db.collection('users').doc(uid).collection('chat_history').get();
        if (!histSnap.empty) {
            const batch = db.batch();
            histSnap.docs.forEach(doc => batch.delete(doc.ref));
            await batch.commit();
        }
        // Close all active sessions
        const sessSnap = await db.collection('users').doc(uid)
            .collection('chatSessions').where('status', '==', 'active').get();
        if (!sessSnap.empty) {
            const batch2 = db.batch();
            sessSnap.docs.forEach(doc => batch2.update(doc.ref, { status: 'closed' }));
            await batch2.commit();
        }
    } catch (err) {
        console.error('[CLEAR HISTORY]', err.message);
    }
    return res.json({ success: true });
};

// ── POST /api/v1/chat/handoff ─────────────────────────────────────────────────

exports.postHandoff = async (req, res) => {
    const db = getFirestore();
    const uid = req.user?.firebaseUid;
    if (!uid) return res.status(401).json({ success: false, message: 'Authentication required' });
    if (!db)  return res.status(500).json({ success: false, message: 'Service unavailable' });

    const { reason } = req.body;
    const config = await fetchConfig(db);
    const hotline = config?.hotline || '';

    try {
        const sessRef = db.collection('users').doc(uid).collection('chatSessions');
        let snap;
        try {
            snap = await sessRef.where('status', '==', 'active').get();
        } catch (_) { snap = { empty: true }; }

        const now = new Date();
        let sessionId;

        if (!snap.empty) {
            const sorted = snap.docs.sort((a, b) => {
                const at = a.data().startedAt?.toMillis ? a.data().startedAt.toMillis() : 0;
                const bt = b.data().startedAt?.toMillis ? b.data().startedAt.toMillis() : 0;
                return bt - at;
            });
            const doc = sorted[0];
            sessionId = doc.data().sessionId;
            await doc.ref.update({ status: 'human_support_requested', handoffReason: reason || '', handoffAt: now });
        } else {
            // Create a handoff record even without an active chat
            sessionId = randomUUID();
            await sessRef.add({
                sessionId,
                status: 'human_support_requested',
                startedAt: now,
                lastMessageAt: now,
                handoffReason: reason || '',
                handoffAt: now
            });
        }

        // Save system message to chat history so admin can see context
        await db.collection('users').doc(uid).collection('chat_history').add({
            sender: 'system',
            text: 'Yêu cầu hỗ trợ nhân viên đã được ghi nhận.',
            sessionId,
            productData: null,
            createdAt: now
        });

        return res.json({
            success: true,
            message: 'Yêu cầu hỗ trợ đã được ghi nhận. Nhân viên sẽ liên hệ với bạn sớm.',
            sessionStatus: 'human_support_requested',
            hotline
        });
    } catch (err) {
        console.error('[HANDOFF]', err.message);
        return res.status(500).json({ success: false, message: 'Failed to process handoff request' });
    }
};

// ── GET /api/v1/chat/categories ───────────────────────────────────────────────
// Returns chatCategories filtered by parentId query param.
// parentId absent or "null" → return root-level categories (parentId == null).

exports.getChatCategories = async (req, res) => {
    try {
        const db = getFirestore();
        if (!db) return res.json({ success: true, data: [] });

        const { parentId } = req.query;
        const isRoot = !parentId || parentId === 'null' || parentId === '';

        let snap;
        try {
            snap = isRoot
                ? await db.collection('chatCategories')
                    .where('isActive', '==', true)
                    .where('parentId', '==', null)
                    .get()
                : await db.collection('chatCategories')
                    .where('isActive', '==', true)
                    .where('parentId', '==', parentId)
                    .get();
        } catch (err) {
            console.error('[CHAT CATEGORIES] query error:', err.message);
            return res.json({ success: true, data: [] });
        }

        const categories = snap.docs
            .map(doc => ({ id: doc.id, ...doc.data() }))
            .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0));

        return res.json({ success: true, data: categories });
    } catch (err) {
        console.error('[CHAT CATEGORIES]', err.message);
        return res.json({ success: true, data: [] });
    }
};
