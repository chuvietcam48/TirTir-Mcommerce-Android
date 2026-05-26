package com.example.tirtir_mcommerce.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.List;

/**
 * Model đại diện cho một User trong hệ thống TirTir.
 * Implements Serializable để truyền qua Intent giữa các Activity/Fragment.
 */
public class User implements Serializable {

    @SerializedName("_id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("email")
    private String email;

    @SerializedName("phone")
    private String phone;

    @SerializedName("avatar")
    private String avatar;

    @SerializedName("role")
    private String role; // "user", "admin", "inventory_staff", "customer_service"

    @SerializedName("isEmailVerified")
    private boolean isEmailVerified;

    @SerializedName("gender")
    private String gender;

    @SerializedName("birthDate")
    private String birthDate;

    @SerializedName("addresses")
    private List<Address> addresses;

    // ===== Constructors =====
    public User() {}

    public User(String id, String name, String email, String role) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
    }

    // ===== Getters =====
    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getAvatar() { return avatar; }
    public String getRole() { return role; }
    public boolean isEmailVerified() { return isEmailVerified; }
    public String getGender() { return gender; }
    public String getBirthDate() { return birthDate; }
    public List<Address> getAddresses() { return addresses; }

    // ===== Setters =====
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public void setRole(String role) { this.role = role; }
    public void setEmailVerified(boolean emailVerified) { isEmailVerified = emailVerified; }
    public void setGender(String gender) { this.gender = gender; }
    public void setBirthDate(String birthDate) { this.birthDate = birthDate; }
    public void setAddresses(List<Address> addresses) { this.addresses = addresses; }

    // ===== Helpers =====
    public boolean isAdmin() { return "admin".equals(role); }

    /**
     * Lấy tên viết tắt để hiển thị avatar mặc định (khi chưa có ảnh).
     * Ví dụ: "Nguyễn Văn An" -> "NA"
     */
    public String getInitials() {
        if (name == null || name.isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            return String.valueOf(parts[0].charAt(0)).toUpperCase()
                    + String.valueOf(parts[parts.length - 1].charAt(0)).toUpperCase();
        }
        return String.valueOf(parts[0].charAt(0)).toUpperCase();
    }
}
