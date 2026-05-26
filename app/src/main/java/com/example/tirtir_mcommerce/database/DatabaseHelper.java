package com.example.tirtir_mcommerce.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.tirtir_mcommerce.model.Product;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "tirtir_cache.db";
    // v2 → v3: thêm bảng ingredient_conflicts (FTS4)
    private static final int DATABASE_VERSION = 3;

    // === products table ===
    private static final String TABLE_PRODUCT = "products";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_PRODUCT_ID = "product_id";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_PRICE = "price";
    private static final String COLUMN_SALE_PRICE = "sale_price";
    private static final String COLUMN_THUMBNAIL = "thumbnail";
    private static final String COLUMN_CATEGORY = "category";
    private static final String COLUMN_SKIN_TYPE = "skin_type_target";
    private static final String COLUMN_MAIN_CONCERN = "main_concern";
    private static final String COLUMN_IS_SKINCARE = "is_skincare";

    // === ingredient_conflicts FTS4 table ===
    public static final String TABLE_INGREDIENT_CONFLICTS = "ingredient_conflicts";

    private static DatabaseHelper instance;

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
            "CREATE TABLE " + TABLE_PRODUCT + " (" +
            COLUMN_ID + " TEXT PRIMARY KEY, " +
            COLUMN_PRODUCT_ID + " TEXT, " +
            COLUMN_NAME + " TEXT, " +
            COLUMN_PRICE + " REAL, " +
            COLUMN_SALE_PRICE + " REAL, " +
            COLUMN_THUMBNAIL + " TEXT, " +
            COLUMN_CATEGORY + " TEXT, " +
            COLUMN_SKIN_TYPE + " TEXT, " +
            COLUMN_MAIN_CONCERN + " TEXT, " +
            COLUMN_IS_SKINCARE + " TEXT)"
        );

        createIngredientConflictsTable(db);
    }

    // Tạo riêng để onUpgrade có thể gọi lại mà không drop products
    private void createIngredientConflictsTable(SQLiteDatabase db) {
        // VIRTUAL TABLE USING fts4 cho phép full-text search trên tên thành phần
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS " + TABLE_INGREDIENT_CONFLICTS +
            " USING fts4(" +
            "ingredient_a TEXT, " +   // thành phần A
            "ingredient_b TEXT, " +   // thành phần B xung đột với A
            "reason TEXT, " +         // lý do xung đột
            "severity TEXT" +         // mức độ: HIGH / MEDIUM / LOW
            ")"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            // Chỉ thêm bảng mới, không drop products để tránh mất cache
            createIngredientConflictsTable(db);
        }
    }

    // ===========================
    // PRODUCTS
    // ===========================

    public void insertProducts(List<Product> productList) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLE_PRODUCT);

        for (Product p : productList) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_ID, p.getId());
            values.put(COLUMN_PRODUCT_ID, p.getProductId());
            values.put(COLUMN_NAME, p.getName());
            values.put(COLUMN_PRICE, p.getPrice());
            values.put(COLUMN_SALE_PRICE, p.getSalePrice());
            values.put(COLUMN_THUMBNAIL, p.getThumbnailImages());
            values.put(COLUMN_CATEGORY, p.getCategory());
            values.put(COLUMN_SKIN_TYPE, p.getSkinTypeTarget());
            values.put(COLUMN_MAIN_CONCERN, p.getMainConcern());
            values.put(COLUMN_IS_SKINCARE, p.getIsSkincare());
            db.insert(TABLE_PRODUCT, null, values);
        }
    }

    public List<Product> getAllProducts() {
        List<Product> productList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_PRODUCT, null);

        if (cursor.moveToFirst()) {
            do {
                Product product = new Product();
                product.setId(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID)));
                product.setProductId(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PRODUCT_ID)));
                product.setName(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME)));
                product.setPrice(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_PRICE)));
                product.setSalePrice(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_SALE_PRICE)));
                product.setThumbnailImages(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_THUMBNAIL)));
                product.setCategory(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CATEGORY)));
                product.setSkinTypeTarget(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SKIN_TYPE)));
                product.setMainConcern(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MAIN_CONCERN)));
                product.setIsSkincare(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IS_SKINCARE)));
                productList.add(product);
            } while (cursor.moveToNext());
        }

        cursor.close();
        return productList;
    }

    // ===========================
    // INGREDIENT CONFLICTS (FTS4)
    // ===========================

    public void insertIngredientConflict(String ingredientA, String ingredientB,
                                         String reason, String severity) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("ingredient_a", ingredientA);
        values.put("ingredient_b", ingredientB);
        values.put("reason", reason);
        values.put("severity", severity);
        db.insert(TABLE_INGREDIENT_CONFLICTS, null, values);
    }

    // Full-text search: tìm tất cả xung đột liên quan đến một thành phần
    public Cursor searchConflicts(String query) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery(
            "SELECT * FROM " + TABLE_INGREDIENT_CONFLICTS +
            " WHERE " + TABLE_INGREDIENT_CONFLICTS + " MATCH ?",
            new String[]{query + "*"}
        );
    }
}
