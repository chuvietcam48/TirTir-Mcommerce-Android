package com.example.tirtir_mcommerce.model;

import java.util.List;

public class Product {
    private String id;                    // Product_ID
    private String name;
    private String brand;                 // mặc định "TIRTIR"
    private String category;
    private double price;
    private double salePrice;
    private String volumeSize;
    private boolean isSkincare;
    private String skinTypeTarget;
    private String mainConcern;
    private List<String> keyIngredients;  // tách từ Key_Ingredients nếu cần
    private String descriptionShort;
    private String fullDescription;
    private List<String> images;          // Gallery_Images
    private String thumbnail;
    private int stock;
    private boolean isActive;

    // Constructor rỗng bắt buộc cho Firestore
    public Product() {}

    // Getter & Setter (bạn có thể generate bằng Alt + Insert)
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBrand() { return brand != null ? brand : "TIRTIR"; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public double getSalePrice() { return salePrice; }
    public void setSalePrice(double salePrice) { this.salePrice = salePrice; }

    public String getVolumeSize() { return volumeSize; }
    public void setVolumeSize(String volumeSize) { this.volumeSize = volumeSize; }

    public boolean isSkincare() { return isSkincare; }
    public void setSkincare(boolean skincare) { isSkincare = skincare; }

    public String getSkinTypeTarget() { return skinTypeTarget; }
    public void setSkinTypeTarget(String skinTypeTarget) { this.skinTypeTarget = skinTypeTarget; }

    public String getMainConcern() { return mainConcern; }
    public void setMainConcern(String mainConcern) { this.mainConcern = mainConcern; }

    public String getDescriptionShort() { return descriptionShort; }
    public void setDescriptionShort(String descriptionShort) { this.descriptionShort = descriptionShort; }

    public String getFullDescription() { return fullDescription; }
    public void setFullDescription(String fullDescription) { this.fullDescription = fullDescription; }

    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }

    public String getThumbnail() { return thumbnail; }
    public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
}