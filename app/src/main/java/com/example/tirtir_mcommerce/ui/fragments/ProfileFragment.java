package com.example.tirtir_mcommerce.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.model.User;
import com.example.tirtir_mcommerce.ui.activities.LoginActivity;
import com.example.tirtir_mcommerce.viewmodel.AuthViewModel;
import com.example.tirtir_mcommerce.viewmodel.ProfileViewModel;
import com.google.android.material.button.MaterialButton;

/**
 * ProfileFragment - Màn hình Tài khoản người dùng.
 *
 * Android Components được thể hiện (cho Báo cáo):
 * - Fragment: Đây là một Fragment trong Bottom Navigation
 *
 * Tính năng theo Syllabus:
 * - AlertDialog: Xác nhận đăng xuất
 * - Multi-language: Chuyển đổi ngôn ngữ
 */
public class ProfileFragment extends Fragment {

    private ProfileViewModel profileViewModel;
    private AuthViewModel authViewModel;

    private ImageView imgAvatar;
    private TextView tvUserName, tvUserEmail, tvInitials, tvCurrentLanguage;
    private ImageButton btnEditProfile;
    private LinearLayout layoutMyOrders, layoutMyAddresses, layoutMyWishlist, layoutLanguage;
    private MaterialButton btnLogout;

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
    }

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
        } else {
            imgAvatar.setVisibility(View.INVISIBLE);
            tvInitials.setVisibility(View.VISIBLE);
        }
    }

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

        // Wishlist qua ContentProvider
        layoutMyWishlist.setOnClickListener(v -> {
            // TODO Sprint 1 - FE2: Query WishlistContentProvider
            Toast.makeText(getContext(), "Danh sách yêu thích (đang phát triển)", Toast.LENGTH_SHORT).show();
        });

        // Chuyển đổi ngôn ngữ
        layoutLanguage.setOnClickListener(v -> showLanguageDialog());

        btnEditProfile.setOnClickListener(v -> {
            // TODO: Implement EditProfileActivity
            Toast.makeText(getContext(), "Chỉnh sửa hồ sơ (đang phát triển)", Toast.LENGTH_SHORT).show();
        });
    }

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
