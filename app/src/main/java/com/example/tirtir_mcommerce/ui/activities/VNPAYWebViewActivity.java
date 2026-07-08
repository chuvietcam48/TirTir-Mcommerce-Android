package com.example.tirtir_mcommerce.ui.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.tirtir_mcommerce.R;

/**
 * VNPAYWebViewActivity — opens the VNPAY payment page inside an in-app WebView.
 * When VNPAY redirects back to VNP_RETURN_URL (http://10.0.2.2:5000/api/v1/payments/vnpay-return),
 * we intercept the URL, read vnp_ResponseCode, and navigate accordingly WITHOUT
 * letting Chrome load it (which would fail with "site not found").
 */
public class VNPAYWebViewActivity extends AppCompatActivity {

    public static final String EXTRA_PAYMENT_URL = "PAYMENT_URL";
    public static final String EXTRA_ORDER_ID    = "ORDER_ID";

    private WebView  webView;
    private ProgressBar progress;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vnpay_webview);

        webView  = findViewById(R.id.webViewVnpay);
        progress = findViewById(R.id.progressVnpay);

        String paymentUrl = getIntent().getStringExtra(EXTRA_PAYMENT_URL);
        String orderId    = getIntent().getStringExtra(EXTRA_ORDER_ID);

        if (paymentUrl == null || paymentUrl.isEmpty()) {
            Toast.makeText(this, "Invalid payment URL", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

                // Intercept the VNP_RETURN_URL so Chrome never opens it
                if (url.contains("vnpay-return") || url.contains("10.0.2.2")) {
                    android.net.Uri uri = android.net.Uri.parse(url);
                    String responseCode = uri.getQueryParameter("vnp_ResponseCode");
                    String txnRef       = uri.getQueryParameter("vnp_TxnRef");

                    if ("00".equals(responseCode)) {
                        // Payment success
                        String finalOrderId = (txnRef != null) ? txnRef : orderId;
                        Intent intent = new Intent(VNPAYWebViewActivity.this, OrderSuccessActivity.class);
                        intent.putExtra("ORDER_CODE", finalOrderId);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                    } else {
                        // Payment failed or cancelled
                        Toast.makeText(VNPAYWebViewActivity.this,
                                "Payment cancelled or failed. Your order is saved as pending.",
                                Toast.LENGTH_LONG).show();
                    }
                    finish();
                    return true; // Prevent WebView from loading this URL
                }

                // Allow other VNPAY pages to load normally inside the WebView
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
            }
        });

        progress.setVisibility(View.VISIBLE);
        webView.loadUrl(paymentUrl);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
