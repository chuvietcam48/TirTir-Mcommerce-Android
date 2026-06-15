package com.example.tirtir_mcommerce.network;

import androidx.annotation.Nullable;

import com.example.tirtir_mcommerce.model.RefreshTokenRequest;
import com.example.tirtir_mcommerce.model.RefreshTokenResponse;
import com.example.tirtir_mcommerce.utils.SharedPrefsManager;

import java.io.IOException;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Route;
import retrofit2.Response;

/**
 * Refreshes an expired access token once, then retries the original request.
 */
public class TokenAuthenticator implements Authenticator {
    private static final Object REFRESH_LOCK = new Object();

    private final SharedPrefsManager prefsManager;

    public TokenAuthenticator(SharedPrefsManager prefsManager) {
        this.prefsManager = prefsManager;
    }

    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, okhttp3.Response response) {
        if (responseCount(response) >= 2
                || response.request().url().encodedPath().endsWith("/auth/refresh-token")) {
            return null;
        }

        String refreshToken = prefsManager.getRefreshToken();
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            return null;
        }

        synchronized (REFRESH_LOCK) {
            String requestAuthorization = response.request().header("Authorization");
            String latestAccessToken = prefsManager.getToken();
            if (latestAccessToken != null
                    && !("Bearer " + latestAccessToken).equals(requestAuthorization)) {
                return retryWithToken(response, latestAccessToken);
            }

            try {
                ApiService refreshApi = RetrofitClient.getClient().create(ApiService.class);
                Response<RefreshTokenResponse> refreshResponse =
                        refreshApi.refreshToken(new RefreshTokenRequest(refreshToken)).execute();
                RefreshTokenResponse body = refreshResponse.body();

                if (refreshResponse.isSuccessful()
                        && body != null
                        && body.isSuccess()
                        && body.getToken() != null
                        && !body.getToken().trim().isEmpty()) {
                    prefsManager.saveSession(body.getToken(), body.getRefreshToken());
                    return retryWithToken(response, body.getToken());
                }

                int statusCode = refreshResponse.code();
                if (statusCode == 400 || statusCode == 401 || statusCode == 403) {
                    prefsManager.clearAuthSession();
                }
            } catch (IOException ignored) {
                return null;
            }

            return null;
        }
    }

    private Request retryWithToken(okhttp3.Response response, String token) {
        return response.request().newBuilder()
                .header("Authorization", "Bearer " + token)
                .build();
    }

    private int responseCount(okhttp3.Response response) {
        int count = 1;
        while ((response = response.priorResponse()) != null) {
            count++;
        }
        return count;
    }
}
