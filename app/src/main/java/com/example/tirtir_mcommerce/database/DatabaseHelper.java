package com.example.tirtir_mcommerce.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.tirtir_mcommerce.model.CartItem;
import com.example.tirtir_mcommerce.model.Product;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "tirtir_cache.db";
    // v4: thêm bảng cart_items cho Offline Cart
    private static final int DATABASE_VERSION = 4;

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

    // === cart_items table ===
    public static final String TABLE_CART = "cart_items";
    public static final String CART_COL_ID = "id";
    public static final String CART_COL_PRODUCT_ID = "product_id";
    public static final String CART_COL_NAME = "product_name";
    public static final String CART_COL_THUMBNAIL = "thumbnail";
    public static final String CART_COL_PRICE = "price";
    public static final String CART_COL_QUANTITY = "quantity";
    public static final String CART_COL_SHADE = "shade";
    public static final String CART_COL_SYNCED = "synced";
    public static final String CART_COL_ADDED_AT = "added_at";

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
        createCartTable(db);
    }

    private void createCartTable(SQLiteDatabase db) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS " + TABLE_CART + " (" +
            CART_COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            CART_COL_PRODUCT_ID + " TEXT UNIQUE NOT NULL, " +
            CART_COL_NAME + " TEXT, " +
            CART_COL_THUMBNAIL + " TEXT, " +
            CART_COL_PRICE + " REAL, " +
            CART_COL_QUANTITY + " INTEGER DEFAULT 1, " +
            CART_COL_SHADE + " TEXT DEFAULT '', " +
            CART_COL_SYNCED + " INTEGER DEFAULT 0, " +
            CART_COL_ADDED_AT + " INTEGER DEFAULT (strftime('%s','now'))" +
            ")"
        );
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
        if (oldVersion < 4) {
            // Sprint 1.2: Thêm bảng giỏ hàng offline
            createCartTable(db);
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

    // ===========================
    // CART ITEMS (Offline Cart)
    // ===========================

    /**
     * Thêm hoặc cập nhật item giỏ hàng.
     * Nếu product_id đã tồn tại → tăng quantity.
     */
    public void insertOrUpdateCartItem(CartItem item) {
        SQLiteDatabase db = this.getWritableDatabase();

        // Kiểm tra item đã tồn tại chưa
        Cursor cursor = db.query(TABLE_CART, new String[]{CART_COL_ID, CART_COL_QUANTITY},
                CART_COL_PRODUCT_ID + "=?", new String[]{item.getProductId()},
                null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            // Đã tồn tại → tăng quantity
            int existingQty = cursor.getInt(cursor.getColumnIndexOrThrow(CART_COL_QUANTITY));
            long rowId = cursor.getLong(cursor.getColumnIndexOrThrow(CART_COL_ID));
            cursor.close();

            ContentValues values = new ContentValues();
            values.put(CART_COL_QUANTITY, existingQty + item.getQuantity());
            values.put(CART_COL_SYNCED, 0); // Reset sync status
            db.update(TABLE_CART, values, CART_COL_ID + "=?", new String[]{String.valueOf(rowId)});
        } else {
            if (cursor != null) cursor.close();
            // Chưa tồn tại → insert mới
            ContentValues values = new ContentValues();
            values.put(CART_COL_PRODUCT_ID, item.getProductId());
            values.put(CART_COL_NAME, item.getProductName());
            values.put(CART_COL_THUMBNAIL, item.getThumbnail());
            values.put(CART_COL_PRICE, item.getPrice());
            values.put(CART_COL_QUANTITY, item.getQuantity());
            values.put(CART_COL_SHADE, item.getShade());
            values.put(CART_COL_SYNCED, 0);
            db.insert(TABLE_CART, null, values);
        }
    }

    /** Lấy toàn bộ giỏ hàng từ SQLite. */
    public List<CartItem> getCartItems() {
        List<CartItem> items = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_CART +
                " ORDER BY " + CART_COL_ADDED_AT + " DESC", null);

        if (cursor.moveToFirst()) {
            do {
                CartItem item = new CartItem();
                item.setId(cursor.getLong(cursor.getColumnIndexOrThrow(CART_COL_ID)));
                item.setProductId(cursor.getString(cursor.getColumnIndexOrThrow(CART_COL_PRODUCT_ID)));
                item.setProductName(cursor.getString(cursor.getColumnIndexOrThrow(CART_COL_NAME)));
                item.setThumbnail(cursor.getString(cursor.getColumnIndexOrThrow(CART_COL_THUMBNAIL)));
                item.setPrice(cursor.getDouble(cursor.getColumnIndexOrThrow(CART_COL_PRICE)));
                item.setQuantity(cursor.getInt(cursor.getColumnIndexOrThrow(CART_COL_QUANTITY)));
                item.setShade(cursor.getString(cursor.getColumnIndexOrThrow(CART_COL_SHADE)));
                item.setSynced(cursor.getInt(cursor.getColumnIndexOrThrow(CART_COL_SYNCED)));
                items.add(item);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return items;
    }

    /** Lấy các item chưa sync (synced=0) — dùng cho NetworkReceiver. */
    public List<CartItem> getPendingSyncItems() {
        List<CartItem> items = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_CART, null,
                CART_COL_SYNCED + "=0", null, null, null, null);

        if (cursor.moveToFirst()) {
            do {
                CartItem item = new CartItem();
                item.setId(cursor.getLong(cursor.getColumnIndexOrThrow(CART_COL_ID)));
                item.setProductId(cursor.getString(cursor.getColumnIndexOrThrow(CART_COL_PRODUCT_ID)));
                item.setProductName(cursor.getString(cursor.getColumnIndexOrThrow(CART_COL_NAME)));
                item.setPrice(cursor.getDouble(cursor.getColumnIndexOrThrow(CART_COL_PRICE)));
                item.setQuantity(cursor.getInt(cursor.getColumnIndexOrThrow(CART_COL_QUANTITY)));
                item.setShade(cursor.getString(cursor.getColumnIndexOrThrow(CART_COL_SHADE)));
                item.setSynced(0);
                items.add(item);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return items;
    }

    /** Đánh dấu item đã sync thành công. */
    public void markCartItemSynced(String productId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(CART_COL_SYNCED, 1);
        db.update(TABLE_CART, values, CART_COL_PRODUCT_ID + "=?", new String[]{productId});
    }

    /** Cập nhật số lượng item. */
    public void updateCartQuantity(String productId, int newQuantity) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(CART_COL_QUANTITY, newQuantity);
        values.put(CART_COL_SYNCED, 0); // Reset sync khi thay đổi
        db.update(TABLE_CART, values, CART_COL_PRODUCT_ID + "=?", new String[]{productId});
    }

    /** Xóa một item khỏi giỏ. */
    public void removeCartItem(String productId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_CART, CART_COL_PRODUCT_ID + "=?", new String[]{productId});
    }

    /** Xóa toàn bộ giỏ hàng. */
    public void clearCart() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLE_CART);
    }

    /** Đếm số item trong giỏ. */
    public int getCartCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT SUM(" + CART_COL_QUANTITY + ") FROM " + TABLE_CART, null);
        int count = 0;
        if (cursor.moveToFirst()) count = cursor.getInt(0);
        cursor.close();
        return count;
    }
}
