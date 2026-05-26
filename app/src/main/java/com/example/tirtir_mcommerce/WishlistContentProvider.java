package com.example.tirtir_mcommerce;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * WishlistContentProvider - Android Component: ContentProvider
 *
 * Mục đích (theo Android Architecture - Syllabus):
 * - Chia sẻ dữ liệu Wishlist giữa các module trong app
 * - Lưu Wishlist của người dùng vào SQLite cục bộ
 * - Expose dữ liệu thông qua URI chuẩn của Android ContentProvider
 *
 * URI format: content://com.example.tirtir_mcommerce.provider/wishlist
 *
 * Cách dùng trong Fragment khác:
 *   Uri uri = Uri.parse("content://com.example.tirtir_mcommerce.provider/wishlist");
 *   Cursor cursor = getContentResolver().query(uri, null, null, null, null);
 */
public class WishlistContentProvider extends ContentProvider {

    // ===========================
    // CONSTANTS
    // ===========================
    public static final String AUTHORITY = "com.example.tirtir_mcommerce.provider";
    public static final Uri BASE_URI = Uri.parse("content://" + AUTHORITY);

    public static final String TABLE_WISHLIST = "wishlist";
    public static final Uri CONTENT_URI = Uri.withAppendedPath(BASE_URI, TABLE_WISHLIST);

    // Columns
    public static final String COL_ID = "_id";
    public static final String COL_PRODUCT_ID = "product_id";
    public static final String COL_PRODUCT_NAME = "product_name";
    public static final String COL_PRODUCT_IMAGE = "product_image";
    public static final String COL_PRODUCT_PRICE = "product_price";
    public static final String COL_ADDED_AT = "added_at";

    // URI codes
    private static final int WISHLIST = 1;
    private static final int WISHLIST_ID = 2;

    private static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        URI_MATCHER.addURI(AUTHORITY, TABLE_WISHLIST, WISHLIST);
        URI_MATCHER.addURI(AUTHORITY, TABLE_WISHLIST + "/#", WISHLIST_ID);
    }

    private WishlistDbHelper dbHelper;

    // ===========================
    // LIFECYCLE
    // ===========================

    @Override
    public boolean onCreate() {
        dbHelper = new WishlistDbHelper(getContext());
        return true;
    }

    // ===========================
    // CRUD OPERATIONS
    // ===========================

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection,
                        @Nullable String selection, @Nullable String[] selectionArgs,
                        @Nullable String sortOrder) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor;

        switch (URI_MATCHER.match(uri)) {
            case WISHLIST:
                cursor = db.query(TABLE_WISHLIST, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case WISHLIST_ID:
                String id = uri.getLastPathSegment();
                cursor = db.query(TABLE_WISHLIST, projection, COL_ID + "=?", new String[]{id}, null, null, sortOrder);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        if (getContext() != null) {
            cursor.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return cursor;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long rowId = db.insertWithOnConflict(TABLE_WISHLIST, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        if (rowId > 0) {
            Uri newUri = ContentUris.withAppendedId(CONTENT_URI, rowId);
            if (getContext() != null) {
                getContext().getContentResolver().notifyChange(newUri, null);
            }
            return newUri;
        }
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int count;

        switch (URI_MATCHER.match(uri)) {
            case WISHLIST:
                count = db.delete(TABLE_WISHLIST, selection, selectionArgs);
                break;
            case WISHLIST_ID:
                String id = uri.getLastPathSegment();
                count = db.delete(TABLE_WISHLIST, COL_ID + "=?", new String[]{id});
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        if (count > 0 && getContext() != null) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values,
                      @Nullable String selection, @Nullable String[] selectionArgs) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int count = db.update(TABLE_WISHLIST, values, selection, selectionArgs);
        if (count > 0 && getContext() != null) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        switch (URI_MATCHER.match(uri)) {
            case WISHLIST:
                return "vnd.android.cursor.dir/vnd." + AUTHORITY + "." + TABLE_WISHLIST;
            case WISHLIST_ID:
                return "vnd.android.cursor.item/vnd." + AUTHORITY + "." + TABLE_WISHLIST;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    // ===========================
    // SQLITE HELPER (Inner class)
    // ===========================

    /**
     * SQLiteOpenHelper quản lý database cục bộ cho Wishlist.
     */
    static class WishlistDbHelper extends SQLiteOpenHelper {

        private static final String DB_NAME = "tirtir_wishlist.db";
        private static final int DB_VERSION = 1;

        private static final String CREATE_TABLE_WISHLIST =
                "CREATE TABLE " + TABLE_WISHLIST + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_PRODUCT_ID + " TEXT UNIQUE NOT NULL, " +
                COL_PRODUCT_NAME + " TEXT, " +
                COL_PRODUCT_IMAGE + " TEXT, " +
                COL_PRODUCT_PRICE + " REAL, " +
                COL_ADDED_AT + " INTEGER DEFAULT (strftime('%s', 'now'))" +
                ")";

        WishlistDbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATE_TABLE_WISHLIST);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_WISHLIST);
            onCreate(db);
        }
    }
}
