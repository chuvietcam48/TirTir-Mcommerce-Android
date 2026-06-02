const express = require('express');
const router = express.Router();
const menuController = require('../controllers/menu.controller');

router.get('/', menuController.getMenus);
// router.post('/', menuController.createMenu); // Uncomment if needed later

module.exports = router;
