package com.example.tirtir_mcommerce.network;

import com.example.tirtir_mcommerce.model.Address;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.model.LoginRequest;
import com.example.tirtir_mcommerce.model.LoginResponse;
import com.example.tirtir_mcommerce.model.OrderResponse;
import com.example.tirtir_mcommerce.model.CreateOrderRequest;
import com.example.tirtir_mcommerce.model.ProductResponse;
import com.example.tirtir_mcommerce.model.RegisterRequest;
import com.example.tirtir_mcommerce.model.User;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

/**
 * Interface định nghĩa tất cả các API endpoint của hệ thống TirTir.
 * Retrofit sẽ tự động sinh ra implementation từ interface này.
 *
 * Phân nhóm theo module:
 * - AUTH: Đăng nhập, đăng ký, quản lý phiên đăng nhập
 * - USER PROFILE: Thông tin cá nhân, địa chỉ
 * - PRODUCT: Danh sách, chi tiết sản phẩm
 */
public interface ApiService {

    // ===========================
    // AUTH MODULE
    // ===========================

    /**
     * Đăng nhập - POST /api/v1/auth/login
     * Backend trả về: { success, token, refreshToken, user }
     */
    @POST("api/v1/auth/login")
    Call<LoginResponse> loginUser(@Body LoginRequest request);

    /**
     * Đăng ký - POST /api/v1/auth/register
     * Backend trả về: { success, message } (cần xác thực email nếu production)
     */
    @POST("api/v1/auth/register")
    Call<ApiResponse<Void>> registerUser(@Body RegisterRequest request);

    /**
     * Đăng xuất - POST /api/v1/auth/logout
     * Yêu cầu token (Bearer), xóa refreshToken trong DB
     */
    @POST("api/v1/auth/logout")
    Call<ApiResponse<Void>> logout();

    /**
     * Lấy thông tin user hiện tại - GET /api/v1/auth/me
     * Yêu cầu token (Bearer)
     */
    @GET("api/v1/auth/me")
    Call<ApiResponse<User>> getMe();

    // ===========================
    // USER PROFILE MODULE
    // ===========================

    /**
     * Lấy thông tin profile - GET /api/v1/users/profile
     * Yêu cầu token (Bearer)
     */
    @GET("api/v1/users/profile")
    Call<ApiResponse<User>> getProfile();

    /**
     * Cập nhật profile - PUT /api/v1/users/profile
     * Yêu cầu token (Bearer)
     * Body: Map để linh hoạt, chỉ gửi field cần cập nhật
     */
    @PUT("api/v1/users/profile")
    Call<ApiResponse<User>> updateProfile(@Body Map<String, String> body);

    // ===========================
    // ADDRESS MODULE
    // ===========================

    /**
     * Lấy danh sách địa chỉ - GET /api/v1/users/addresses
     */
    @GET("api/v1/users/addresses")
    Call<ApiResponse<List<Address>>> getAddresses();

    /**
     * Thêm địa chỉ mới - POST /api/v1/users/addresses
     */
    @POST("api/v1/users/addresses")
    Call<ApiResponse<User>> addAddress(@Body Address address);

    /**
     * Cập nhật địa chỉ - PUT /api/v1/users/addresses/{id}
     */
    @PUT("api/v1/users/addresses/{id}")
    Call<ApiResponse<User>> updateAddress(@Path("id") String addressId, @Body Address address);

    /**
     * Xóa địa chỉ - DELETE /api/v1/users/addresses/{id}
     */
    @DELETE("api/v1/users/addresses/{id}")
    Call<ApiResponse<Void>> deleteAddress(@Path("id") String addressId);

    /**
     * Đặt địa chỉ mặc định - PATCH /api/v1/users/addresses/{id}/set-default
     */
    @PATCH("api/v1/users/addresses/{id}/set-default")
    Call<ApiResponse<User>> setDefaultAddress(@Path("id") String addressId);

    // ===========================
    // PRODUCT MODULE
    // ===========================

    /**
     * Lấy danh sách sản phẩm - GET /api/v1/products?limit=1000
     */
    @GET("api/v1/products?limit=1000")
    Call<ProductResponse> getProducts();

    // ===========================
    // CART MODULE
    // ===========================

    /**
     * Thêm sản phẩm vào giỏ hàng (server) - POST /api/v1/cart/add
     * Body: { productId, quantity, shade }
     * Yêu cầu token (Bearer)
     */
    @POST("api/v1/cart/add")
    Call<Void> addToCartServer(@Body Map<String, Object> body);

    /**
     * Lấy giỏ hàng hiện tại - GET /api/v1/cart
     */
    @GET("api/v1/cart")
    Call<ApiResponse<Void>> getCart();

    // ===========================
    // ORDER MODULE
    // ===========================

    /**
     * Tạo đơn hàng - POST /api/v1/orders/create
     * Yêu cầu token (Bearer)
     * Body: { shippingAddress, paymentMethod }
     */
    @POST("api/v1/orders/create")
    Call<ApiResponse<OrderResponse>> createOrder(@Body CreateOrderRequest request);

    /**
     * Lấy lịch sử đơn hàng - GET /api/v1/orders/my-orders
     */
    @GET("api/v1/orders/my-orders")
    Call<ApiResponse<List<OrderResponse>>> getMyOrders();

    /**
     * Xem chi tiết đơn hàng - GET /api/v1/orders/{id}
     */
    @GET("api/v1/orders/{id}")
    Call<ApiResponse<OrderResponse>> getOrderById(@Path("id") String orderId);
}