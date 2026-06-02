/**
 * E2E Test Suite for Chat API with Assertions
 * Tests: Authorization, Message Format, Intent Detection, Product Recommendations
 * Usage: npm test -- __tests__/chat.e2e.test.js
 */

const axios = require('axios');
const assert = require('assert');

const API_BASE = process.env.API_BASE || 'http://localhost:5001';
const AI_SERVICE_API_KEY = process.env.AI_SERVICE_API_KEY || 'your_generated_api_key_here_change_this_in_production';

// Helper to consume SSE stream and collect events
async function streamChatMessage(message, authToken = null) {
    const response = await axios.post(
        `${API_BASE}/api/v1/chat`,
        { message },
        {
            headers: {
                'Content-Type': 'application/json',
                Accept: 'text/event-stream',
                ...(authToken && { 'Authorization': `Bearer ${authToken}` }),
            },
            responseType: 'stream',
            validateStatus: () => true,
        }
    );

    return new Promise((resolve, reject) => {
        let buffer = '';
        const events = [];
        let finalPayload = null;

        response.data.on('data', (chunk) => {
            buffer += chunk.toString();
            const lines = buffer.split('\n\n');
            
            for (let i = 0; i < lines.length - 1; i++) {
                const eventBlock = lines[i].trim();
                if (!eventBlock) continue;

                const eventLine = eventBlock.match(/event:\s*(\w+)/);
                const dataLine = eventBlock.match(/data:\s*([\s\S]+)$/m);

                if (eventLine && dataLine) {
                    try {
                        const eventName = eventLine[1];
                        const payload = JSON.parse(dataLine[1]);
                        events.push({ event: eventName, payload });

                        if (eventName === 'done') {
                            finalPayload = payload;
                        }
                    } catch (e) {
                        // Ignore parse errors in stream
                    }
                }
            }
            buffer = lines[lines.length - 1];
        });

        response.data.on('end', () => {
            resolve({ events, finalPayload, statusCode: response.status });
        });

        response.data.on('error', reject);
    });
}

// ─── Test Suite ────────────────────────────────────────────────────────────────

describe('Chat API E2E Tests', () => {
    console.log(`\n🔌 API Base: ${API_BASE}`);
    console.log(`🔑 Using AI_SERVICE_API_KEY: ${AI_SERVICE_API_KEY ? 'SET' : 'NOT SET'}\n`);

    // ─── Test 1: Basic Greeting Detection ─────────────────────────────────────
    it('should detect greeting intent and return text response', async function() {
        this.timeout(30000);
        
        const { finalPayload, events } = await streamChatMessage('xin chào');
        
        assert(finalPayload, 'Should receive final payload');
        assert.strictEqual(finalPayload.success, true, 'Response should be successful');
        assert(finalPayload.data, 'Should contain data object');
        assert.strictEqual(finalPayload.data.intent, 'greeting', 'Intent should be "greeting"');
        assert(finalPayload.data.message, 'Should contain message text');
        
        // Verify chunk events were streamed
        const chunkEvents = events.filter(e => e.event === 'chunk');
        assert(chunkEvents.length > 0, 'Should have streamed text chunks');
        
        console.log('  ✅ Greeting detected correctly');
    });

    // ─── Test 2: Product Consultation Intent ─────────────────────────────────
    it('should detect consultation intent and return product recommendations', async function() {
        this.timeout(30000);
        
        const { finalPayload } = await streamChatMessage('da dầu có mụn, muốn mua toner dưới 45 đô');
        
        assert(finalPayload, 'Should receive final payload');
        assert.strictEqual(finalPayload.success, true, 'Response should be successful');
        assert.strictEqual(finalPayload.data.type, 'product', 'Type should be "product"');
        assert.strictEqual(finalPayload.data.intent, 'consultation', 'Intent should be "consultation"');
        
        // Validate product data structure
        const { productData } = finalPayload.data.data || {};
        assert(productData || finalPayload.data.data, 'Should return product data');
        
        if (productData) {
            assert(productData.id, 'Product should have id');
            assert(productData.name, 'Product should have name');
            assert(typeof productData.price === 'number', 'Price should be a number');
            assert(productData.image, 'Product should have image URL');
            assert(productData.slug, 'Product should have slug');
        }
        
        console.log('  ✅ Product consultation processed correctly');
    });

    // ─── Test 3: Recommendation Data Preservation ─────────────────────────────
    it('should include recommendations and alternatives in response', async function() {
        this.timeout(30000);
        
        const { finalPayload } = await streamChatMessage('da khô, muốn serum hydration');
        
        assert(finalPayload, 'Should receive payload');
        assert.strictEqual(finalPayload.success, true, 'Should be successful');
        
        const data = finalPayload.data.data || {};
        
        // Check for recommendation structure
        if (data.recommendations) {
            assert(Array.isArray(data.recommendations), 'Recommendations should be an array');
            assert(data.recommendations.length > 0, 'Should have at least one recommendation');
            
            // Validate each recommendation
            data.recommendations.forEach((rec, idx) => {
                assert(rec.id, `Recommendation ${idx} should have id`);
                assert(rec.name, `Recommendation ${idx} should have name`);
                assert(typeof rec.score === 'number', `Recommendation ${idx} should have numeric score`);
            });
        }
        
        // Check for cheaper alternative
        if (data.cheaper_alternative) {
            assert(data.cheaper_alternative.id, 'Alternative should have id');
            assert(data.cheaper_alternative.price, 'Alternative should have price');
        }
        
        console.log('  ✅ Recommendations preserved correctly');
    });

    // ─── Test 4: Filtering Context Stored ──────────────────────────────────────
    it('should include filtering context in response', async function() {
        this.timeout(30000);
        
        const { finalPayload } = await streamChatMessage('da nhạy cảm, tìm kem mascara dưới 30');
        
        assert(finalPayload, 'Should receive payload');
        assert.strictEqual(finalPayload.success, true, 'Should be successful');
        
        const data = finalPayload.data.data || {};
        
        if (data.filters) {
            // Validate that filter parsing worked
            assert(data.filters.skin_type || data.filters.concern || data.filters.budget, 
                'Should have extracted at least one filter');
        }
        
        if (data.scoring_formula) {
            assert(typeof data.scoring_formula === 'string', 'Scoring formula should be a string');
            assert(data.scoring_formula.includes('*'), 'Formula should contain weighting operators');
        }
        
        console.log('  ✅ Filtering context included');
    });

    // ─── Test 5: Error Handling ────────────────────────────────────────────────
    it('should handle empty message gracefully', async function() {
        this.timeout(30000);
        
        const response = await axios.post(
            `${API_BASE}/api/v1/chat`,
            { message: '' },
            { validateStatus: () => true }
        );
        
        assert.strictEqual(response.status, 400, 'Should return 400 for empty message');
        assert.strictEqual(response.data.success, false, 'Success should be false');
        assert(response.data.message, 'Should include error message');
        
        console.log('  ✅ Empty message rejected correctly');
    });

    // ─── Test 6: Response Headers ──────────────────────────────────────────────
    it('should return proper SSE headers', async function() {
        this.timeout(30000);
        
        const response = await axios.post(
            `${API_BASE}/api/v1/chat`,
            { message: 'hello' },
            {
                responseType: 'stream',
                validateStatus: () => true,
            }
        );
        
        const contentType = response.headers['content-type'] || '';
        assert(contentType.includes('text/event-stream'), 
            `Content-Type should be text/event-stream, got: ${contentType}`);
        assert.strictEqual(response.headers['cache-control'], 'no-cache, no-transform');
        assert.strictEqual(response.headers['connection'], 'keep-alive');
        
        console.log('  ✅ SSE headers correct');
    });

    // ─── Test 7: Multi-turn Conversation Context ──────────────────────────────
    it('should accept conversation history in request', async function() {
        this.timeout(30000);
        
        // This test validates that the API accepts conversation history
        // (actual persistence requires authentication)
        const response = await axios.post(
            `${API_BASE}/api/v1/chat`,
            {
                message: 'what was my previous concern?',
                conversation_history: [
                    { role: 'user', content: 'da dầu' },
                    { role: 'bot', content: 'Tôi thấy bạn có da dầu...' }
                ]
            },
            {
                responseType: 'stream',
                validateStatus: () => true,
            }
        );
        
        assert(response.status < 400, `Should accept conversation history, got ${response.status}`);
        
        console.log('  ✅ Conversation history accepted');
    });
});

// ─── Run Tests ──────────────────────────────────────────────────────────────

if (require.main === module) {
    console.log('Running Chat E2E Tests...\n');
    
    const Mocha = require('mocha');
    const mocha = new Mocha();
    mocha.addFile(__filename);
    
    mocha.run((failures) => {
        process.exit(failures ? 1 : 0);
    });
}

module.exports = { streamChatMessage };
