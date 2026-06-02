const User = require('../models/user.model');

// GET /api/v1/admin/users
// List all users with pagination, search & role filter
exports.getAllUsers = async (req, res) => {
    try {
        const page = parseInt(req.query.page) || 1;
        const limit = parseInt(req.query.limit) || 10;
        const skip = (page - 1) * limit;
        const search = req.query.search; // Name or Email
        const roleFilter = req.query.role; // Filter by role: user | admin | customer_service | inventory_staff

        let query = {};

        // Apply role filter if provided, otherwise default to non-admin users
        if (roleFilter) {
            query.role = roleFilter;
        } else {
            // Default: show all non-admin users (customers)
            query.role = 'user';
        }

        if (search) {
            query.$or = [
                { name: { $regex: search, $options: 'i' } },
                { email: { $regex: search, $options: 'i' } },
                { phone: { $regex: search, $options: 'i' } }
            ];
        }

        const total = await User.countDocuments(query);
        const users = await User.find(query)
            .select('-password') // Exclude password
            .sort({ createdAt: -1 })
            .skip(skip)
            .limit(limit);

        res.json({
            users,
            page,
            pages: Math.ceil(total / limit),
            total,
            roleFilter: roleFilter || 'user'
        });
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

// GET /api/v1/admin/users/:id
exports.getUserDetail = async (req, res) => {
    try {
        const user = await User.findById(req.params.id).select('-password');
        if (!user) {
            return res.status(404).json({ message: 'User not found' });
        }
        res.json(user);
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

// PUT /api/v1/admin/users/:id/status
// Ban/Unban user
exports.updateUserStatus = async (req, res) => {
    try {
        const { isBlocked } = req.body; // Expect boolean
        const user = await User.findById(req.params.id);

        if (!user) {
            return res.status(404).json({ message: 'User not found' });
        }

        // Prevent blocking other admins
        if (user.role === 'admin') {
            return res.status(400).json({ message: 'Cannot block an admin' });
        }

        user.isBlocked = isBlocked;
        await user.save();

        res.json({
            success: true,
            message: `User ${isBlocked ? 'blocked' : 'unblocked'} successfully`,
            user
        });
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

// GET /api/v1/admin/users/:id/orders
// List all orders for a specific user
exports.getUserOrders = async (req, res) => {
    try {
        const userId = req.params.id;
        const Order = require('../models/order.model'); // Import Order model

        const orders = await Order.find({ user: userId })
            .sort({ createdAt: -1 })
            .populate('items.product', 'Name Price Thumbnail_Images'); // Populate product details

        res.json(orders);
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

// GET /api/v1/admin/users/admins
// List all admin and staff users
exports.getAdminUsers = async (req, res) => {
    try {
        const users = await User.find({ role: { $ne: 'user' } })
            .select('-password')
            .sort({ createdAt: -1 });
        res.json(users);
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

// POST /api/v1/admin/users/admin
// Create a new admin or staff user
exports.createAdminUser = async (req, res) => {
    try {
        const { name, email, password, role } = req.body;

        // Validate role
        if (!['admin', 'inventory_staff', 'customer_service'].includes(role)) {
            return res.status(400).json({ message: 'Invalid role' });
        }

        const userExists = await User.findOne({ email });
        if (userExists) {
            return res.status(400).json({ message: 'User already exists' });
        }

        const user = await User.create({
            name,
            email,
            password,
            role,
            isEmailVerified: true // Auto-verify admin created users
        });

        res.status(201).json({
            success: true,
            user: {
                _id: user._id,
                name: user.name,
                email: user.email,
                role: user.role
            }
        });
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

// PUT /api/v1/admin/users/:id/role
// Update user role
exports.updateUserRole = async (req, res) => {
    try {
        const { role } = req.body;

        // Validate role
        if (!['user', 'admin', 'inventory_staff', 'customer_service'].includes(role)) {
            return res.status(400).json({ message: 'Invalid role' });
        }

        const user = await User.findById(req.params.id);

        if (!user) {
            return res.status(404).json({ message: 'User not found' });
        }

        // Prevent modifying the main admin (optional safety check)
        if (user.email === 'admin@tirtir.com' && req.user.email !== 'admin@tirtir.com') {
            return res.status(403).json({ message: 'Cannot modify the main admin' });
        }

        user.role = role;
        await user.save();

        res.json({
            success: true,
            user: {
                _id: user._id,
                name: user.name,
                email: user.email,
                role: user.role
            }
        });
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

// DELETE /api/v1/admin/users/:id
// Delete a user (admin or regular)
exports.deleteUser = async (req, res) => {
    try {
        const user = await User.findById(req.params.id);

        if (!user) {
            return res.status(404).json({ message: 'User not found' });
        }

        // Prevent deleting the main admin
        if (user.email === 'admin@tirtir.com') {
            return res.status(403).json({ message: 'Cannot delete the main admin account' });
        }

        // Prevent deleting yourself
        if (user._id.toString() === req.user._id.toString()) {
            return res.status(400).json({ message: 'Cannot delete your own account' });
        }

        await user.deleteOne();

        res.json({ success: true, message: 'User deleted successfully' });
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};
