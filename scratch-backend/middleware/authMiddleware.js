const jwt = require('jsonwebtoken');

const protect = (req, res, next) => {
  const authHeader = req.headers['authorization'];
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ success: false, message: 'Chưa xác thực. Vui lòng đăng nhập.' });
  }

  const token = authHeader.slice(7);
  try {
    req.user = jwt.verify(token, process.env.JWT_SECRET);
    next();
  } catch (err) {
    const message =
      err.name === 'TokenExpiredError'
        ? 'Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại.'
        : 'Token không hợp lệ.';
    return res.status(401).json({ success: false, message });
  }
};

const restrictTo = (...roles) => (req, res, next) => {
  if (!roles.includes(req.user.role)) {
    return res.status(403).json({ success: false, message: 'Bạn không có quyền thực hiện thao tác này.' });
  }
  next();
};

const optionalProtect = (req, res, next) => {
  const authHeader = req.headers['authorization'];
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return next();
  }
  const token = authHeader.slice(7);
  try {
    req.user = jwt.verify(token, process.env.JWT_SECRET);
  } catch (err) {
    // Ignore invalid token
  }
  next();
};

module.exports = { protect, restrictTo, optionalProtect };
