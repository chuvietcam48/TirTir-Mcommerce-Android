const express = require('express');
const router = express.Router();
const jwt = require('jsonwebtoken');
const { saveRoutine, getCommunityRoutines, likeRoutine, applyRoutine, getRecommendation } = require('../controllers/routineController');

// Flexible middleware: populates req.user if token is present, but allows through if userId is provided in body/query
const flexProtect = (req, res, next) => {
  const authHeader = req.headers['authorization'];
  if (authHeader && authHeader.startsWith('Bearer ')) {
    const token = authHeader.slice(7);
    try {
      req.user = jwt.verify(token, process.env.JWT_SECRET);
    } catch (err) {
      // If token invalid, but userId present in body/query, let controller validate userId
    }
  }
  next();
};

// Base URL: /api/v1/routines and /api/routines
router.post('/save', flexProtect, saveRoutine);
router.get('/community', getCommunityRoutines);
router.post('/:id/like', flexProtect, likeRoutine);
router.post('/:id/apply', flexProtect, applyRoutine);
router.get('/suggest', flexProtect, getRecommendation);
router.get('/suggestion', flexProtect, getRecommendation);
router.get('/recommendation', flexProtect, getRecommendation);

module.exports = router;

