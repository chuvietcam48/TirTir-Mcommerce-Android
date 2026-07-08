package com.example.tirtir_mcommerce.repository;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.example.tirtir_mcommerce.model.ApiResponse;
import com.example.tirtir_mcommerce.model.CreateOrderRequest;
import com.example.tirtir_mcommerce.model.CreateOrderResponse;
import com.example.tirtir_mcommerce.model.OrderResponse;
import com.example.tirtir_mcommerce.network.ApiService;
import com.example.tirtir_mcommerce.network.ApiConfig;
import com.example.tirtir_mcommerce.network.RetrofitClient;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;
import com.example.tirtir_mcommerce.utils.SharedPrefsManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * OrderRepository — xử lý nghiệp vụ đặt hàng và PDF invoice.
 *
 * Chức năng:
 * 1. placeOrder() — POST /api/v1/orders/create
 * 2. getMyOrders() — GET /api/v1/orders/my-orders
 * 3. downloadInvoicePdf() — dùng Android DownloadManager tải PDF về máy
 *
 * Android Components (cho Báo cáo):
 * - DownloadManager: Service hệ thống tải file ở nền, progress hiển thị trong notification bar
 *
 * Sprint 1.3 — Task C: Đặt hàng + PDF
 */
public class OrderRepository {

    private static final String TAG = "OrderRepository";
    private final Context context;

    public OrderRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    // ===========================
    // PLACE ORDER
    // ===========================

    /**
     * Tạo đơn hàng mới từ giỏ hàng hiện tại.
     * Backend sẽ tự lấy cart của user qua JWT token.
     *
     * @param request   Địa chỉ giao hàng + phương thức thanh toán
     * @param onSuccess Callback nhận OrderResponse (có orderId)
     * @param onError   Callback nhận thông báo lỗi
     */
    public void placeOrder(CreateOrderRequest request,
                           Consumer<CreateOrderResponse> onSuccess,
                           Consumer<String> onError) {
        if (request == null || request.getShippingAddress() == null
                || request.getPaymentMethod() == null || request.getPaymentMethod().trim().isEmpty()) {
            if (onError != null) onError.accept("Please review your delivery and payment details.");
            return;
        }

        ApiService apiService = RetrofitClient.getAuthClient(context).create(ApiService.class);

        apiService.createOrder(request).enqueue(new Callback<CreateOrderResponse>() {
            @Override
            public void onResponse(Call<CreateOrderResponse> call,
                                   Response<CreateOrderResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().getOrderId() != null) {
                        Log.d(TAG, "Order placed: " + response.body().getOrderId());
                        if (onSuccess != null) onSuccess.accept(response.body());
                    } else {
                        if (onError != null) {
                            onError.accept("Your order could not be confirmed. Please try again.");
                        }
                    }
                } else {
                    Log.e(TAG, "Order API failed with HTTP " + response.code());
                    String message;
                    if (response.code() == 401 || response.code() == 403) {
                        message = "Your session has expired. Please sign in again.";
                    } else if (response.code() == 400 || response.code() == 422) {
                        message = "Please review your cart and delivery details.";
                    } else if (response.code() == 409) {
                        message = "One or more items are no longer available.";
                    } else {
                        message = "We could not place your order right now. Please try again.";
                    }
                    if (onError != null) onError.accept(message);
                }
            }

            @Override
            public void onFailure(Call<CreateOrderResponse> call, Throwable t) {
                Log.e(TAG, "Order network failure", t);
                if (onError != null) {
                    onError.accept("Check your connection and try placing the order again.");
                }
            }
        });
    }

    // ===========================
    // GET ORDERS HISTORY
    // ===========================

    public void getMyOrders(Consumer<List<OrderResponse>> onSuccess, Consumer<String> onError) {
        ApiService apiService = RetrofitClient.getAuthClient(context).create(ApiService.class);

        apiService.getMyOrders().enqueue(new Callback<ApiResponse<List<java.util.Map<String, Object>>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<java.util.Map<String, Object>>>> call,
                                   Response<ApiResponse<List<java.util.Map<String, Object>>>> response) {
                List<OrderResponse> merged = new ArrayList<>(new SharedPrefsManager(context).getLocalOrders());
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    List<java.util.Map<String, Object>> remoteMaps = response.body().getData();
                    List<OrderResponse> remote = new ArrayList<>();
                    for (java.util.Map<String, Object> map : remoteMaps) {
                        remote.add(mapToOrderResponse(map));
                    }
                    for (OrderResponse r : remote) {
                        boolean exists = false;
                        for (OrderResponse l : merged) {
                            if (l.getId() != null && l.getId().equals(r.getId())) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            merged.add(r);
                        }
                    }
                    onSuccess.accept(merged);
                } else {
                    if (!merged.isEmpty()) {
                        onSuccess.accept(merged);
                    } else {
                        onError.accept("Unable to load order history.");
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<java.util.Map<String, Object>>>> call, Throwable t) {
                List<OrderResponse> local = new SharedPrefsManager(context).getLocalOrders();
                if (!local.isEmpty()) {
                    onSuccess.accept(local);
                } else {
                    onError.accept("Connection error. Please try again.");
                }
            }
        });
    }

    private OrderResponse mapToOrderResponse(java.util.Map<String, Object> map) {
        OrderResponse o = new OrderResponse();
        o.setId(String.valueOf(map.get("_id")));
        o.setStatus(String.valueOf(map.get("status")));
        o.setCreatedAt(String.valueOf(map.get("createdAt")));
        o.setPaymentMethod(String.valueOf(map.get("paymentMethod")));
        o.setInvoiceUrl(String.valueOf(map.get("invoiceUrl")));
        
        Object total = map.get("totalPrice");
        if (total == null) total = map.get("totalAmount");
        if (total instanceof Number) o.setTotalPrice(((Number) total).doubleValue());
        else if (total instanceof String) {
            try { o.setTotalPrice(Double.parseDouble((String) total)); } catch (Exception ignored) {}
        }
        
        Object paid = map.get("isPaid");
        if (paid instanceof Boolean) o.setPaid((Boolean) paid);
        
        return o;
    }

    // ===========================
    // DOWNLOAD PDF INVOICE
    // ===========================

    /**
     * Tải hóa đơn PDF về máy bằng DownloadManager.
     *
     * DownloadManager là Android System Service:
     * - Tải file ở background (không block UI)
     * - Hiển thị progress trong notification bar
     * - File được lưu vào thư mục Downloads
     *
     * URL PDF: nếu backend trả về invoiceUrl → dùng trực tiếp
     *          nếu không → tự build từ orderId
     *
     * @param orderId    ID đơn hàng
     * @param invoiceUrl URL PDF (có thể null — sẽ build từ orderId)
     * @return downloadId từ DownloadManager để track tiến trình
     */
    public long downloadInvoicePdf(String orderId, String invoiceUrl) {
        // Build PDF URL: ưu tiên invoiceUrl từ response, fallback orderId pattern
        String pdfUrl;
        if (invoiceUrl != null && !invoiceUrl.isEmpty()) {
            pdfUrl = invoiceUrl;
        } else {
            // Pattern URL tự build — cần điều chỉnh theo backend thực tế
            pdfUrl = ApiConfig.BASE_URL + "api/v1/orders/" + orderId + "/invoice";
        }

        String fileName = "TirTir_Invoice_" + orderId.substring(Math.max(0, orderId.length() - 8)) + ".pdf";

        Log.d(TAG, "Downloading PDF from: " + pdfUrl);

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(pdfUrl))
                .setTitle("TirTir invoice")
                .setDescription("Downloading your order invoice...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setMimeType("application/pdf")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true);

        DownloadManager downloadManager =
                (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

        if (downloadManager == null) {
            Log.e(TAG, "DownloadManager not available");
            return -1;
        }

        long downloadId = downloadManager.enqueue(request);
        Log.d(TAG, "PDF download started with ID: " + downloadId);
        return downloadId;
    }
}
