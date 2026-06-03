package com.example.tirtir_mcommerce.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.example.tirtir_mcommerce.model.CreateOrderRequest;
import com.example.tirtir_mcommerce.model.OrderResponse;
import com.example.tirtir_mcommerce.model.ShippingAddress;
import com.example.tirtir_mcommerce.repository.OrderRepository;

import java.util.List;

/**
 * OrderViewModel — MVVM layer cho đặt hàng và PDF invoice.
 *
 * LiveData:
 * - orderSuccess: OrderResponse khi đặt hàng thành công
 * - myOrders: danh sách đơn hàng
 * - isLoading: trạng thái đang xử lý
 * - errorMessage: thông báo lỗi
 * - pdfDownloadId: ID từ DownloadManager để track tải PDF
 *
 * Sprint 1.3 — Task C: Đặt hàng + PDF
 */
public class OrderViewModel extends AndroidViewModel {

    private final OrderRepository orderRepository;

    public final MutableLiveData<OrderResponse> orderSuccess = new MutableLiveData<>();
    public final MutableLiveData<List<OrderResponse>> myOrders = new MutableLiveData<>();
    public final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    public final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    public final MutableLiveData<Long> pdfDownloadId = new MutableLiveData<>();

    public OrderViewModel(@NonNull Application application) {
        super(application);
        orderRepository = new OrderRepository(application.getApplicationContext());
    }

    // ===========================
    // PLACE ORDER
    // ===========================

    /**
     * Đặt hàng từ giỏ hàng hiện tại.
     *
     * @param fullName      Tên người nhận
     * @param phone         Số điện thoại
     * @param address       Địa chỉ
     * @param city          Thành phố
     * @param paymentMethod VNPAY | MOMO | CARD
     */
    public void placeOrder(String fullName, String phone, String address,
                           String city, String paymentMethod) {
        isLoading.setValue(true);

        ShippingAddress shippingAddress = new ShippingAddress(fullName, phone, address, city);
        CreateOrderRequest request = new CreateOrderRequest(shippingAddress, paymentMethod);

        orderRepository.placeOrder(request,
                order -> {
                    isLoading.postValue(false);
                    orderSuccess.postValue(order);

                    // Tự động bắt đầu tải PDF nếu backend trả về invoiceUrl
                    if (order.getInvoiceUrl() != null && !order.getInvoiceUrl().isEmpty()) {
                        downloadInvoice(order.getId(), order.getInvoiceUrl());
                    }
                },
                error -> {
                    isLoading.postValue(false);
                    errorMessage.postValue(error);
                }
        );
    }

    // ===========================
    // GET ORDER HISTORY
    // ===========================

    /** Lấy lịch sử đơn hàng của user. */
    public void loadMyOrders() {
        isLoading.setValue(true);
        orderRepository.getMyOrders(
                orders -> {
                    isLoading.postValue(false);
                    myOrders.postValue(orders);
                },
                error -> {
                    isLoading.postValue(false);
                    errorMessage.postValue(error);
                }
        );
    }

    // ===========================
    // PDF DOWNLOAD
    // ===========================

    /**
     * Tải hóa đơn PDF bằng DownloadManager.
     * downloadId được post qua LiveData để Fragment có thể track.
     *
     * @param orderId    ID đơn hàng
     * @param invoiceUrl URL PDF (optional)
     */
    public void downloadInvoice(String orderId, String invoiceUrl) {
        long downloadId = orderRepository.downloadInvoicePdf(orderId, invoiceUrl);
        if (downloadId != -1) {
            pdfDownloadId.postValue(downloadId);
        } else {
            errorMessage.postValue("Không thể tải hóa đơn PDF");
        }
    }
}
