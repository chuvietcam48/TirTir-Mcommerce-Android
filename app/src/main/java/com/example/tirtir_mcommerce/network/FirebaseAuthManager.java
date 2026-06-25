package com.example.tirtir_mcommerce.network;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.concurrent.TimeUnit;

public class FirebaseAuthManager {

    private static final String TAG = "FirebaseAuthManager";
    public static final int RC_SIGN_IN = 9001;

    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;

    public interface AuthCallback {
        void onSuccess(FirebaseUser user);
        void onError(Exception exception);
    }

    public FirebaseAuthManager(Context context) {
        mAuth = FirebaseAuth.getInstance();

        // Attempt to get the default web client ID from strings resources
        // This requires google-services.json to have oauth_client populated.
        String defaultWebClientId = null;
        try {
            int resId = context.getResources().getIdentifier("default_web_client_id", "string", context.getPackageName());
            if (resId != 0) {
                defaultWebClientId = context.getString(resId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not find default_web_client_id. Did you add SHA-1 and enable Google Sign in?", e);
        }

        if (defaultWebClientId != null && !defaultWebClientId.isEmpty()) {
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(defaultWebClientId)
                    .requestEmail()
                    .build();
            mGoogleSignInClient = GoogleSignIn.getClient(context, gso);
        } else {
            Log.w(TAG, "Google Sign-In is not initialized because default_web_client_id is missing.");
        }
    }

    public FirebaseAuth getAuth() {
        return mAuth;
    }

    public FirebaseUser getCurrentUser() {
        return mAuth.getCurrentUser();
    }

    // --- Email & Password ---
    public void signUpWithEmail(String email, String password, AuthCallback callback) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onSuccess(mAuth.getCurrentUser());
                    } else {
                        callback.onError(task.getException());
                    }
                });
    }

    public void signInWithEmail(String email, String password, AuthCallback callback) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onSuccess(mAuth.getCurrentUser());
                    } else {
                        callback.onError(task.getException());
                    }
                });
    }

    // --- Google Sign-In ---
    public void startGoogleSignIn(Activity activity) {
        if (mGoogleSignInClient != null) {
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            activity.startActivityForResult(signInIntent, RC_SIGN_IN);
        } else {
            Log.e(TAG, "GoogleSignInClient is null. Make sure to provide web client ID.");
            // You can also notify the UI via a callback here
        }
    }

    public void handleGoogleSignInResult(Intent data, AuthCallback callback) {
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account != null) {
                firebaseAuthWithGoogle(account.getIdToken(), callback);
            }
        } catch (ApiException e) {
            Log.e(TAG, "Google sign in failed", e);
            callback.onError(e);
        }
    }

    private void firebaseAuthWithGoogle(String idToken, AuthCallback callback) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onSuccess(mAuth.getCurrentUser());
                    } else {
                        callback.onError(task.getException());
                    }
                });
    }

    // --- Phone Authentication ---
    public void startPhoneAuth(String phoneNumber, Activity activity, PhoneAuthProvider.OnVerificationStateChangedCallbacks callbacks) {
        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth)
                .setPhoneNumber(phoneNumber)       // Phone number to verify
                .setTimeout(60L, TimeUnit.SECONDS) // Timeout and unit
                .setActivity(activity)             // Activity (for callback binding)
                .setCallbacks(callbacks)           // OnVerificationStateChangedCallbacks
                .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    public void verifyPhoneNumberWithCode(String verificationId, String code, AuthCallback callback) {
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onSuccess(mAuth.getCurrentUser());
                    } else {
                        callback.onError(task.getException());
                    }
                });
    }

    public void signOut() {
        mAuth.signOut();
        if (mGoogleSignInClient != null) {
            mGoogleSignInClient.signOut();
        }
    }
}
