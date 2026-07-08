package com.example.tirtir_mcommerce.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.tirtir_mcommerce.model.CartItem;
import com.example.tirtir_mcommerce.model.Product;
import com.example.tirtir_mcommerce.model.RoutineStep;
import com.example.tirtir_mcommerce.model.ShadeMatchResult;
import com.example.tirtir_mcommerce.model.SkinAnalysisResult;
import com.example.tirtir_mcommerce.model.SkinProfile;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "tirtir_cache.db";
    // v4: thêm bảng cart_items cho Offline Cart
    // v5: thêm bảng skin_profiles cho Offline Skin Analysis (Phase 3)
    // v6: extend products table with rating, review_count, is_vegan_formula, is_dermatologist_tested
    private static final int DATABASE_VERSION = 7;

    private static final Gson GSON = new Gson();

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
    private static final String COLUMN_RATING = "rating";
    private static final String COLUMN_REVIEW_COUNT = "review_count";
    private static final String COLUMN_IS_VEGAN = "is_vegan_formula";
    private static final String COLUMN_IS_DERMA = "is_dermatologist_tested";

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

    // === skin_profiles table ===
    public static final String TABLE_SKIN_PROFILES = "skin_profiles";
    public static final String SP_COL_ID = "id";
    public static final String SP_COL_USER_ID = "user_id";
    public static final String SP_COL_SKIN_TONE = "skin_tone";
    public static final String SP_COL_UNDERTONE = "undertone";
    public static final String SP_COL_SKIN_TYPE = "skin_type";
    public static final String SP_COL_CONCERNS = "concerns";
    public static final String SP_COL_CONFIDENCE = "confidence";
    public static final String SP_COL_SKIN_HEX = "skin_hex";
    public static final String SP_COL_SHADE_RESULTS = "shade_results";
    public static final String SP_COL_ROUTINE_STEPS = "routine_steps";
    public static final String SP_COL_TIMESTAMP = "timestamp";
    public static final String SP_COL_SYNCED = "synced";

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
            COLUMN_IS_SKINCARE + " TEXT, " +
            COLUMN_RATING + " REAL DEFAULT 0, " +
            COLUMN_REVIEW_COUNT + " INTEGER DEFAULT 0, " +
            COLUMN_IS_VEGAN + " INTEGER DEFAULT 0, " +
            COLUMN_IS_DERMA + " INTEGER DEFAULT 0)"
        );

        createIngredientConflictsTable(db);
        createCartTable(db);
        createSkinProfilesTable(db);
        createWishlistTable(db);
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

    private void createWishlistTable(SQLiteDatabase db) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS wishlist_items (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "product_id TEXT UNIQUE NOT NULL, " +
            "product_name TEXT, " +
            "thumbnail TEXT, " +
            "price REAL, " +
            "synced INTEGER DEFAULT 0, " +
            "added_at INTEGER DEFAULT (strftime('%s','now'))" +
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
        seedIngredientConflicts(db);
    }

    private void seedIngredientConflicts(SQLiteDatabase db) {
        db.execSQL("INSERT INTO " + TABLE_INGREDIENT_CONFLICTS + " (ingredient_a, ingredient_b, reason, severity) VALUES ('Retinol', 'AHA', 'Tăng nguy cơ kích ứng, mẩn đỏ và khô da', 'HIGH')");
        db.execSQL("INSERT INTO " + TABLE_INGREDIENT_CONFLICTS + " (ingredient_a, ingredient_b, reason, severity) VALUES ('Retinol', 'BHA', 'Làm quá tải khả năng tẩy tế bào chết của da', 'HIGH')");
        db.execSQL("INSERT INTO " + TABLE_INGREDIENT_CONFLICTS + " (ingredient_a, ingredient_b, reason, severity) VALUES ('Vitamin C', 'AHA', 'Làm thay đổi độ pH khiến Vitamin C mất tác dụng', 'MEDIUM')");
        db.execSQL("INSERT INTO " + TABLE_INGREDIENT_CONFLICTS + " (ingredient_a, ingredient_b, reason, severity) VALUES ('Vitamin C', 'Retinol', 'Tác động mạnh làm mỏng màng bảo vệ da', 'HIGH')");
    }

    private void createSkinProfilesTable(SQLiteDatabase db) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS " + TABLE_SKIN_PROFILES + " (" +
            SP_COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            SP_COL_USER_ID + " TEXT, " +
            SP_COL_SKIN_TONE + " TEXT, " +
            SP_COL_UNDERTONE + " TEXT, " +
            SP_COL_SKIN_TYPE + " TEXT, " +
            SP_COL_CONCERNS + " TEXT, " +
            SP_COL_CONFIDENCE + " REAL DEFAULT 0, " +
            SP_COL_SKIN_HEX + " TEXT, " +
            SP_COL_SHADE_RESULTS + " TEXT, " +
            SP_COL_ROUTINE_STEPS + " TEXT, " +
            SP_COL_TIMESTAMP + " INTEGER, " +
            SP_COL_SYNCED + " INTEGER DEFAULT 0" +
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
        if (oldVersion < 5) {
            // Phase 3 Sprint 3.1: Thêm bảng skin_profiles cho offline skin analysis
            createSkinProfilesTable(db);
        }
        if (oldVersion < 6) {
            // v6: add product rating/badge columns for offline cache
            try { db.execSQL("ALTER TABLE " + TABLE_PRODUCT + " ADD COLUMN " + COLUMN_RATING + " REAL DEFAULT 0"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + TABLE_PRODUCT + " ADD COLUMN " + COLUMN_REVIEW_COUNT + " INTEGER DEFAULT 0"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + TABLE_PRODUCT + " ADD COLUMN " + COLUMN_IS_VEGAN + " INTEGER DEFAULT 0"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + TABLE_PRODUCT + " ADD COLUMN " + COLUMN_IS_DERMA + " INTEGER DEFAULT 0"); } catch (Exception ignored) {}
        }
        if (oldVersion < 7) {
            createWishlistTable(db);
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
            values.put(COLUMN_RATING, p.getRating());
            values.put(COLUMN_REVIEW_COUNT, p.getReviewCount());
            values.put(COLUMN_IS_VEGAN, p.isVeganFormula() ? 1 : 0);
            values.put(COLUMN_IS_DERMA, p.isDermatologistTested() ? 1 : 0);
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
                int ratingIdx = cursor.getColumnIndex(COLUMN_RATING);
                if (ratingIdx >= 0) product.setRating(cursor.getDouble(ratingIdx));
                int reviewIdx = cursor.getColumnIndex(COLUMN_REVIEW_COUNT);
                if (reviewIdx >= 0) product.setReviewCount(cursor.getInt(reviewIdx));
                int veganIdx = cursor.getColumnIndex(COLUMN_IS_VEGAN);
                if (veganIdx >= 0) product.setVeganFormula(cursor.getInt(veganIdx) == 1);
                int dermaIdx = cursor.getColumnIndex(COLUMN_IS_DERMA);
                if (dermaIdx >= 0) product.setDermatologistTested(cursor.getInt(dermaIdx) == 1);
                productList.add(product);
            } while (cursor.moveToNext());
        }

        cursor.close();
        return productList;
    }

    public Product getProductByIdOrName(String idOrName) {
        if (idOrName == null || idOrName.trim().isEmpty()) return null;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_PRODUCT + 
                " WHERE " + COLUMN_ID + " = ? OR " + COLUMN_PRODUCT_ID + " = ? OR " + COLUMN_NAME + " = ?", 
                new String[]{idOrName, idOrName, idOrName});
        Product product = null;
        if (cursor.moveToFirst()) {
            product = new Product();
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
            int ratingIdx = cursor.getColumnIndex(COLUMN_RATING);
            if (ratingIdx >= 0) product.setRating(cursor.getDouble(ratingIdx));
            int reviewIdx = cursor.getColumnIndex(COLUMN_REVIEW_COUNT);
            if (reviewIdx >= 0) product.setReviewCount(cursor.getInt(reviewIdx));
            int veganIdx = cursor.getColumnIndex(COLUMN_IS_VEGAN);
            if (veganIdx >= 0) product.setVeganFormula(cursor.getInt(veganIdx) == 1);
            int dermaIdx = cursor.getColumnIndex(COLUMN_IS_DERMA);
            if (dermaIdx >= 0) product.setDermatologistTested(cursor.getInt(dermaIdx) == 1);
        }
        cursor.close();
        
        // If not found by exact ID or name, do a fuzzy search by name
        if (product == null) {
            cursor = db.rawQuery("SELECT * FROM " + TABLE_PRODUCT + " WHERE " + COLUMN_NAME + " LIKE ?", 
                    new String[]{"%" + idOrName + "%"});
            if (cursor.moveToFirst()) {
                product = new Product();
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
                int ratingIdx = cursor.getColumnIndex(COLUMN_RATING);
                if (ratingIdx >= 0) product.setRating(cursor.getDouble(ratingIdx));
                int reviewIdx = cursor.getColumnIndex(COLUMN_REVIEW_COUNT);
                if (reviewIdx >= 0) product.setReviewCount(cursor.getInt(reviewIdx));
                int veganIdx = cursor.getColumnIndex(COLUMN_IS_VEGAN);
                if (veganIdx >= 0) product.setVeganFormula(cursor.getInt(veganIdx) == 1);
                int dermaIdx = cursor.getColumnIndex(COLUMN_IS_DERMA);
                if (dermaIdx >= 0) product.setDermatologistTested(cursor.getInt(dermaIdx) == 1);
            }
            cursor.close();
        }
        return product;
    }

    public String getLatestSkinType() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT skin_type FROM " + TABLE_SKIN_PROFILES + 
                " ORDER BY timestamp DESC LIMIT 1", null);
        String skinType = "combination";
        if (cursor.moveToFirst()) {
            skinType = cursor.getString(0);
        }
        cursor.close();
        return skinType;
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

    /** Update the selected product variant and mark the local-first item pending sync. */
    public void updateCartShade(String productId, String shade) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(CART_COL_SHADE, shade == null ? "" : shade);
        values.put(CART_COL_SYNCED, 0);
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

    /**
     * Thay thế các item đã đồng bộ bằng danh sách mới từ Cloud.
     * Giữ lại các item đang có thay đổi offline (synced = 0).
     */
    public void replaceCartItemsFromCloud(List<CartItem> cloudItems) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            // Xóa tất cả các item ĐÃ đồng bộ (synced = 1)
            db.delete(TABLE_CART, CART_COL_SYNCED + "=1", null);
            
            for (CartItem item : cloudItems) {
                // Kiểm tra xem item này có đang nằm trong pending sync không (synced = 0)
                Cursor cursor = db.query(TABLE_CART, new String[]{CART_COL_ID},
                        CART_COL_PRODUCT_ID + "=? AND " + CART_COL_SYNCED + "=0",
                        new String[]{item.getProductId()}, null, null, null);
                        
                if (cursor != null && cursor.moveToFirst()) {
                    // Đang pending sync -> Bỏ qua bản ghi từ mây, giữ nguyên bản ghi local
                    cursor.close();
                    continue;
                }
                if (cursor != null) cursor.close();

                // Chèn mới item từ cloud (đánh dấu synced = 1)
                ContentValues values = new ContentValues();
                values.put(CART_COL_PRODUCT_ID, item.getProductId());
                values.put(CART_COL_NAME, item.getProductName());
                values.put(CART_COL_THUMBNAIL, item.getThumbnail());
                values.put(CART_COL_PRICE, item.getPrice());
                values.put(CART_COL_QUANTITY, item.getQuantity());
                values.put(CART_COL_SHADE, item.getShade());
                values.put(CART_COL_SYNCED, 1); 
                db.insert(TABLE_CART, null, values);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
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

    // ===========================
    // SKIN PROFILES (Offline Skin Analysis)
    // ===========================

    /**
     * Lưu kết quả phân tích da vào SQLite.
     * Dùng khi user chưa đăng nhập (guest mode).
     * @return row ID vừa insert, hoặc -1 nếu lỗi
     */
    public long saveSkinProfile(SkinProfile profile) {
        SQLiteDatabase db = this.getWritableDatabase();
        SkinAnalysisResult result = profile.getAnalysisResult();

        ContentValues values = new ContentValues();
        values.put(SP_COL_USER_ID, profile.getUserId());
        values.put(SP_COL_SKIN_TONE, result != null ? result.getSkinTone() : null);
        values.put(SP_COL_UNDERTONE, result != null ? result.getUndertone() : null);
        values.put(SP_COL_SKIN_TYPE, result != null ? result.getSkinType() : null);
        values.put(SP_COL_CONCERNS, result != null ? GSON.toJson(result.getConcerns()) : null);
        values.put(SP_COL_CONFIDENCE, result != null ? result.getConfidence() : 0);
        values.put(SP_COL_SKIN_HEX, result != null ? result.getSkinHex() : null);
        values.put(SP_COL_SHADE_RESULTS, GSON.toJson(profile.getShadeMatches()));
        values.put(SP_COL_ROUTINE_STEPS, GSON.toJson(profile.getRoutineSteps()));
        values.put(SP_COL_TIMESTAMP, profile.getTimestamp());
        values.put(SP_COL_SYNCED, 0);

        return db.insert(TABLE_SKIN_PROFILES, null, values);
    }

    /**
     * Lấy hồ sơ da mới nhất từ SQLite.
     * @return SkinProfile mới nhất, hoặc null nếu chưa có
     */
    public SkinProfile getLatestSkinProfile() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(
            TABLE_SKIN_PROFILES, null, null, null, null, null,
            SP_COL_TIMESTAMP + " DESC", "1"
        );

        if (cursor == null || !cursor.moveToFirst()) {
            if (cursor != null) cursor.close();
            return null;
        }

        try {
            SkinAnalysisResult result = new SkinAnalysisResult();
            result.setSkinTone(cursor.getString(cursor.getColumnIndexOrThrow(SP_COL_SKIN_TONE)));
            result.setUndertone(cursor.getString(cursor.getColumnIndexOrThrow(SP_COL_UNDERTONE)));
            result.setSkinType(cursor.getString(cursor.getColumnIndexOrThrow(SP_COL_SKIN_TYPE)));
            result.setConfidence(cursor.getDouble(cursor.getColumnIndexOrThrow(SP_COL_CONFIDENCE)));
            result.setSkinHex(cursor.getString(cursor.getColumnIndexOrThrow(SP_COL_SKIN_HEX)));

            String concernsJson = cursor.getString(cursor.getColumnIndexOrThrow(SP_COL_CONCERNS));
            if (concernsJson != null) {
                Type listType = new TypeToken<List<String>>() {}.getType();
                result.setConcerns(GSON.fromJson(concernsJson, listType));
            }

            String shadeJson = cursor.getString(cursor.getColumnIndexOrThrow(SP_COL_SHADE_RESULTS));
            Type shadeListType = new TypeToken<List<ShadeMatchResult>>() {}.getType();
            List<ShadeMatchResult> shadeMatches = shadeJson != null
                ? GSON.fromJson(shadeJson, shadeListType) : new ArrayList<>();

            String routineJson = cursor.getString(cursor.getColumnIndexOrThrow(SP_COL_ROUTINE_STEPS));
            Type routineListType = new TypeToken<List<RoutineStep>>() {}.getType();
            List<RoutineStep> routineSteps = routineJson != null
                ? GSON.fromJson(routineJson, routineListType) : new ArrayList<>();

            SkinProfile profile = new SkinProfile(
                cursor.getString(cursor.getColumnIndexOrThrow(SP_COL_USER_ID)),
                result, shadeMatches, routineSteps
            );
            profile.setId(cursor.getLong(cursor.getColumnIndexOrThrow(SP_COL_ID)));
            profile.setSynced(cursor.getInt(cursor.getColumnIndexOrThrow(SP_COL_SYNCED)) == 1);
            return profile;
        } finally {
            cursor.close();
        }
    }

    /**
     * Đánh dấu hồ sơ da đã sync lên server.
     */
    public void markProfileSynced(long profileId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(SP_COL_SYNCED, 1);
        db.update(TABLE_SKIN_PROFILES, values, SP_COL_ID + "=?",
            new String[]{String.valueOf(profileId)});
    }

    /**
     * Lấy tất cả hồ sơ chưa sync (guest profiles).
     */
    public List<SkinProfile> getUnsyncedProfiles() {
        List<SkinProfile> profiles = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(
            TABLE_SKIN_PROFILES, null,
            SP_COL_SYNCED + "=0", null, null, null,
            SP_COL_TIMESTAMP + " DESC"
        );

        if (cursor != null && cursor.moveToFirst()) {
            do {
                SkinAnalysisResult result = new SkinAnalysisResult();
                result.setSkinTone(cursor.getString(cursor.getColumnIndexOrThrow(SP_COL_SKIN_TONE)));
                result.setUndertone(cursor.getString(cursor.getColumnIndexOrThrow(SP_COL_UNDERTONE)));
                result.setSkinType(cursor.getString(cursor.getColumnIndexOrThrow(SP_COL_SKIN_TYPE)));
                result.setConfidence(cursor.getDouble(cursor.getColumnIndexOrThrow(SP_COL_CONFIDENCE)));
                result.setSkinHex(cursor.getString(cursor.getColumnIndexOrThrow(SP_COL_SKIN_HEX)));

                SkinProfile profile = new SkinProfile(null, result, new ArrayList<>(), new ArrayList<>());
                profile.setId(cursor.getLong(cursor.getColumnIndexOrThrow(SP_COL_ID)));
                profiles.add(profile);
            } while (cursor.moveToNext());
            cursor.close();
        }
        return profiles;
    }

    /**
     * Cập nhật danh sách bước vào hồ sơ da mới nhất
     */
    public void updateRoutineSteps(List<RoutineStep> steps) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.query(
            TABLE_SKIN_PROFILES, new String[]{SP_COL_ID}, null, null, null, null,
            SP_COL_TIMESTAMP + " DESC", "1"
        );
        if (cursor != null && cursor.moveToFirst()) {
            long id = cursor.getLong(0);
            ContentValues values = new ContentValues();
            values.put(SP_COL_ROUTINE_STEPS, GSON.toJson(steps));
            db.update(TABLE_SKIN_PROFILES, values, SP_COL_ID + " = ?", new String[]{String.valueOf(id)});
        }
        if (cursor != null) cursor.close();
    }
}
