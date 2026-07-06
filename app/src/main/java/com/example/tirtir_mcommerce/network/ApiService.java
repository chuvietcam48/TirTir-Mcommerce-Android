package com.example.tirtir_mcommerce.network;

import com.example.tirtir_mcommerce.model.Address;
import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.model.ArbitrateOrderRequest;
import com.example.tirtir_mcommerce.model.ArbitrateOrderResponse;
import com.example.tirtir_mcommerce.model.CartItem;
import com.example.tirtir_mcommerce.model.CreateOrderRequest;
import com.example.tirtir_mcommerce.model.CreateOrderResponse;
import com.example.tirtir_mcommerce.model.LoginRequest;
import com.example.tirtir_mcommerce.model.LoginResponse;
import com.example.tirtir_mcommerce.model.OrderResponse;
import com.example.tirtir_mcommerce.model.MarketingOverviewResponse;
import com.example.tirtir_mcommerce.model.Product;
import com.example.tirtir_mcommerce.model.RetentionStatsResponse;
import com.example.tirtir_mcommerce.model.ChurnUser;
import com.example.tirtir_mcommerce.model.ProductDetailResponse;
import com.example.tirtir_mcommerce.model.ProductResponse;
import com.example.tirtir_mcommerce.model.RegisterRequest;
import com.example.tirtir_mcommerce.model.RegisterResponse;
import com.example.tirtir_mcommerce.model.RefreshTokenRequest;
import com.example.tirtir_mcommerce.model.RefreshTokenResponse;
import com.example.tirtir_mcommerce.model.RoutineRecommendRequest;
import com.example.tirtir_mcommerce.model.ShadeMatchRequest;
import com.example.tirtir_mcommerce.model.ShadeMatchResult;
import com.example.tirtir_mcommerce.model.User;
import com.example.tirtir_mcommerce.model.FcmTokenRequest;
import com.example.tirtir_mcommerce.model.SkinAnalysisResult;
import com.example.tirtir_mcommerce.model.RoutineStep;

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Multipart;
import retrofit2.http.Part;

/**
 * Interface định nghĩa tất cả các API endpoint của hệ thống TirTir.
 * Retrofit sẽ tự động sinh ra implementation từ interface này.
 *
 * Phân nhóm theo module:
 * - AUTH: Đăng nhập, đăng ký, quản lý phiên đăng nhập
 * - USER PROFILE: Thông tin cá nhân, địa chỉ
 * - PRODUCT: Danh sách, chi tiết sản phẩm
 * - CART: Giỏ hàng (offline + server sync)
 * - ORDER: Đặt hàng, lịch sử, hóa đơn
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
    Call<RegisterResponse> registerUser(@Body RegisterRequest request);

    @POST("api/v1/auth/refresh")
    Call<RefreshTokenResponse> refreshToken(@Body RefreshTokenRequest request);

    @POST("api/v1/auth/forgot-password")
    Call<ApiResponse<Void>> forgotPassword(@Body Map<String, String> body);

    @POST("api/v1/auth/verify-otp")
    Call<ApiResponse<String>> verifyOTP(@Body Map<String, String> body);

    @POST("api/v1/auth/reset-password")
    Call<ApiResponse<Void>> resetPassword(@Body Map<String, String> body);

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
    Call<ApiResponse<List<Address>>> addAddress(@Body Address address);

    /**
     * Cập nhật địa chỉ - PUT /api/v1/users/addresses/{id}
     */
    @PUT("api/v1/users/addresses/{id}")
    Call<ApiResponse<List<Address>>> updateAddress(@Path("id") String addressId, @Body Address address);

    /**
     * Xóa địa chỉ - DELETE /api/v1/users/addresses/{id}
     */
    @DELETE("api/v1/users/addresses/{id}")
    Call<ApiResponse<List<Address>>> deleteAddress(@Path("id") String addressId);

    /**
     * Đặt địa chỉ mặc định - PATCH /api/v1/users/addresses/{id}/set-default
     */
    @PATCH("api/v1/users/addresses/{id}/set-default")
    Call<ApiResponse<List<Address>>> setDefaultAddress(@Path("id") String addressId);

    // ===========================
    // SHIPPING MODULE
    // ===========================

    @GET("api/shipping/locations/provinces")
    Call<Map<String, Object>> getProvinces();

    @GET("api/shipping/locations/districts")
    Call<Map<String, Object>> getDistricts(@Query("provinceId") String provinceId);

    @GET("api/shipping/locations/wards")
    Call<Map<String, Object>> getWards(@Query("districtId") String districtId);

    @GET("api/v1/loyalty/me")
    Call<ApiResponse<Map<String, Object>>> getLoyaltyDetails();

    @GET("api/v1/chat/config")
    Call<ApiResponse<Map<String, Object>>> getChatConfig();

    @GET("api/v1/chat/suggested-questions")
    Call<ApiResponse<List<Map<String, Object>>>> getChatSuggestedQuestions();

    @GET("api/v1/chat/history")
    Call<ApiResponse<List<Map<String, Object>>>> getChatHistory();

    @POST("api/v1/chat/handoff")
    Call<ApiResponse<Map<String, Object>>> postChatHandoff(@Body Map<String, Object> body);

    @GET("api/v1/chat/categories")
    Call<ApiResponse<List<Map<String, Object>>>> getChatCategories(@Query("parentId") String parentId);

    @POST("api/v1/ai/analyze-face")
    Call<ApiResponse<Map<String, Object>>> analyzeSkin(@Body Map<String, String> body);

    @Multipart
    @POST("/analyze")
    Call<SkinAnalysisResult> analyzeSkinPython(@Part MultipartBody.Part image);

    @GET("api/v1/products/cushion-match")
    Call<ApiResponse<List<ShadeMatchResult>>> matchCushion(@Query("skin_tone_hex") String skinToneHex);

    @POST("api/ingredients/check")
    Call<ApiResponse<Map<String, Object>>> checkIngredients(@Body Map<String, String> body);

    /**
     * Tìm sản phẩm nền phù hợp theo màu da - POST /api/v1/shades/match
     * Body: { r, g, b, skinType }
     * Response: danh sách sản phẩm + matchScore (Delta-E distance)
     */
    @POST("api/v1/shades/match")
    Call<ApiResponse<List<ShadeMatchResult>>> matchShade(@Body ShadeMatchRequest request);

    /**
     * Giao đề xuất chu trình dưỡng da - POST /api/ai/recommend-routine
     * Body: { skinType, skinTone, undertone, concerns[], shadeMatchProduct }
     * Response: danh sách các bước skincare AI đề xuất
     */
    @POST("api/v1/ai/recommend-routine")
    Call<ApiResponse<Map<String, Object>>> recommendRoutine(@Body RoutineRecommendRequest request);

    @GET("api/v1/ai/latest-profile")
    Call<ApiResponse<Map<String, Object>>> getLatestSkinProfile();

    @GET("api/routines/{id}")
    Call<ApiResponse<List<RoutineStep>>> getCommunityRoutine(@Path("id") String id);

    @POST("api/v1/routines/save")
    Call<ApiResponse<Map<String, Object>>> saveRoutine(@Body Map<String, Object> body);

    @GET("api/v1/routines/recommendation")
    Call<ApiResponse<Map<String, Object>>> getRoutineRecommendation();

    /** Authoritative checkout: shipping (Viettel SOAP) + tax + voucher + VNPAY URL */
    @POST("api/v1/payments/arbitrate")
    Call<ApiResponse<ArbitrateOrderResponse>> arbitrateOrder(@Body ArbitrateOrderRequest request);

    @GET("api/v1/ingredient/scan-history")
    Call<Map<String, Object>> getIngredientHistory(@Query("userId") String userId);

    // ===========================
    // PRODUCT MODULE
    // ===========================

     /**
      * Lấy danh sách sản phẩm - GET /api/v1/products?limit={limit}
      * API trả về: { total, page, limit, data[], categories[], concerns[], skinTypes[] }
      * Mặc định limit=1000 để load toàn bộ, filter client-side.
      */
    @GET("api/v1/products")
    Call<ProductResponse> getProducts(@retrofit2.http.Query("limit") int limit, @retrofit2.http.Query("_t") long timestamp);

    /**
     * Lấy sản phẩm theo category - GET /api/v1/products?limit={limit}&category={category}
     * Dùng khi muốn filter server-side (tùy backend hỗ trợ).
     */
    @GET("api/v1/products")
    Call<ProductResponse> getProductsByCategory(
            @retrofit2.http.Query("limit") int limit,
            @retrofit2.http.Query("category") String category);

    /**
     * Lấy sản phẩm theo category - GET /api/v1/products?category=...&limit=...
     */
    @GET("api/v1/products")
    Call<ProductResponse> getProductsByCategory(@Query("category") String category,
                                                 @Query("limit") int limit);

    /**
     * Lấy chi tiết sản phẩm - GET /api/v1/products/{id}
     */
    @GET("api/v1/products/{id}")
    Call<ProductDetailResponse> getProductById(@Path("id") String productId);

    @GET("api/v1/shades")
    Call<List<Map<String, Object>>> getShades(
            @Query("productId") String productId,
            @Query("parentId") String parentId,
            @Query("limit") int limit);

    @GET("api/v1/products/{id}/reviews")
    Call<Map<String, Object>> getProductReviews(
            @Path("id") String productId,
            @Query("page") int page,
            @Query("limit") int limit);

    // ===========================
    // ADMIN MODULE
    // ===========================

    @GET("api/v1/admin/vouchers/stats")
    Call<ApiResponse<com.example.tirtir_mcommerce.model.AdminVoucherStats>> getAdminVoucherStats();

    @GET("api/v1/admin/vouchers")
    Call<com.example.tirtir_mcommerce.model.AdminVouchersResponse> getAdminVouchers();

    @GET("api/v1/admin/vouchers/{id}")
    Call<ApiResponse<com.example.tirtir_mcommerce.model.AdminVoucher>> getAdminVoucherById(@Path("id") String id);

    @POST("api/v1/admin/vouchers")
    Call<ApiResponse<com.example.tirtir_mcommerce.model.AdminVoucher>> createAdminVoucher(@Body java.util.Map<String, Object> body);

    @PUT("api/v1/admin/vouchers/{id}")
    Call<ApiResponse<com.example.tirtir_mcommerce.model.AdminVoucher>> updateAdminVoucher(@Path("id") String id, @Body java.util.Map<String, Object> body);

    @DELETE("api/v1/admin/vouchers/{id}")
    Call<ApiResponse<Object>> deleteAdminVoucher(@Path("id") String id);


    @retrofit2.http.Multipart
    @POST("api/v1/admin/products")
    Call<Product> createProduct(
            @retrofit2.http.Part okhttp3.MultipartBody.Part thumbnail,
            @retrofit2.http.PartMap Map<String, okhttp3.RequestBody> data
    );

    @retrofit2.http.Multipart
    @PUT("api/v1/admin/products/{id}")
    Call<Product> updateProduct(
            @Path("id") String productId,
            @retrofit2.http.Part okhttp3.MultipartBody.Part thumbnail,
            @retrofit2.http.PartMap Map<String, okhttp3.RequestBody> data
    );

    @DELETE("api/v1/admin/products/{id}")
    Call<Map<String, Object>> deleteProduct(@Path("id") String productId);

    @PATCH("api/v1/admin/products/{id}/toggle-active")
    Call<Map<String, Object>> toggleProductActive(@Path("id") String productId);

    // Metrics for Dashboard
    @GET("api/v1/admin/metrics")
    Call<Map<String, Object>> getAdminMetrics(@Query("range") String range);

    @GET("api/v1/admin/dashboard/top-products")
    Call<List<Map<String, Object>>> getTopProducts();

    @GET("api/v1/admin/dashboard/overview")
    Call<Map<String, Object>> getAdminOverview(@Query("range") String range);

    @GET("api/v1/admin/orders")
    Call<List<Map<String, Object>>> getAdminOrders(@Query("limit") int limit);

    @PATCH("api/v1/orders/{id}/status")
    Call<Map<String, Object>> updateAdminOrderStatus(
            @Path("id") String orderId,
            @Body Map<String, String> body);

    @GET("api/v1/admin/orders/{id}")
    Call<ApiResponse<Map<String, Object>>> getAdminOrderDetails(@Path("id") String orderId);

    @PATCH("api/v1/admin/orders/{id}/shipping")
    Call<ApiResponse<Map<String, Object>>> updateAdminOrderShipping(@Path("id") String orderId, @Body Map<String, Object> body);

    @PATCH("api/v1/admin/orders/{id}/notes")
    Call<ApiResponse<Map<String, Object>>> updateAdminOrderNotes(@Path("id") String orderId, @Body Map<String, Object> body);

    @POST("api/v1/admin/orders/{id}/cancel")
    Call<ApiResponse<Map<String, Object>>> cancelAdminOrder(@Path("id") String orderId, @Body Map<String, Object> body);

    // ===========================
    // ADMIN SETTINGS MODULE
    // ===========================
    @PUT("api/v1/admin/settings/preferences")
    Call<ApiResponse<Map<String, Object>>> updateAdminPreferences(@Body Map<String, Object> body);

    @PUT("api/v1/admin/settings/change-password")
    Call<ApiResponse<Map<String, Object>>> changeAdminPassword(@Body Map<String, String> body);

    @GET("api/v1/admin/settings/login-history")
    Call<ApiResponse<List<Map<String, Object>>>> getAdminLoginHistory();

    @GET("api/v1/admin/settings/api-logs")
    Call<ApiResponse<List<Map<String, Object>>>> getAdminApiLogs();

    @GET("api/v1/admin/settings/audit-trails")
    Call<ApiResponse<List<Map<String, Object>>>> getAdminAuditTrails();

    @GET("api/v1/admin/stats/cart-recovery")
    Call<Map<String, Object>> getCartRecoveryStats();

    @GET("api/v1/admin/marketing/overview")
    Call<ApiResponse<MarketingOverviewResponse>> getMarketingOverview();

    @POST("api/v1/admin/marketing/campaigns")
    Call<ApiResponse<Map<String, Object>>> sendFlashSale(@Body Map<String, String> body);

    @GET("api/v1/admin/retention/stats")
    Call<ApiResponse<RetentionStatsResponse>> getRetentionAnalytics();

    @GET("api/v1/admin/retention/users")
    Call<ApiResponse<List<ChurnUser>>> getAtRiskUsers();

    @POST("api/v1/marketing/abandoned-cart-recovery")
    Call<ApiResponse<Map<String, Object>>> triggerCartRecovery();

    @POST("api/v1/marketing/win-back")
    Call<ApiResponse<Map<String, Object>>> sendWinBackPush();

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
    Call<ApiResponse<List<CartItem>>> getCart();

    @PUT("api/v1/cart/update")
    Call<Void> updateCartServer(@Body Map<String, Object> body);

    @DELETE("api/v1/cart/clear")
    Call<Void> clearCartServer();

    @POST("api/v1/cart/sync")
    Call<ApiResponse<List<CartItem>>> syncCart(@Body Map<String, Object> body);

    // ===========================
    // ORDER MODULE
    // ===========================

    /**
     * Tạo đơn hàng - POST /api/v1/orders/create
     * Yêu cầu token (Bearer)
     * Body: { shippingAddress, paymentMethod }
     * Backend tự lấy cart của user qua JWT
     * Dùng cho SCR-16 CheckoutActivity
     */
    @POST("api/v1/orders/create")
    Call<CreateOrderResponse> createOrder(@Body CreateOrderRequest request);

    /**
     * Lấy lịch sử đơn hàng - GET /api/v1/orders/my-orders
     * Yêu cầu token (Bearer)
     * Dùng cho SCR-18 OrderHistoryFragment
     */
    @GET("api/v1/orders/my-orders")
    Call<ApiResponse<List<OrderResponse>>> getMyOrders();

    /**
     * Xem chi tiết đơn hàng - GET /api/v1/orders/{id}
     * Yêu cầu token (Bearer)
     * Dùng cho SCR-17 OrderSuccessActivity
     */
    @GET("api/v1/orders/{id}")
    Call<OrderResponse> getOrderById(@Path("id") String orderId);

    /**
     * Đăng ký FCM Token lên backend - POST /api/v1/notifications/fcm-token
     */
    @POST("api/v1/notifications/fcm-token")
    Call<ApiResponse<Object>> registerFcmToken(@Body FcmTokenRequest request);

    @GET("api/v1/notifications")
    Call<ApiResponse<List<Map<String, Object>>>> getNotifications();

    @PUT("api/v1/notifications/read-all")
    Call<ApiResponse<Void>> markAllNotificationsRead();

    @PUT("api/v1/notifications/{id}/read")
    Call<ApiResponse<Map<String, Object>>> markNotificationRead(@Path("id") String notificationId);

    @DELETE("api/v1/notifications/{id}")
    Call<ApiResponse<Void>> deleteNotification(@Path("id") String notificationId);

    @POST("api/users/device-token")
    Call<ApiResponse<Object>> updateDeviceToken(@Body Map<String, String> body);

    @GET("api/v1/admin/churn")
    Call<ApiResponse<List<com.example.tirtir_mcommerce.model.ChurnResponseItem>>> getChurnList();

    @POST("api/v1/admin/churn/send-voucher")
    Call<ApiResponse<Object>> sendVoucher(@Body Map<String, Object> body);

    @POST("api/v1/admin/notifications/send-voucher-fcm")
    Call<ApiResponse<Object>> sendVoucherFcm(@Body Map<String, Object> body);

    @POST("api/v1/loyalty/scan")
    Call<ApiResponse<Map<String, Object>>> scanBarcode(@Body Map<String, String> body);

    @POST("api/v1/loyalty/redeem")
    Call<ApiResponse<Map<String, Object>>> redeemPoints(@Body Map<String, Integer> body);

    @GET("api/v1/loyalty/wallet")
    Call<ApiResponse<List<Map<String, Object>>>> getWallet();

    @POST("api/v1/auth/google-login")
    Call<LoginResponse> googleLogin(@Body Map<String, String> body);

    @POST("api/v1/chatbot/message")
    Call<Map<String, Object>> getChatbotMessage(@Body Map<String, Object> body);

    @POST("api/v1/users/fcm-token")
    Call<ApiResponse<Object>> registerUserFcmToken(@Body Map<String, Object> body);

    @GET("api/v1/routines/community")
    Call<ApiResponse<List<Map<String, Object>>>> getCommunityRoutines();

    @POST("api/v1/routines/{id}/like")
    Call<ApiResponse<Map<String, Object>>> likeRoutine(@Path("id") String id);

    @POST("api/v1/routines/{id}/apply")
    Call<ApiResponse<Map<String, Object>>> applyRoutine(@Path("id") String id);

    @GET("api/v1/routine/suggest")
    Call<ApiResponse<Map<String, Object>>> suggestRoutine(@Query("userId") String userId, @Query("missingStep") String missingStep);

    @POST("api/v1/loyalty/claim-welcome")
    Call<ApiResponse<Map<String, Object>>> claimWelcomeVoucher();
}
