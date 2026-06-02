const User = require('../models/user.model');
const bcrypt = require('bcryptjs');

/**
 * User Profile Controller for TirTir Shop
 * Handles profile management, password changes, and address book operations
 */

// ===== PROFILE OPERATIONS =====

/**
 * @desc    Get user profile
 * @route   GET /api/users/profile
 * @access  Private (Protected)
 */
exports.getProfile = async (req, res) => {
    try {
        // req.user is already populated by protect middleware (excluding password)
        const user = await User.findById(req.user._id).select('-password -emailVerificationToken -resetPasswordToken -resetPasswordExpire');

        if (!user) {
            return res.status(404).json({
                success: false,
                message: 'User not found'
            });
        }

        res.status(200).json({
            success: true,
            data: user
        });
    } catch (error) {
        console.error('Get Profile Error:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to retrieve profile',
            error: error.message
        });
    }
};

/**
 * @desc    Update user profile
 * @route   PUT /api/users/profile
 * @access  Private (Protected)
 */
exports.updateProfile = async (req, res) => {
    try {
        // 🔒 SECURITY: WHITELIST APPROACH - Only allow specific fields
        // This prevents users from modifying role, password, email, etc.
        const { name, avatar, phone, gender, birthDate } = req.body;

        // Build update object with only allowed fields
        const updateData = {};
        if (name !== undefined) updateData.name = name;
        if (avatar !== undefined) updateData.avatar = avatar;
        if (phone !== undefined) updateData.phone = phone;
        if (gender !== undefined) updateData.gender = gender;
        if (birthDate !== undefined) updateData.birthDate = birthDate;

        const updatedUser = await User.findByIdAndUpdate(
            req.user._id,
            updateData,
            {
                new: true, // Return updated document
                runValidators: true // Run mongoose validators
            }
        ).select('-password -emailVerificationToken -resetPasswordToken -resetPasswordExpire');

        if (!updatedUser) {
            return res.status(404).json({
                success: false,
                message: 'User not found'
            });
        }

        res.status(200).json({
            success: true,
            message: 'Profile updated successfully',
            data: updatedUser
        });
    } catch (error) {
        console.error('Update Profile Error:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to update profile',
            error: error.message
        });
    }
};

/**
 * @desc    Change user password
 * @route   POST /api/users/change-password
 * @access  Private (Protected)
 */
exports.changePassword = async (req, res) => {
    try {
        const { currentPassword, oldPassword, newPassword } = req.body;
        const providedCurrent = typeof currentPassword === 'string' && currentPassword.length > 0
            ? currentPassword
            : oldPassword;

        // Validation
        if (!providedCurrent || !newPassword) {
            return res.status(400).json({
                success: false,
                message: 'Vui lòng cung cấp mật khẩu hiện tại và mật khẩu mới'
            });
        }

        if (newPassword.length < 6) {
            return res.status(400).json({
                success: false,
                message: 'Mật khẩu mới phải có ít nhất 6 ký tự'
            });
        }

        // Get user with password (not excluded)
        const user = await User.findById(req.user._id).select('+password');

        if (!user) {
            return res.status(404).json({
                success: false,
                message: 'User not found'
            });
        }

        // Check if old password matches
        const isMatch = await user.matchPassword(providedCurrent);

        if (!isMatch) {
            return res.status(401).json({
                success: false,
                message: 'Mật khẩu hiện tại không đúng'
            });
        }

        // Update password (pre-save hook will hash it automatically)
        user.password = newPassword;
        await user.save();

        res.status(200).json({
            success: true,
            message: 'Password changed successfully'
        });
    } catch (error) {
        console.error('Change Password Error:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to change password',
            error: error.message
        });
    }
};

/**
 * @desc    Upload avatar image
 * @route   POST /api/users/avatar/upload
 * @access  Private (Protected)
 */
exports.uploadAvatar = async (req, res) => {
    try {
        // Check if file was uploaded
        if (!req.file) {
            return res.status(400).json({
                success: false,
                message: 'No file uploaded'
            });
        }

        // Generate avatar URL (relative path from server root)
        const avatarUrl = `/uploads/avatars/${req.file.filename}`;

        // Update user's avatar in database
        const updatedUser = await User.findByIdAndUpdate(
            req.user._id,
            { avatar: avatarUrl },
            {
                new: true, // Return updated document
                runValidators: true
            }
        ).select('-password -emailVerificationToken -resetPasswordToken -resetPasswordExpire');

        if (!updatedUser) {
            return res.status(404).json({
                success: false,
                message: 'User not found'
            });
        }

        res.status(200).json({
            success: true,
            message: 'Avatar uploaded successfully',
            data: {
                avatar: avatarUrl,
                user: updatedUser
            }
        });
    } catch (error) {
        console.error('Upload Avatar Error:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to upload avatar',
            error: error.message
        });
    }
};


// ===== ADDRESS MANAGEMENT =====

/**
 * @desc    Get all user addresses
 * @route   GET /api/users/addresses
 * @access  Private (Protected)
 */
exports.getAddresses = async (req, res) => {
    try {
        const user = await User.findById(req.user._id).select('addresses');

        if (!user) {
            return res.status(404).json({
                success: false,
                message: 'User not found'
            });
        }

        res.status(200).json({
            success: true,
            data: user.addresses
        });
    } catch (error) {
        console.error('Get Addresses Error:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to retrieve addresses',
            error: error.message
        });
    }
};

/**
 * @desc    Add new address
 * @route   POST /api/users/addresses
 * @access  Private (Protected)
 */
exports.addAddress = async (req, res) => {
    try {
        const { fullName, phone, street, city, district, ward, isDefault } = req.body;

        // Validation
        if (!fullName || !phone || !street || !city || !district || !ward) {
            return res.status(400).json({
                success: false,
                message: 'Please provide all required address fields'
            });
        }

        const user = await User.findById(req.user._id);

        if (!user) {
            return res.status(404).json({
                success: false,
                message: 'User not found'
            });
        }

        // If this is the first address OR isDefault is true, set as default
        const shouldBeDefault = user.addresses.length === 0 || isDefault === true;

        // If setting as default, unset all other defaults
        if (shouldBeDefault) {
            user.addresses.forEach(addr => {
                addr.isDefault = false;
            });
        }

        // Add new address
        user.addresses.push({
            fullName,
            phone,
            street,
            city,
            district,
            ward,
            isDefault: shouldBeDefault
        });

        await user.save();

        res.status(201).json({
            success: true,
            message: 'Address added successfully',
            data: user.addresses
        });
    } catch (error) {
        console.error('Add Address Error:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to add address',
            error: error.message
        });
    }
};

/**
 * @desc    Update address
 * @route   PUT /api/users/addresses/:id
 * @access  Private (Protected)
 */
exports.updateAddress = async (req, res) => {
    try {
        const addressId = req.params.id;
        const { fullName, phone, street, city, district, ward } = req.body;

        const user = await User.findById(req.user._id);

        if (!user) {
            return res.status(404).json({
                success: false,
                message: 'User not found'
            });
        }

        // Find the address
        const address = user.addresses.id(addressId);

        if (!address) {
            return res.status(404).json({
                success: false,
                message: 'Address not found'
            });
        }

        // Update fields if provided
        if (fullName !== undefined) address.fullName = fullName;
        if (phone !== undefined) address.phone = phone;
        if (street !== undefined) address.street = street;
        if (city !== undefined) address.city = city;
        if (district !== undefined) address.district = district;
        if (ward !== undefined) address.ward = ward;

        await user.save();

        res.status(200).json({
            success: true,
            message: 'Address updated successfully',
            data: user.addresses
        });
    } catch (error) {
        console.error('Update Address Error:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to update address',
            error: error.message
        });
    }
};

/**
 * @desc    Delete address
 * @route   DELETE /api/users/addresses/:id
 * @access  Private (Protected)
 */
exports.deleteAddress = async (req, res) => {
    try {
        const addressId = req.params.id;

        const user = await User.findById(req.user._id);

        if (!user) {
            return res.status(404).json({
                success: false,
                message: 'User not found'
            });
        }

        // Find the address to check if it's default
        const addressToDelete = user.addresses.id(addressId);

        if (!addressToDelete) {
            return res.status(404).json({
                success: false,
                message: 'Address not found'
            });
        }

        const wasDefault = addressToDelete.isDefault;

        // Remove the address using pull (Mongoose subdocument method)
        user.addresses.pull(addressId);

        // ✅ SMART DEFAULT REASSIGNMENT
        // If deleted address was default AND there are remaining addresses,
        // set the first remaining address as default
        if (wasDefault && user.addresses.length > 0) {
            user.addresses[0].isDefault = true;
        }

        await user.save();

        res.status(200).json({
            success: true,
            message: 'Address deleted successfully',
            data: user.addresses
        });
    } catch (error) {
        console.error('Delete Address Error:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to delete address',
            error: error.message
        });
    }
};

/**
 * @desc    Set address as default
 * @route   PATCH /api/users/addresses/:id/set-default
 * @access  Private (Protected)
 */
exports.setDefaultAddress = async (req, res) => {
    try {
        const addressId = req.params.id;

        const user = await User.findById(req.user._id);

        if (!user) {
            return res.status(404).json({
                success: false,
                message: 'User not found'
            });
        }

        // Find the target address
        const targetAddress = user.addresses.id(addressId);

        if (!targetAddress) {
            return res.status(404).json({
                success: false,
                message: 'Address not found'
            });
        }

        // Set all addresses to not default
        user.addresses.forEach(addr => {
            addr.isDefault = false;
        });

        // Set target address as default
        targetAddress.isDefault = true;

        await user.save();

        res.status(200).json({
            success: true,
            message: 'Default address updated successfully',
            data: user.addresses
        });
    } catch (error) {
        console.error('Set Default Address Error:', error);
        res.status(500).json({
            success: false,
            message: 'Failed to set default address',
            error: error.message
        });
    }
};
