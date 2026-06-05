import { Injectable } from '@angular/core';

/**
 * F5: ColorService — extracted from ShadeFinderComponent
 * Handles color science operations: RGB ↔ Lab conversion, skin color validation,
 * and live metric estimation.
 */
@Injectable({ providedIn: 'root' })
export class ColorService {
  /**
   * Validate skin color for adequate lighting conditions.
   * Returns { isValid, reason? }
   */
  validateSkinColor(r: number, g: number, b: number): { isValid: boolean; reason?: string } {
    const lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
    if (lum <= 50) return { isValid: false, reason: '💡 Not enough light. Please turn on a light.' };
    if (lum > 220) return { isValid: false, reason: '☀️ Too bright. Move to shade.' };
    const lab = this.rgbToLab(r, g, b);
    // More lenient for darker skin: L < 15 instead of L < 40
    if (lab.L < 15) return { isValid: false, reason: 'Lighting too dark. Please adjust.' };
    // Allow warm tones: a up to 50 instead of 45
    if (lab.a < 3 || lab.a > 50) return { isValid: false, reason: 'Color cast detected. Check lighting.' };
    // Allow more yellow for darker skin: b up to 60
    if (lab.b < 5 || lab.b > 60) return { isValid: false, reason: 'Yellow/blue tint detected. Adjust lighting.' };
    return { isValid: true };
  }

  /**
   * Convert RGB (0-255) to CIE Lab (D65 illuminant).
   * Standard sRGB → XYZ → Lab pipeline.
   */
  rgbToLab(r: number, g: number, b: number): { L: number; a: number; b: number } {
    let r_ = r / 255, g_ = g / 255, b_ = b / 255;
    if (r_ > 0.04045) r_ = Math.pow((r_ + 0.055) / 1.055, 2.4); else r_ /= 12.92;
    if (g_ > 0.04045) g_ = Math.pow((g_ + 0.055) / 1.055, 2.4); else g_ /= 12.92;
    if (b_ > 0.04045) b_ = Math.pow((b_ + 0.055) / 1.055, 2.4); else b_ /= 12.92;
    r_ *= 100; g_ *= 100; b_ *= 100;
    let X = r_ * 0.4124 + g_ * 0.3576 + b_ * 0.1805;
    let Y = r_ * 0.2126 + g_ * 0.7152 + b_ * 0.0722;
    let Z = r_ * 0.0193 + g_ * 0.1192 + b_ * 0.9505;
    X /= 95.047; Y /= 100; Z /= 108.883;
    const f = (t: number) => t > 0.008856 ? Math.pow(t, 1 / 3) : 7.787 * t + 16 / 116;
    return { L: 116 * f(Y) - 16, a: 500 * (f(X) - f(Y)), b: 200 * (f(Y) - f(Z)) };
  }

  /**
   * Estimate live skin metrics from smoothed RGB values.
   * NOTE: These are RGB-based APPROXIMATIONS, not clinical measurements.
   */
  estimateLiveMetrics(
    smoothedColor: { r: number; g: number; b: number },
    facePointColors: { r: number; g: number; b: number }[]
  ): { moisture: number; pores: number; redness: number; evenness: number } {
    const { r, g, b: blue } = smoothedColor;
    const lum = 0.2126 * r + 0.7152 * g + 0.0722 * blue;

    // Moisture: higher blue channel relative to lum = more hydrated appearance
    const moisture = Math.min(99, Math.max(20, Math.round(40 + (blue / lum) * 35)));
    // Redness: elevated red channel relative to green
    const redness = Math.min(60, Math.max(5, Math.round(((r - g) / 255) * 120)));
    // Pores: variance across face points (lower variance = smoother)
    const variance = facePointColors.reduce(
      (acc, c) => acc + Math.abs(c.r - r) + Math.abs(c.g - g), 0
    ) / facePointColors.length;
    const pores = Math.min(50, Math.max(5, Math.round(variance * 0.8)));
    // Evenness: inverse of variance
    const evenness = Math.min(99, Math.max(40, Math.round(99 - pores)));

    return { moisture, redness, pores, evenness };
  }
}
