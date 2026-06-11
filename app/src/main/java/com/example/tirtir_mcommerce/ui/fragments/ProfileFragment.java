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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import com.example.tirtir_mcommerce.ui.activities.AddressManagerActivity;
import com.example.tirtir_mcommerce.ui.activities.LoginActivity;
import com.example.tirtir_mcommerce.viewmodel.AuthViewModel;
import com.example.tirtir_mcommerce.viewmodel.ProfileViewModel;
import com.example.tirtir_mcommerce.ui.adapters.AddressAdapter;
import com.example.tirtir_mcommerce.model.Address;
import java.util.ArrayList;
import java.util.List;

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

    private ImageView ivAvatar;
    private TextView tvUserName, tvEmail, tvInitials;
    private ImageButton btnEditProfile;
    private LinearLayout layoutMyOrders, layoutMyAddresses, layoutMyWishlist;
    private com.google.android.material.button.MaterialButton btnLogout;
    private ProgressBar progressAvatarUpload;
    private com.google.android.material.chip.ChipGroup chipGroupSkinType;
    private com.google.android.material.chip.Chip chipSkinOily, chipSkinDry, chipSkinCombo, chipSkinSensitive, chipSkinNormal;
    private RecyclerView rvAddresses;
    private AddressAdapter addressAdapter;

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

        // Load dữ liệu Profile và Địa chỉ
        profileViewModel.loadProfile();
        profileViewModel.loadAddresses();
    }

    // ===========================
    // BIND VIEWS
    // ===========================

    private void bindViews(View view) {
        ivAvatar = view.findViewById(R.id.ivAvatar);
        tvUserName = view.findViewById(R.id.tvUserName);
        tvEmail = view.findViewById(R.id.tvEmail);
        tvInitials = view.findViewById(R.id.tvInitials);
        btnEditProfile = view.findViewById(R.id.btnEditProfile);
        layoutMyOrders = view.findViewById(R.id.layoutMyOrders);
        layoutMyAddresses = view.findViewById(R.id.layoutMyAddresses);
        layoutMyWishlist = view.findViewById(R.id.layoutMyWishlist);
        btnLogout = view.findViewById(R.id.btnLogout);
        progressAvatarUpload = view.findViewById(R.id.progressAvatarUpload);
        
        chipGroupSkinType = view.findViewById(R.id.chipGroupSkinType);
        chipSkinOily = view.findViewById(R.id.chipSkinOily);
        chipSkinDry = view.findViewById(R.id.chipSkinDry);
        chipSkinCombo = view.findViewById(R.id.chipSkinCombo);
        chipSkinSensitive = view.findViewById(R.id.chipSkinSensitive);
        chipSkinNormal = view.findViewById(R.id.chipSkinNormal);
        
        rvAddresses = view.findViewById(R.id.rvAddresses);

        setupAddressesRecyclerView();
    }

    private void setupAddressesRecyclerView() {
        addressAdapter = new AddressAdapter(new ArrayList<>(), new AddressAdapter.AddressActionListener() {
            @Override
            public void onEditAddress(Address address) {
                Toast.makeText(getContext(), "Chỉnh sửa địa chỉ (đang phát triển)", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDeleteAddress(Address address) {
                profileViewModel.deleteAddress(address.getId());
            }

            @Override
            public void onSetDefault(Address address) {
                profileViewModel.setDefaultAddress(address.getId());
            }
        });
        rvAddresses.setLayoutManager(new LinearLayoutManager(getContext()));
        rvAddresses.setAdapter(addressAdapter);
    }

    // ===========================
    // OBSERVE VIEWMODEL
    // ===========================

    private void observeViewModel() {
        // Observe User Profile from API
        profileViewModel.userLiveData.observe(getViewLifecycleOwner(), this::bindUserData);

        // Observe Addresses
        profileViewModel.addressesLiveData.observe(getViewLifecycleOwner(), addresses -> {
            if (addresses != null && addressAdapter != null) {
                addressAdapter.updateData(addresses);
            }
        });

        // Loading state
        profileViewModel.isLoading.observe(getViewLifecycleOwner(), isLoading -> {
            // Optional: show a progress bar for profile loading
        });

        // Error message
        profileViewModel.errorMessage.observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
            }
        });

        // Success message
        profileViewModel.successMessage.observe(getViewLifecycleOwner(), message -> {
            if (message != null) {
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });

        // Avatar loading state
        profileViewModel.avatarUploadLoading.observe(getViewLifecycleOwner(), isLoading -> {
            if (progressAvatarUpload != null) {
                progressAvatarUpload.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void bindUserData(User user) {
        if (user == null) return;

        tvUserName.setText(user.getName());
        tvEmail.setText(user.getEmail());
        tvInitials.setText(user.getInitials());

        // Load ảnh avatar bằng Glide (nếu có)
        if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
            Glide.with(this)
                    .load(user.getAvatar())
                    .circleCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(ivAvatar);
            tvInitials.setVisibility(View.GONE);
            ivAvatar.setVisibility(View.VISIBLE);
        } else {
            ivAvatar.setVisibility(View.INVISIBLE);
            tvInitials.setVisibility(View.VISIBLE);
        }

        // Lấy thông tin mở rộng từ Firestore (role, skinType, loyalty fields)
        try {
            com.google.firebase.auth.FirebaseUser firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (firebaseUser != null) {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(firebaseUser.getUid())
                        .addSnapshotListener((documentSnapshot, e) -> {
                            if (e != null) {
                                android.util.Log.e("ProfileFragment", "Firestore listen failed.", e);
                                return;
                            }

                            if (documentSnapshot != null && documentSnapshot.exists()) {
                                String skinType = documentSnapshot.getString("skinType");
                                String loyaltyTier = documentSnapshot.getString("loyaltyTier");
                                Long loyaltyPoints = documentSnapshot.getLong("loyaltyPoints");

                                if (skinType != null && !skinType.isEmpty()) {
                                    chipGroupSkinType.setVisibility(View.VISIBLE);
                                    if (skinType.toLowerCase().contains("dầu") || skinType.equalsIgnoreCase("oily")) {
                                        chipSkinOily.setChecked(true);
                                    } else if (skinType.toLowerCase().contains("khô") || skinType.equalsIgnoreCase("dry")) {
                                        chipSkinDry.setChecked(true);
                                    } else if (skinType.toLowerCase().contains("hỗn hợp") || skinType.equalsIgnoreCase("combo")) {
                                        chipSkinCombo.setChecked(true);
                                    } else if (skinType.toLowerCase().contains("nhạy cảm") || skinType.equalsIgnoreCase("sensitive")) {
                                        chipSkinSensitive.setChecked(true);
                                    } else {
                                        chipSkinNormal.setChecked(true);
                                    }
                                } else {
                                    chipGroupSkinType.setVisibility(View.GONE);
                                }
                            }
                        });
            }
        } catch (Exception ex) {
            android.util.Log.e("ProfileFragment", "Error loading Firestore profile", ex);
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
        ivAvatar.setOnClickListener(v -> showAvatarPickerDialog());
        // Cũng cho phép click vào chữ viết tắt (khi chưa có avatar)
        tvInitials.setOnClickListener(v -> showAvatarPickerDialog());

        // Chuyển màn hình Địa chỉ
        layoutMyAddresses.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), AddressManagerActivity.class));
        });

        // Chuyển màn hình Đơn hàng
        layoutMyOrders.setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, new OrderHistoryFragment())
                    .addToBackStack("order_history")
                    .commit();
        });

        // ===== WISHLIST - Navigate WishlistFragment (ContentProvider) =====
        layoutMyWishlist.setOnClickListener(v -> navigateToWishlist());

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

    private void previewAndUploadAvatar(Uri imageUri) {
        // Hiện preview ngay (UX tốt hơn — user thấy thay đổi ngay)
        Glide.with(this)
                .load(imageUri)
                .circleCrop()
                .into(ivAvatar);
        ivAvatar.setVisibility(View.VISIBLE);
        tvInitials.setVisibility(View.GONE);

        // Upload lên Firebase Storage qua ViewModel
        profileViewModel.uploadAvatar(imageUri);
    }

    // ===========================
    // WISHLIST NAVIGATION
    // ===========================

    private void navigateToWishlist() {
        startActivity(new android.content.Intent(requireContext(), com.example.tirtir_mcommerce.ui.activities.WishlistActivity.class));
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


}
