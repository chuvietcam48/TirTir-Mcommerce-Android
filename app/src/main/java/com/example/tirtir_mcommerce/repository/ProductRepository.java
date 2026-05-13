package com.example.tirtir_mcommerce.repository;

import com.example.tirtir_mcommerce.model.Product;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import java.util.List;

public class ProductRepository {

    private final FirebaseFirestore db;

    public ProductRepository() {
        db = FirebaseFirestore.getInstance();
    }

    // Lấy tất cả sản phẩm
    public void getAllProducts(ProductCallback callback) {
        db.collection("products")
                .whereEqualTo("isActive", true)
                .orderBy("name")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Product> products = queryDocumentSnapshots.toObjects(Product.class);
                    callback.onSuccess(products);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // Callback interface
    public interface ProductCallback {
        void onSuccess(List<Product> products);
        void onFailure(String error);
    }
}