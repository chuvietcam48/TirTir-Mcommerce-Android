package com.example.tirtir_mcommerce;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.example.tirtir_mcommerce.repository.CartRepository;

public class CartSyncWorker extends Worker {

    public CartSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            // Lấy dữ liệu từ SQLite (Local) đẩy lên API Node.js và Firestore
            CartRepository cartRepository = new CartRepository(getApplicationContext());
            cartRepository.syncPendingToServer();
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Result.retry();
        }
    }
}
