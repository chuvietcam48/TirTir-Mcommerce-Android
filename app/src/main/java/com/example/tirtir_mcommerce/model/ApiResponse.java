package com.example.tirtir_mcommerce.model;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

/**
 * Wrapper chung cho tất cả các phản hồi từ API.
 * Backend Node.js thường trả về dạng:
 * {
 *   "success": true/false,
 *   "message": "...",
 *   "data": { ... }   <-- optional
 * }
 *
 * Dùng generic type T để tái sử dụng cho mọi loại response.
 * Ví dụ: ApiResponse<User>, ApiResponse<List<Address>>, ...
 */
public class ApiResponse<T> {

    @SerializedName("success")
    private boolean success;

    @SerializedName("message")
    private String message;

    @SerializedName("warning")
    private boolean warning;

    @SerializedName("data")
    private T data;

    @SerializedName("voucher")
    private Map<String, Object> voucher;

    // ===== Getters =====
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public boolean isWarning() { return warning; }
    public T getData() { return data; }
    public Map<String, Object> getVoucher() { return voucher; }

    // ===== Setters =====
    public void setSuccess(boolean success) { this.success = success; }
    public void setMessage(String message) { this.message = message; }
    public void setData(T data) { this.data = data; }
}
