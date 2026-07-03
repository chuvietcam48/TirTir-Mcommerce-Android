package com.example.tirtir_mcommerce.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class Product {

    @SerializedName("_id")
    private String id; // Lưu ý: API Node.js phải làm phẳng "$oid" thành chuỗi "696b48..." nhé!

    @SerializedName("Product_ID")
    private String productId;

    @SerializedName("Parent_ID")
    private String parentId;

    @SerializedName("Category")
    private String category;

    @SerializedName("Category_Slug")
    private String categorySlug;

    @SerializedName("Name")
    private String name;

    @SerializedName("Product_Slug")
    private String productSlug;

    @SerializedName("Price")
    private double price;

    @SerializedName("Sale_Price")
    private double salePrice;

    @SerializedName("Volume_Size")
    private String volumeSize;

    @SerializedName("Is_Skincare")
    private String isSkincare; // Từ MongoDB trả về chuỗi "FALSE" hoặc "TRUE"

    @SerializedName("Skin_Type_Target")
    private String skinTypeTarget;

    @SerializedName("Main_Concern")
    private String mainConcern;

    @SerializedName("Key_Ingredients")
    private String keyIngredients;

    @SerializedName("Description_Short")
    private String descriptionShort;

    @SerializedName("How_To_Use")
    private String howToUse;

    @SerializedName("Status")
    private String status;

    @SerializedName("Stock_Quantity")
    private int stockQuantity;

    @SerializedName("Full_Description")
    private String fullDescription;

    @SerializedName("Description_Images")
    private List<String> descriptionImages;

    @SerializedName("Thumbnail_Images")
    private String thumbnailImages;

    @SerializedName("Gallery_Images")
    private List<String> galleryImages;

    @SerializedName("slug")
    private String slug;

    @SerializedName("Stock_Reserved")
    private int stockReserved;

    @SerializedName("isActive")
    private boolean isActive = true;

    @SerializedName("shade_color_hex")
    private String shadeColorHex;

    @SerializedName("Brand")
    private String brand;

    @SerializedName("rating")
    private double rating;

    @SerializedName("reviewCount")
    private int reviewCount;

    @SerializedName("Is_Vegan_Formula")
    private boolean isVeganFormula;

    @SerializedName("Is_Dermatologist_Tested")
    private boolean isDermatologistTested;

    public Product() {
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getCategorySlug() { return categorySlug; }
    public void setCategorySlug(String categorySlug) { this.categorySlug = categorySlug; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProductSlug() { return productSlug; }
    public void setProductSlug(String productSlug) { this.productSlug = productSlug; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public double getSalePrice() { return salePrice; }
    public void setSalePrice(double salePrice) { this.salePrice = salePrice; }

    public String getVolumeSize() { return volumeSize; }
    public void setVolumeSize(String volumeSize) { this.volumeSize = volumeSize; }

    public String getIsSkincare() { return isSkincare; }
    public void setIsSkincare(String isSkincare) { this.isSkincare = isSkincare; }

    public String getSkinTypeTarget() { return skinTypeTarget; }
    public void setSkinTypeTarget(String skinTypeTarget) { this.skinTypeTarget = skinTypeTarget; }

    public String getMainConcern() { return mainConcern; }
    public void setMainConcern(String mainConcern) { this.mainConcern = mainConcern; }

    public String getKeyIngredients() { return keyIngredients; }
    public void setKeyIngredients(String keyIngredients) { this.keyIngredients = keyIngredients; }

    public String getDescriptionShort() { return descriptionShort; }
    public void setDescriptionShort(String descriptionShort) { this.descriptionShort = descriptionShort; }

    public String getHowToUse() { return howToUse; }
    public void setHowToUse(String howToUse) { this.howToUse = howToUse; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getStockQuantity() { return stockQuantity <= 0 ? 100 : stockQuantity; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }

    public String getFullDescription() { return fullDescription; }
    public void setFullDescription(String fullDescription) { this.fullDescription = fullDescription; }

    public List<String> getDescriptionImages() { return descriptionImages; }
    public void setDescriptionImages(List<String> descriptionImages) { this.descriptionImages = descriptionImages; }

    public String getThumbnailImages() { return thumbnailImages; }
    public void setThumbnailImages(String thumbnailImages) { this.thumbnailImages = thumbnailImages; }

    public List<String> getGalleryImages() { return galleryImages; }
    public void setGalleryImages(List<String> galleryImages) { this.galleryImages = galleryImages; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public int getStockReserved() { return stockReserved; }
    public void setStockReserved(int stockReserved) { this.stockReserved = stockReserved; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public String getShadeColorHex() { return shadeColorHex; }
    public void setShadeColorHex(String shadeColorHex) { this.shadeColorHex = shadeColorHex; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }

    public int getReviewCount() { return reviewCount; }
    public void setReviewCount(int reviewCount) { this.reviewCount = reviewCount; }

    public boolean isVeganFormula() { return isVeganFormula; }
    public void setVeganFormula(boolean veganFormula) { isVeganFormula = veganFormula; }

    public boolean isDermatologistTested() { return isDermatologistTested; }
    public void setDermatologistTested(boolean dermatologistTested) { isDermatologistTested = dermatologistTested; }
}