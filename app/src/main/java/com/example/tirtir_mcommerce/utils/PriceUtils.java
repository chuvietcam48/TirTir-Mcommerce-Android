package com.example.tirtir_mcommerce.utils;

import java.text.NumberFormat;
import java.util.Locale;

public class PriceUtils {

    private static final NumberFormat currencyFormat = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    /**
     * Normalizes a raw price. If it's < 1000, it assumes USD and converts to VND (x 25,000).
     * If >= 1000, assumes it's already VND and keeps it as is.
     */
    public static double normalizePrice(double rawPrice) {
        if (rawPrice < 1000 && rawPrice > 0) {
            return rawPrice * 25000.0;
        }
        return rawPrice;
    }

    /**
     * Formats a normalized price into Vietnamese VND string, e.g. "350.000 đ".
     */
    public static String formatPriceVnd(double normalizedPrice) {
        return currencyFormat.format(normalizedPrice) + " đ";
    }
}
