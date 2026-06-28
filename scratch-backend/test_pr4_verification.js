const fcmService = require('./services/fcmService');
const restockAlertService = require('./services/restockAlertService');
const ingredientConflictService = require('./services/ingredientConflictService');

console.log('--- PR4 Code Verification ---');
console.log('1. fcmService functions exported:', Object.keys(fcmService));
console.log('2. restockAlertService exported:', Object.keys(restockAlertService));
console.log('3. ingredientConflictService exported:', Object.keys(ingredientConflictService));

console.log('SUCCESS: All PR4 services and modules loaded properly!');
