/**
 * Converts RGB to LAB
 * @param {number} r - Red (0-255)
 * @param {number} g - Green (0-255)
 * @param {number} b - Blue (0-255)
 * @returns {{L: number, a: number, b: number}}
 */
function rgbToLab(r, g, b) {
  let r_ = r / 255, g_ = g / 255, b_ = b / 255;

  if (r_ > 0.04045) r_ = Math.pow(((r_ + 0.055) / 1.055), 2.4);
  else r_ = r_ / 12.92;

  if (g_ > 0.04045) g_ = Math.pow(((g_ + 0.055) / 1.055), 2.4);
  else g_ = g_ / 12.92;

  if (b_ > 0.04045) b_ = Math.pow(((b_ + 0.055) / 1.055), 2.4);
  else b_ = b_ / 12.92;

  r_ = r_ * 100;
  g_ = g_ * 100;
  b_ = b_ * 100;

  let X = r_ * 0.4124 + g_ * 0.3576 + b_ * 0.1805;
  let Y = r_ * 0.2126 + g_ * 0.7152 + b_ * 0.0722;
  let Z = r_ * 0.0193 + g_ * 0.1192 + b_ * 0.9505;

  // Observer= 2°, Illuminant= D65
  X = X / 95.047;
  Y = Y / 100.000;
  Z = Z / 108.883;

  if (X > 0.008856) X = Math.pow(X, 1/3);
  else X = (7.787 * X) + (16 / 116);

  if (Y > 0.008856) Y = Math.pow(Y, 1/3);
  else Y = (7.787 * Y) + (16 / 116);

  if (Z > 0.008856) Z = Math.pow(Z, 1/3);
  else Z = (7.787 * Z) + (16 / 116);

  const L = (116 * Y) - 16;
  const a = 500 * (X - Y);
  const b_val = 200 * (Y - Z);

  return { L, a, b: b_val };
}

/**
 * Calculates DeltaE 2000 between two LAB colors
 * @param {{L: number, a: number, b: number}} lab1 
 * @param {{L: number, a: number, b: number}} lab2 
 * @returns {number}
 */
function deltaE00(lab1, lab2) {
  const { L: L1, a: a1, b: b1 } = lab1;
  const { L: L2, a: a2, b: b2 } = lab2;

  const deg2rad = Math.PI / 180;
  const rad2deg = 180 / Math.PI;

  const kL = 1;
  const kC = 1;
  const kH = 1;

  const C1 = Math.sqrt(a1 * a1 + b1 * b1);
  const C2 = Math.sqrt(a2 * a2 + b2 * b2);
  const C_avg = (C1 + C2) / 2;

  const G = 0.5 * (1 - Math.sqrt(Math.pow(C_avg, 7) / (Math.pow(C_avg, 7) + Math.pow(25, 7))));

  const a1_p = (1 + G) * a1;
  const a2_p = (1 + G) * a2;

  const C1_p = Math.sqrt(a1_p * a1_p + b1 * b1);
  const C2_p = Math.sqrt(a2_p * a2_p + b2 * b2);

  const h1_p = (Math.atan2(b1, a1_p) * rad2deg + 360) % 360;
  const h2_p = (Math.atan2(b2, a2_p) * rad2deg + 360) % 360;

  const L_avg = (L1 + L2) / 2;
  const C_avg_p = (C1_p + C2_p) / 2;

  let h_avg_p = (h1_p + h2_p) / 2;
  if (Math.abs(h1_p - h2_p) > 180) {
      h_avg_p += 180;
  }

  const T = 1 - 0.17 * Math.cos(deg2rad * (h_avg_p - 30))
              + 0.24 * Math.cos(deg2rad * (2 * h_avg_p))
              + 0.32 * Math.cos(deg2rad * (3 * h_avg_p + 6))
              - 0.20 * Math.cos(deg2rad * (4 * h_avg_p - 63));

  let delta_h_p = h2_p - h1_p;
  if (Math.abs(delta_h_p) > 180) {
      if (h2_p <= h1_p) delta_h_p += 360;
      else delta_h_p -= 360;
  }

  const delta_L_p = L2 - L1;
  const delta_C_p = C2_p - C1_p;
  const delta_H_p = 2 * Math.sqrt(C1_p * C2_p) * Math.sin(deg2rad * (delta_h_p / 2));

  const S_L = 1 + (0.015 * Math.pow(L_avg - 50, 2)) / Math.sqrt(20 + Math.pow(L_avg - 50, 2));
  const S_C = 1 + 0.045 * C_avg_p;
  const S_H = 1 + 0.015 * C_avg_p * T;

  const delta_theta = 30 * Math.exp(-Math.pow((h_avg_p - 275) / 25, 2));
  const R_C = 2 * Math.sqrt(Math.pow(C_avg_p, 7) / (Math.pow(C_avg_p, 7) + Math.pow(25, 7)));
  const R_T = -Math.sin(deg2rad * (2 * delta_theta)) * R_C;

  const dE = Math.sqrt(
      Math.max(0,
          Math.pow(delta_L_p / (kL * S_L), 2) +
          Math.pow(delta_C_p / (kC * S_C), 2) +
          Math.pow(delta_H_p / (kH * S_H), 2) +
          R_T * (delta_C_p / (kC * S_C)) * (delta_H_p / (kH * S_H))
      )
  );

  return dE;
}

module.exports = { rgbToLab, deltaE00 };
