package com.example.tirtir_mcommerce.ui.fragments;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.User;
import com.example.tirtir_mcommerce.ui.activities.LoginActivity;
import com.example.tirtir_mcommerce.viewmodel.AuthViewModel;
import com.example.tirtir_mcommerce.viewmodel.ProfileViewModel;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * ProfileFragment - Màn hình Tài khoản người dùng.
 *
 * Android Components được thể hiện (cho Báo cáo):
 * - Fragment: Đây là một Fragment trong Bottom Navigation
 *
 * Tính năng theo Syllabus:
 * - AlertDialog: Xác nhận đăng xuất
 * - Multi-language: Chuyển đổi ngôn ngữ
 *
 * Sprint 1.1:
 * - Avatar upload: Camera/Gallery → Firebase Storage
 * - Wishlist: Navigate WishlistFragment
 */
public class ProfileFragment extends Fragment {

    // ===========================
    // REQUEST CODES
    // ===========================
    private static final int REQUEST_CAMERA_PERMISSION = 101;
    private static final int REQUEST_STORAGE_PERMISSION = 102;

    // ===========================
    // VIEWS
    // ===========================
    private ProfileViewModel profileViewModel;
    private AuthViewModel authViewModel;

    private ImageView imgAvatar;
    private TextView tvUserName, tvUserEmail, tvInitials, tvCurrentLanguage;
    private ImageButton btnEditProfile;
    private LinearLayout layoutMyOrders, layoutMyAddresses, layoutMyWishlist, layoutLanguage;
    private MaterialButton btnLogout;
    private ProgressBar progressAvatarUpload;

    // ===========================
    // CAMERA / GALLERY
    // ===========================
    private Uri cameraImageUri; // URI tạm lưu ảnh chụp camera

    /** Activity Result Launcher cho Gallery */
    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri selectedUri = result.getData().getData();
                    if (selectedUri != null) {
                        previewAndUploadAvatar(selectedUri);
                    }
                }
            }
    );

    /** Activity Result Launcher cho Camera */
    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && cameraImageUri != null) {
                    previewAndUploadAvatar(cameraImageUri);
                }
            }
    );

    /** Permission launcher cho Camera */
    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (granted) openCamera();
                else Toast.makeText(getContext(), "Cần quyền Camera để chụp ảnh", Toast.LENGTH_SHORT).show();
            }
    );

    /** Permission launcher cho Storage (Android < 13) */
    private final ActivityResultLauncher<String> storagePermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (granted) openGallery();
                else Toast.makeText(getContext(), "Cần quyền bộ nhớ để chọn ảnh", Toast.LENGTH_SHORT).show();
            }
    );

    // ===========================
    // LIFECYCLE
    // ===========================

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        profileViewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        bindViews(view);
        observeViewModel();
        setListeners();

        // Load dữ liệu Profile
        profileViewModel.loadProfile();
    }

    // ===========================
    // BIND VIEWS
    // ===========================

    private void bindViews(View view) {
        imgAvatar = view.findViewById(R.id.imgAvatar);
        tvUserName = view.findViewById(R.id.tvUserName);
        tvUserEmail = view.findViewById(R.id.tvUserEmail);
        tvInitials = view.findViewById(R.id.tvInitials);
        tvCurrentLanguage = view.findViewById(R.id.tvCurrentLanguage);
        btnEditProfile = view.findViewById(R.id.btnEditProfile);
        layoutMyOrders = view.findViewById(R.id.layoutMyOrders);
        layoutMyAddresses = view.findViewById(R.id.layoutMyAddresses);
        layoutMyWishlist = view.findViewById(R.id.layoutMyWishlist);
        layoutLanguage = view.findViewById(R.id.layoutLanguage);
        btnLogout = view.findViewById(R.id.btnLogout);
        progressAvatarUpload = view.findViewById(R.id.progressAvatarUpload);
    }

    // ===========================
    // OBSERVE VIEWMODEL
    // ===========================

    private void observeViewModel() {
        profileViewModel.userLiveData.observe(getViewLifecycleOwner(), this::bindUserData);

        profileViewModel.errorMessage.observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });

        profileViewModel.successMessage.observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });

        // Hiện ProgressBar khi đang upload avatar
        profileViewModel.avatarUploadLoading.observe(getViewLifecycleOwner(), isUploading -> {
            if (progressAvatarUpload != null) {
                progressAvatarUpload.setVisibility(isUploading ? View.VISIBLE : View.GONE);
            }
            // Disable avatar click khi đang upload
            imgAvatar.setEnabled(!isUploading);
        });
    }

    private void bindUserData(User user) {
        if (user == null) return;

        tvUserName.setText(user.getName());
        tvUserEmail.setText(user.getEmail());
        tvInitials.setText(user.getInitials());

        // Load ảnh avatar bằng Glide (nếu có)
        if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
            Glide.with(this)
                    .load(user.getAvatar())
                    .circleCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(imgAvatar);
            tvInitials.setVisibility(View.GONE);
            imgAvatar.setVisibility(View.VISIBLE);
        } else {
            imgAvatar.setVisibility(View.INVISIBLE);
            tvInitials.setVisibility(View.VISIBLE);
        }
    }

    // ===========================
    // LISTENERS
    // ===========================

    private void setListeners() {
        // ===== ALERT DIALOG - Xác nhận Đăng xuất (Yêu cầu môn học) =====
        btnLogout.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.dialog_confirm_title)
                    .setMessage(R.string.dialog_logout_confirm)
                    .setPositiveButton(R.string.btn_confirm, (dialog, which) -> performLogout())
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show();
        });

        // ===== AVATAR - Chọn Camera hoặc Gallery =====
        imgAvatar.setOnClickListener(v -> showAvatarPickerDialog());
        // Cũng cho phép click vào chữ viết tắt (khi chưa có avatar)
        tvInitials.setOnClickListener(v -> showAvatarPickerDialog());

        // Chuyển màn hình Địa chỉ
        layoutMyAddresses.setOnClickListener(v -> {
            // TODO Sprint 1 - FE2: Implement AddressFragment
            Toast.makeText(getContext(), "Quản lý địa chỉ (đang phát triển)", Toast.LENGTH_SHORT).show();
        });

        // Chuyển màn hình Đơn hàng
        layoutMyOrders.setOnClickListener(v -> {
            // TODO Sprint 2 - FE2: Implement OrderHistoryFragment
            Toast.makeText(getContext(), "Đơn hàng của tôi (đang phát triển)", Toast.LENGTH_SHORT).show();
        });

        // ===== WISHLIST - Navigate WishlistFragment (ContentProvider) =====
        layoutMyWishlist.setOnClickListener(v -> navigateToWishlist());

        // Chuyển đổi ngôn ngữ
        layoutLanguage.setOnClickListener(v -> showLanguageDialog());

        btnEditProfile.setOnClickListener(v -> {
            // TODO: Implement EditProfileActivity
            Toast.makeText(getContext(), "Chỉnh sửa hồ sơ (đang phát triển)", Toast.LENGTH_SHORT).show();
        });
    }

    // ===========================
    // AVATAR PICKER (Camera / Gallery)
    // ===========================

    /**
     * Hiển thị Dialog chọn nguồn ảnh: Camera hoặc Thư viện.
     */
    private void showAvatarPickerDialog() {
        String[] options = {"📷 Chụp ảnh bằng Camera", "🖼️ Chọn từ Thư viện"};
        new AlertDialog.Builder(requireContext())
                .setTitle("Cập nhật ảnh đại diện")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) checkCameraPermissionAndOpen();
                    else checkStoragePermissionAndOpen();
                })
                .show();
    }

    /** Kiểm tra quyền Camera trước khi mở */
    private void checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    /** Kiểm tra quyền Storage trước khi mở Gallery (Android < 13 cần xin) */
    private void checkStoragePermissionAndOpen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: không cần xin READ_EXTERNAL_STORAGE cho media
            openGallery();
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
    }

    /** Mở Camera và tạo URI tạm cho ảnh chụp */
    private void openCamera() {
        try {
            File imageFile = createTempImageFile();
            cameraImageUri = FileProvider.getUriForFile(
                    requireContext(),
                    "com.example.tirtir_mcommerce.fileprovider",
                    imageFile
            );
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
            cameraLauncher.launch(cameraIntent);
        } catch (IOException e) {
            Toast.makeText(getContext(), "Không thể mở camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /** Mở Gallery để chọn ảnh */
    private void openGallery() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryIntent.setType("image/*");
        galleryLauncher.launch(galleryIntent);
    }

    /** Tạo file ảnh tạm thời trong thư mục Pictures của app */
    private File createTempImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "avatar_" + timeStamp;
        File storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    /**
     * Preview ảnh ngay lập tức rồi bắt đầu upload lên Firebase Storage.
     *
     * @param imageUri URI của ảnh (từ Camera hoặc Gallery)
     */
    private void previewAndUploadAvatar(Uri imageUri) {
        // Hiện preview ngay (UX tốt hơn — user thấy thay đổi ngay)
        Glide.with(this)
                .load(imageUri)
                .circleCrop()
                .into(imgAvatar);
        imgAvatar.setVisibility(View.VISIBLE);
        tvInitials.setVisibility(View.GONE);

        // Upload lên Firebase Storage qua ViewModel
        profileViewModel.uploadAvatar(imageUri);
    }

    // ===========================
    // WISHLIST NAVIGATION
    // ===========================

    /**
     * Điều hướng sang WishlistFragment để xem danh sách yêu thích.
     * Dùng ContentProvider để query SQLite Wishlist DB.
     */
    private void navigateToWishlist() {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.frame_container, new WishlistFragment())
                .addToBackStack("wishlist")
                .commit();
    }

    // ===========================
    // LOGOUT
    // ===========================

    private void performLogout() {
        authViewModel.logout(() -> {
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(getContext(), getString(R.string.toast_logout_success), Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(getActivity(), LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            });
        });
    }

    // ===========================
    // LANGUAGE DIALOG
    // ===========================

    /**
     * Dialog chọn ngôn ngữ (Yêu cầu môn học: Dialog + Multi-language)
     */
    private void showLanguageDialog() {
        String[] languages = {"🇻🇳 Tiếng Việt", "🇺🇸 English"};
        new AlertDialog.Builder(requireContext())
                .setTitle("Chọn ngôn ngữ / Select Language")
                .setItems(languages, (dialog, which) -> {
                    if (which == 0) {
                        tvCurrentLanguage.setText("🇻🇳 VI");
                        Toast.makeText(getContext(), "Đã chuyển sang Tiếng Việt", Toast.LENGTH_SHORT).show();
                    } else {
                        tvCurrentLanguage.setText("🇺🇸 EN");
                        Toast.makeText(getContext(), "Switched to English", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }
}
