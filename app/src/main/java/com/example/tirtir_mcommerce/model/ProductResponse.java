package com.example.tirtir_mcommerce.model;

import java.util.List;

public class ProductResponse {
    // Chữ "data" này phải gõ y chang cái chữ "data" trên web của bạn
    private List<Product> data;

    public List<Product> getData() {
        return data;
    }

    public void setData(List<Product> data) {
        this.data = data;
    }
}