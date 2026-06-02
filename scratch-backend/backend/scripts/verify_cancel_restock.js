const axios = require('axios');

// Use 127.0.0.1 to avoid IPv6 issues
const BASE_URL = 'http://127.0.0.1:5001/api/v1';
const TEST_EMAIL = 'test_cancel_' + Date.now() + '@example.com';
const TEST_PASSWORD = 'password123';

// Mock Data
let token = '';
let userId = '';
let productId = '';
let initialStock = 0;
let initialReserved = 0;
let orderId = '';

const log = (msg, type = 'info') => {
    if (type === 'success') console.log(`✅ ${msg}`);
    else if (type === 'error') console.log(`❌ ${msg}`);
    else if (type === 'warn') console.log(`⚠️ ${msg}`);
    else console.log(`ℹ️ ${msg}`);
};

async function runTest() {
    try {
        log('--- STARTING CANCEL ORDER BUG VERIFICATION ---');

        // 1. REGISTER & LOGIN
        log('1. Registering/Logging in test user...');
        try {
            await axios.post(`${BASE_URL}/auth/register`, {
                name: 'Test Cancel User',
                email: TEST_EMAIL,
                password: TEST_PASSWORD
            });
        } catch (e) {
            // Ignore if already exists (unlikely with timestamp)
        }

        const loginRes = await axios.post(`${BASE_URL}/auth/login`, {
            email: TEST_EMAIL,
            password: TEST_PASSWORD
        });
        token = loginRes.data.token;
        if (loginRes.data.user) {
            userId = loginRes.data.user._id;
        } else {
             // Fallback or just ignore if not needed strictly for next steps (though controller uses req.user.id from token)
             log('Warning: User object not returned in login response', 'warn');
        }
        log(`Logged in. Token acquired.`);

        // 2. GET A PRODUCT TO TEST
        log('2. Fetching a product to test...');
        const productsRes = await axios.get(`${BASE_URL}/products`);
        // API returns { data: [...], ... }
        const product = productsRes.data.data ? productsRes.data.data[0] : null; 
        if (!product) {
            console.log('API Response:', productsRes.data);
            throw new Error('No products found to test.');
        }
        
        // Use Product_ID (string) for addToCart as per controller logic (Product.findOne({ Product_ID: productId }))
        productId = product.id || product.Product_ID; 
        
        // Note: For stock verification, we need to know the initial stock.
        // Public API returns Stock_Quantity.
        initialStock = product.Stock_Quantity;
        initialReserved = product.Stock_Reserved || 0;
        
        log(`Selected Product: ${product.Name} (ID: ${productId})`);
        log(`Initial Stock: ${initialStock} | Reserved: ${initialReserved}`);

        if (initialStock < 1) throw new Error('Product out of stock. Cannot test order creation.');

        // 3. ADD TO CART
        log('3. Adding to Cart...');
        await axios.post(`${BASE_URL}/cart/add`, {
            productId: productId,
            quantity: 1
        }, { headers: { Authorization: `Bearer ${token}` } });
        log('Added to cart.');

        // 4. CREATE ORDER
        log('4. Creating Order...');
        // Endpoint: /orders/create
        const orderRes = await axios.post(`${BASE_URL}/orders/create`, {
            shippingAddress: { 
                fullName: "Test User",
                address: "123 Test St", 
                city: "Test City", 
                phone: "0123456789" 
            },
            paymentMethod: "VNPAY"
        }, { headers: { Authorization: `Bearer ${token}` } });
        
        console.log('Order Create Response Data:', JSON.stringify(orderRes.data, null, 2));

        // Check controller response for Create Order
        // Response is { message: "...", orderId: "..." }
        orderId = orderRes.data.orderId;
        if (!orderId && orderRes.data.order && orderRes.data.order._id) {
            orderId = orderRes.data.order._id;
        }

        if (!orderId) {
             throw new Error('Failed to retrieve Order ID from response');
        }
        
        log(`Order Created: ${orderId}`);

        // 5. VERIFY STOCK AFTER ORDER (Should be -1 Stock, +1 Reserved)
        log('5. Verifying Stock AFTER Order (Expected: Stock -1, Reserved +1)...');
        // Use Public API to verify.
        // Note: Public API lookup usually by ID or Slug. 
        // /products/:id uses getProductDetail which searches by Product_ID or Slug or _id?
        // Let's check getProductDetail.
        // It likely uses findOne({ Product_ID: param }) or findById(param).
        // If we use productId (which is Product_ID), it should work.
        const pResAfterOrder = await axios.get(`${BASE_URL}/products/${productId}`);
        const pAfterOrder = pResAfterOrder.data;
        
        log(`Current Stock: ${pAfterOrder.Stock_Quantity} | Reserved: ${pAfterOrder.Stock_Reserved}`);

        if (pAfterOrder.Stock_Quantity !== initialStock - 1) {
            log(`ERROR: Stock did not decrease correctly! Expected ${initialStock - 1}, got ${pAfterOrder.Stock_Quantity}`, 'error');
        } else {
            log(`OK: Stock decreased by 1.`, 'success');
        }

        if (pAfterOrder.Stock_Reserved !== initialReserved + 1) {
            log(`ERROR: Reserved did not increase correctly! Expected ${initialReserved + 1}, got ${pAfterOrder.Stock_Reserved}`, 'error');
        } else {
            log(`OK: Reserved increased by 1.`, 'success');
        }

        // 6. CANCEL ORDER
        log('6. Cancelling Order...');
        // Endpoint: POST /orders/:id/cancel
        const cancelRes = await axios.post(`${BASE_URL}/orders/${orderId}/cancel`, {}, { 
            headers: { Authorization: `Bearer ${token}` } 
        });
        log(`Order Cancelled: ${cancelRes.data.message}`);

        // 7. VERIFY STOCK AFTER CANCEL (Should return to Initial)
        log('7. Verifying Stock AFTER Cancel (Expected: Return to Initial)...');
        const pResAfterCancel = await axios.get(`${BASE_URL}/products/${productId}`);
        const pAfterCancel = pResAfterCancel.data;

        log(`Final Stock: ${pAfterCancel.Stock_Quantity} | Reserved: ${pAfterCancel.Stock_Reserved}`);

        let passed = true;

        if (pAfterCancel.Stock_Quantity !== initialStock) {
            log(`FAIL: Stock NOT restored! Expected ${initialStock}, got ${pAfterCancel.Stock_Quantity}`, 'error');
            passed = false;
        } else {
            log(`PASS: Stock restored correctly.`, 'success');
        }

        if (pAfterCancel.Stock_Reserved !== initialReserved) {
            log(`FAIL: Reserved NOT released! Expected ${initialReserved}, got ${pAfterCancel.Stock_Reserved}`, 'error');
            passed = false;
        } else {
            log(`PASS: Reserved released correctly.`, 'success');
        }

        if (passed) {
            log('\n✅ VERIFICATION SUCCESSFUL: THE BUG IS FIXED.', 'success');
        } else {
            log('\n❌ VERIFICATION FAILED: THE BUG PERSISTS.', 'error');
        }

    } catch (error) {
        console.error("FULL ERROR OBJECT:", error);
        log(`Test Failed with Error: ${error.message || 'Unknown Error'}`, 'error');
        if (error.response) {
            console.log('Response Status:', error.response.status);
            console.log('Response Data:', JSON.stringify(error.response.data, null, 2));
        } else if (error.request) {
            console.log('No response received. Request details:', error.request);
        }
    }
}

runTest();