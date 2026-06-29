package com.example.tirtir_mcommerce.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.tirtir_mcommerce.R;
import com.example.tirtir_mcommerce.repository.AuthRepository;
import com.google.android.material.button.MaterialButton;

public class VerifyEmailActivity extends AppCompatActivity {

    private EditText otp1, otp2, otp3, otp4;
    private AuthRepository authRepository;
    private String email;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_email);

        authRepository = new AuthRepository(this);
        email = getIntent().getStringExtra("EMAIL");

        TextView tvVerifySubtitle = findViewById(R.id.tvVerifySubtitle);
        if (email != null && !email.isEmpty()) {
            tvVerifySubtitle.setText("We've sent a 4-digit code to\n" + email);
        }

        ((Toolbar) findViewById(R.id.toolbar)).setNavigationOnClickListener(v -> finish());

        otp1 = findViewById(R.id.otp1);
        otp2 = findViewById(R.id.otp2);
        otp3 = findViewById(R.id.otp3);
        otp4 = findViewById(R.id.otp4);

        setupOtpInputs();

        MaterialButton btnVerify = findViewById(R.id.btnVerify);
        btnVerify.setOnClickListener(v -> {
            String otp = otp1.getText().toString() + otp2.getText().toString() +
                         otp3.getText().toString() + otp4.getText().toString();

            if (otp.length() < 4) {
                Toast.makeText(this, "Please enter all 4 digits", Toast.LENGTH_SHORT).show();
                return;
            }

            btnVerify.setEnabled(false);

            authRepository.verifyOTP(email, otp,
                resetToken -> {
                    btnVerify.setEnabled(true);
                    Intent intent = new Intent(this, NewPasswordActivity.class);
                    intent.putExtra("EMAIL", email);
                    intent.putExtra("RESET_TOKEN", resetToken);
                    startActivity(intent);
                    finish();
                },
                error -> {
                    btnVerify.setEnabled(true);
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                });
        });
    }

    private void setupOtpInputs() {
        otp1.addTextChangedListener(new OtpTextWatcher(otp1, otp2, null));
        otp2.addTextChangedListener(new OtpTextWatcher(otp2, otp3, otp1));
        otp3.addTextChangedListener(new OtpTextWatcher(otp3, otp4, otp2));
        otp4.addTextChangedListener(new OtpTextWatcher(otp4, null, otp3));

        // Handle backspace properly
        setupKeyListener(otp2, otp1);
        setupKeyListener(otp3, otp2);
        setupKeyListener(otp4, otp3);
    }

    private void setupKeyListener(EditText current, EditText previous) {
        current.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DEL && current.getText().toString().isEmpty()) {
                previous.requestFocus();
                return true;
            }
            return false;
        });
    }

    private class OtpTextWatcher implements TextWatcher {
        private final View currentView;
        private final View nextView;
        private final View previousView;

        public OtpTextWatcher(View currentView, View nextView, View previousView) {
            this.currentView = currentView;
            this.nextView = nextView;
            this.previousView = previousView;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            String text = s.toString();
            if (text.length() == 1 && nextView != null) {
                nextView.requestFocus();
            } else if (text.isEmpty() && previousView != null) {
                // Focus handled by OnKeyListener for backspace, but we keep this as fallback
            }
        }
    }
}
