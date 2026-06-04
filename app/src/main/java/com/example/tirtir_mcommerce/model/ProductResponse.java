package com.example.tirtir_mcommerce.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Response model for GET /api/v1/products
 *
 * API response shape:
 * {
 *   "total": 25,
 *   "page": 1,
 *   "limit": 1000,
 *   "data": [ { Product }, ... ],
 *   "categories": [ { "name": "skincare", "count": 10 }, ... ],
 *   "concerns": [ { "name": "Hydration", "count": 3 }, ... ],
 *   "skinTypes": [ { "name": "All Skin Types", "count": 5 }, ... ]
 * }
 *
 * Sprint 1.2 — Task A
 */
public class ProductResponse {

    @SerializedName("total")
    private int total;

    @SerializedName("page")
    private int page;

    @SerializedName("limit")
    private int limit;

    @SerializedName("data")
    private List<Product> data;

    /**
     * Danh mục từ API, dùng để build chip filter động.
     * Item: { "name": "skincare", "count": 10 }
     */
    @SerializedName("categories")
    private List<CategoryItem> categories;

    // ===========================
    // GETTERS / SETTERS
    // ===========================

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }

    public List<Product> getData() { return data; }
    public void setData(List<Product> data) { this.data = data; }

    public List<CategoryItem> getCategories() { return categories; }
    public void setCategories(List<CategoryItem> categories) { this.categories = categories; }

    // ===========================
    // INNER CLASS
    // ===========================

    /**
     * Đại diện một mục trong mảng categories/concerns/skinTypes trả về từ API.
     * { "name": "skincare", "count": 10 }
     */
    public static class CategoryItem {
        @SerializedName("name")
        private String name;

        @SerializedName("count")
        private int count;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }
}