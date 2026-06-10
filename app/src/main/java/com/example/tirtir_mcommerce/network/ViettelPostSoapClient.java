package com.example.tirtir_mcommerce.network;

import android.content.Context;
import android.util.Log;

import com.example.tirtir_mcommerce.model.ShippingOption;

import org.json.JSONArray;
import org.json.JSONObject;
import org.ksoap2.SoapEnvelope;
import org.ksoap2.serialization.SoapObject;
import org.ksoap2.serialization.SoapSerializationEnvelope;
import org.ksoap2.transport.HttpTransportSE;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SOAP client giao tiếp với Viettel Post Shipping API.
 *
 * Chiến lược 2 lớp:
 *   1. Gọi SOAP thật qua ksoap2 (production)
 *   2. Nếu lỗi (offline / timeout / dev environment) → fallback sang
 *      assets/viettelpost_stub.json để app luôn hiển thị được phí vận chuyển
 *
 * Cách dùng:
 *   ViettelPostSoapClient client = new ViettelPostSoapClient(context);
 *   client.getShippingFees("HCM", "HN", 500, new ViettelPostSoapClient.Callback() {
 *       public void onSuccess(List<ShippingOption> options) { ... }
 *       public void onFallback(List<ShippingOption> options) { ... } // từ stub
 *       public void onError(String message) { ... }
 *   });
 */
public class ViettelPostSoapClient {

    private static final String TAG = "ViettelPostSOAP";

    // === Viettel Post SOAP endpoint ===
    private static final String SOAP_URL =
            "https://api.viettelpost.vn/Webservice/ViettelPost.asmx";
    private static final String SOAP_NAMESPACE = "http://tempuri.org/";
    private static final String SOAP_ACTION_GET_PRICE =
            "http://tempuri.org/VTP_GetListService";
    private static final String SOAP_METHOD_GET_PRICE = "VTP_GetListService";

    private static final int TIMEOUT_MS = 5_000; // Timeout 5s as requested
    private static final String STUB_FILE = "viettelpost_stub.json";

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface Callback {
        void onSuccess(List<ShippingOption> options);
        // Trả về dữ liệu stub — UI biết đây là giá ước tính
        void onFallback(List<ShippingOption> options);
        void onError(String message);
    }

    public ViettelPostSoapClient(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * @param senderProvince   Tỉnh/thành gửi hàng (vd: "HCM")
     * @param receiverProvince Tỉnh/thành nhận hàng (vd: "HN")
     * @param weightGrams      Khối lượng gói hàng (gram)
     * @param callback         Kết quả trả về trên background thread
     */
    public void getShippingFees(String senderProvince,
                                String receiverProvince,
                                int weightGrams,
                                Callback callback) {
        executor.execute(() -> {
            try {
                List<ShippingOption> result = callSoapApi(
                        senderProvince, receiverProvince, weightGrams);
                callback.onSuccess(result);
            } catch (Exception e) {
                Log.w(TAG, "SOAP call thất bại, dùng stub: " + e.getMessage());
                try {
                    List<ShippingOption> stub = loadStub();
                    callback.onFallback(stub);
                } catch (Exception stubEx) {
                    callback.onError("Không thể tải phí vận chuyển: " + stubEx.getMessage());
                }
            }
        });
    }

    // ===========================
    // SOAP CALL (ksoap2)
    // ===========================

    private List<ShippingOption> callSoapApi(String sender,
                                              String receiver,
                                              int weight) throws Exception {
        SoapObject request = new SoapObject(SOAP_NAMESPACE, SOAP_METHOD_GET_PRICE);
        request.addProperty("SENDER_PROVINCE", sender);
        request.addProperty("RECEIVER_PROVINCE", receiver);
        request.addProperty("PRODUCT_WEIGHT", weight);

        SoapSerializationEnvelope envelope =
                new SoapSerializationEnvelope(SoapEnvelope.VER11);
        envelope.dotNet = true;
        envelope.setOutputSoapObject(request);

        HttpTransportSE transport = new HttpTransportSE(SOAP_URL, TIMEOUT_MS);
        transport.call(SOAP_ACTION_GET_PRICE, envelope);

        SoapObject response = (SoapObject) envelope.bodyIn;
        return parseSoapResponse(response);
    }

    private List<ShippingOption> parseSoapResponse(SoapObject response) {
        List<ShippingOption> options = new ArrayList<>();
        if (response == null) return options;

        for (int i = 0; i < response.getPropertyCount(); i++) {
            Object prop = response.getProperty(i);
            if (!(prop instanceof SoapObject)) continue;
            SoapObject item = (SoapObject) prop;

            ShippingOption opt = new ShippingOption();
            opt.setServiceCode(safeGetString(item, "MA_DICH_VU"));
            opt.setServiceName(safeGetString(item, "TEN_DICH_VU"));
            opt.setPrice(safeGetLong(item, "GIA_CUOC"));
            opt.setEstimatedTime(safeGetString(item, "THOI_GIAN_DU_KIEN"));
            opt.setDescription(safeGetString(item, "MO_TA"));
            options.add(opt);
        }
        return options;
    }

    // ===========================
    // STUB FALLBACK (assets JSON)
    // ===========================

    private List<ShippingOption> loadStub() throws Exception {
        String json = readAsset(STUB_FILE);
        return parseStubJson(json);
    }

    private String readAsset(String filename) throws IOException {
        try (InputStream is = context.getAssets().open(filename)) {
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            return new String(buffer, StandardCharsets.UTF_8);
        }
    }

    private List<ShippingOption> parseStubJson(String json) throws Exception {
        List<ShippingOption> options = new ArrayList<>();
        JSONObject root = new JSONObject(json);
        JSONArray data = root.getJSONArray("data");

        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.getJSONObject(i);
            options.add(new ShippingOption(
                    item.getString("ma_dich_vu"),
                    item.getString("ten_dich_vu"),
                    item.getLong("gia_cuoc"),
                    item.getString("thoi_gian_du_kien"),
                    item.getString("mo_ta")
            ));
        }
        return options;
    }

    // ===========================
    // HELPERS
    // ===========================

    private String safeGetString(SoapObject obj, String key) {
        try { return obj.getPropertyAsString(key); } catch (Exception e) { return ""; }
    }

    private long safeGetLong(SoapObject obj, String key) {
        try { return Long.parseLong(obj.getPropertyAsString(key)); } catch (Exception e) { return 0; }
    }
}
