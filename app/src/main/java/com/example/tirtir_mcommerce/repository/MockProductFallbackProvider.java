package com.example.tirtir_mcommerce.repository;

import com.example.tirtir_mcommerce.model.Product;

import java.util.ArrayList;
import java.util.List;

/**
 * MockProductFallbackProvider — Nhà cung cấp dữ liệu sản phẩm giả (LAST RESORT ONLY).
 *
 * ⚠️ QUAN TRỌNG: Class này CHỈ được dùng khi:
 *    1. API backend không phản hồi (Render cold start quá dài hoặc offline)
 *    2. SQLite cache chưa có dữ liệu
 *
 * Không dùng class này làm nguồn sự thật thay cho MongoDB.
 * Dữ liệu ở đây là placeholder để app không bị trắng màn hình hoàn toàn.
 *
 * TODO: Remove hoặc disable trong production khi backend ổn định.
 *
 * Sprint 1.2 — Offline Demo Fallback
 */
public class MockProductFallbackProvider {

    // Price field: API trả về số nguyên (ví dụ: 10 = $10, 45 = $45 USD / hoặc đơn vị backend định nghĩa)
    // Khi hiển thị, dùng NumberFormat VND locale nhưng KHÔNG nhân hệ số
    // Xem thêm: ProductAdapter.buildDisplayPrice()

    private MockProductFallbackProvider() {
        // Utility class — không instantiate
    }

    /**
     * Trả về danh sách sản phẩm mock (chỉ dùng khi API + cache đều thất bại).
     * Cấu trúc khớp với API response để UI không cần thay đổi.
     */
    public static List<Product> getMockProducts() {
        // TODO: Remove or disable when backend is stable
        List<Product> mockList = new ArrayList<>();

        // Mock #1 — khớp với API response field names
        Product p1 = new Product();
        p1.setId("mock-001");
        p1.setProductId("mock-001");
        p1.setName("[DEMO] Matcha Calming Toner");
        p1.setCategory("Skincare");
        // Price: xem ProductAdapter.buildDisplayPrice() — không nhân hệ số
        p1.setPrice(35);
        p1.setSalePrice(0);
        p1.setStockQuantity(50);
        p1.setSkinTypeTarget("All Skin Types");
        p1.setDescriptionShort("Lightweight calming toner with 10,000 ppm Matcha-PDRN.");
        p1.setKeyIngredients("Matcha-PDRN, Soymilk Complex");
        // Thumbnail: để trống → ProductAdapter sẽ dùng placeholder
        p1.setThumbnailImages("");
        mockList.add(p1);

        // Mock #2
        Product p2 = new Product();
        p2.setId("mock-002");
        p2.setProductId("mock-002");
        p2.setName("[DEMO] Mask Fit Cushion");
        p2.setCategory("Makeup");
        p2.setPrice(28);
        p2.setSalePrice(0);
        p2.setStockQuantity(100);
        p2.setSkinTypeTarget("All Skin Types");
        p2.setDescriptionShort("High coverage cushion foundation with skin-fit formula.");
        p2.setKeyIngredients("SPF50, PA++++");
        p2.setThumbnailImages("");
        mockList.add(p2);

        return mockList;
    }
}
