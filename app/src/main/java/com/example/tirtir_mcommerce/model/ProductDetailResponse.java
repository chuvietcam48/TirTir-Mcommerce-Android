package com.example.tirtir_mcommerce.model;

import com.google.gson.annotations.SerializedName;

/**
 * Accepts both product-detail contracts that currently exist in the TirTir
 * backends: a direct product object and { success, data: product }.
 */
public class ProductDetailResponse extends Product {
    @SerializedName("data")
    private Product data;

    public Product getProduct() {
        return data != null ? data : this;
    }
}
