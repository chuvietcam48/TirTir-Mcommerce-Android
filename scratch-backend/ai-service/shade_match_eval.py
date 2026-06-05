"""
Shade Match Accuracy Evaluation on Real Face Images (n=34)
===========================================================
For each image:
  1. Extract dominant face skin colour (cheek ROI average, BGR→RGB)
  2. POST to /api/v1/shades/match → get deltaE for top-1, top-2, top-3, top-5
  3. Compute acceptability tiers:
       ΔE < 1.0  → imperceptible
       ΔE < 2.0  → just-noticeable difference (JND)
       ΔE < 3.5  → acceptable cosmetic match (industry standard)
       ΔE < 5.0  → noticeable but usable
       ΔE ≥ 5.0  → poor match
  4. Break down by Fitzpatrick skin tone category
  5. Generate bar + scatter figures
"""

import os, sys, cv2, json, csv, requests
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from datetime import datetime

# ── Config ────────────────────────────────────────────────────────────────────
IMG_DIR    = os.path.join(os.path.dirname(__file__), "real_eval_images")
OUT_DIR    = os.path.join(os.path.dirname(__file__), "eval_results")
FIG_DIR    = os.path.join(OUT_DIR, "figures")
API_URL    = "http://localhost:5001/api/v1/shades/match"
SKIN_TYPES = ["Normal", "Oily", "Dry", "Combination"]

os.makedirs(FIG_DIR, exist_ok=True)

# Fitzpatrick-like tone categories (by L* lightness from extracted LAB)
# Based on ITA° (Individual Typology Angle) approximation via L* alone
def tone_category(L):
    if L >= 70:   return "Fair (I-II)"
    elif L >= 58: return "Light (II-III)"
    elif L >= 46: return "Medium (III-IV)"
    elif L >= 36: return "Tan (IV-V)"
    else:         return "Deep (V-VI)"

# ── Skin colour extraction (cheek ROI average) ─────────────────────────────────
# Use simple face-centre crop to approximate cheek region
# (MediaPipe not available in this script → use geometric estimate)
def extract_skin_rgb(img_path):
    img = cv2.imread(img_path)
    if img is None:
        return None, None
    h, w = img.shape[:2]

    # Central 40% of image, lower half → approximate cheek/skin region
    y1, y2 = int(h * 0.35), int(h * 0.70)
    x1, x2 = int(w * 0.20), int(w * 0.80)
    roi = img[y1:y2, x1:x2]

    # Convert to HSV, keep only skin-like hues (H:0-25 or 340-360, S:20-150)
    hsv = cv2.cvtColor(roi, cv2.COLOR_BGR2HSV)
    # Skin hue mask (broad range to cover diverse tones)
    mask1 = cv2.inRange(hsv, np.array([0,  15, 60]), np.array([25, 200, 255]))
    mask2 = cv2.inRange(hsv, np.array([160,15, 60]), np.array([180,200, 255]))
    mask  = cv2.bitwise_or(mask1, mask2)

    skin_pixels = roi[mask > 0]
    if len(skin_pixels) < 50:
        # Fallback: use full ROI mean
        skin_pixels = roi.reshape(-1, 3)

    mean_bgr = skin_pixels.mean(axis=0)
    r, g, b = int(mean_bgr[2]), int(mean_bgr[1]), int(mean_bgr[0])

    # Compute L* for tone categorisation
    lab = cv2.cvtColor(np.array([[mean_bgr]], dtype=np.uint8), cv2.COLOR_BGR2Lab)
    L = float(lab[0, 0, 0]) * 100.0 / 255.0

    return (r, g, b), L

# ── Call shade match API ───────────────────────────────────────────────────────
def call_shade_match(r, g, b, skin_type="Normal"):
    try:
        resp = requests.post(API_URL,
                             json={"r": r, "g": g, "b": b, "skinType": skin_type},
                             timeout=8)
        resp.raise_for_status()
        return resp.json()
    except Exception as e:
        print(f"  API error: {e}")
        return []

# ── Main ───────────────────────────────────────────────────────────────────────
def run():
    img_files = sorted(
        f for f in os.listdir(IMG_DIR)
        if f.lower().endswith((".png", ".jpg", ".jpeg"))
    )
    print(f"Processing {len(img_files)} images against shade database...")
    print("=" * 65)

    rows       = []
    top1_deltas = []
    top3_min   = []
    tone_groups = {}

    for fname in img_files:
        path = os.path.join(IMG_DIR, fname)
        rgb, L = extract_skin_rgb(path)
        if rgb is None:
            print(f"  SKIP: {fname}")
            continue

        r, g, b = rgb
        results = call_shade_match(r, g, b, skin_type="Normal")
        if not results:
            continue

        tone = tone_category(L)
        d1   = float(results[0]["deltaE"]) if len(results) >= 1 else None
        d3   = min(float(x["deltaE"]) for x in results[:3]) if len(results) >= 3 else d1
        d5   = min(float(x["deltaE"]) for x in results[:5]) if len(results) >= 5 else d3
        d_all= [float(x["deltaE"]) for x in results]

        top1_name = results[0].get("Shade_Name", "?")
        top1_hex  = results[0].get("Hex_Code", "#888")
        top1_undertone = results[0].get("predictedUndertone", "?")

        tier_top1 = (
            "Imperceptible (ΔE<1)"   if d1 < 1.0 else
            "JND (ΔE<2)"             if d1 < 2.0 else
            "Acceptable (ΔE<3.5)"    if d1 < 3.5 else
            "Noticeable (ΔE<5)"      if d1 < 5.0 else
            "Poor (ΔE≥5)"
        )

        rows.append({
            "filename":      fname,
            "skin_tone_cat": tone,
            "L_star":        round(L, 1),
            "extracted_R":   r, "extracted_G": g, "extracted_B": b,
            "top1_shade":    top1_name,
            "top1_hex":      top1_hex,
            "top1_undertone":top1_undertone,
            "top1_deltaE":   round(d1, 3),
            "top3_min_deltaE":round(d3, 3),
            "top5_min_deltaE":round(d5, 3),
            "tier":          tier_top1,
        })

        top1_deltas.append(d1)
        top3_min.append(d3)

        if tone not in tone_groups:
            tone_groups[tone] = []
        tone_groups[tone].append(d1)

        print(f"  {fname:20s} L={L:5.1f} ({tone:15s}) "
              f"→ top1 '{top1_name}' ΔE={d1:.2f} [{tier_top1.split('(')[0].strip()}]")

    # ── Stats ─────────────────────────────────────────────────────────────────
    n = len(top1_deltas)
    mean_d1  = np.mean(top1_deltas)
    med_d1   = np.median(top1_deltas)
    p95_d1   = np.percentile(top1_deltas, 95)
    mean_d3  = np.mean(top3_min)

    pct_imp  = 100 * sum(d < 1.0 for d in top1_deltas) / n
    pct_jnd  = 100 * sum(d < 2.0 for d in top1_deltas) / n
    pct_acc  = 100 * sum(d < 3.5 for d in top1_deltas) / n
    pct_not  = 100 * sum(d < 5.0 for d in top1_deltas) / n
    pct_poor = 100 * sum(d >= 5.0 for d in top1_deltas) / n

    pct_acc_top3 = 100 * sum(d < 3.5 for d in top3_min) / n

    print("\n" + "=" * 65)
    print(f"  n = {n} real face images")
    print(f"  Top-1 ΔE₀₀: mean={mean_d1:.2f}  median={med_d1:.2f}  P95={p95_d1:.2f}")
    print(f"  Top-3 best ΔE₀₀: mean={mean_d3:.2f}")
    print()
    print(f"  Top-1 acceptability:")
    print(f"    ΔE < 1.0 (imperceptible):  {pct_imp:5.1f}%")
    print(f"    ΔE < 2.0 (JND):            {pct_jnd:5.1f}%")
    print(f"    ΔE < 3.5 (acceptable):     {pct_acc:5.1f}%  ← industry standard")
    print(f"    ΔE < 5.0 (noticeable):     {pct_not:5.1f}%")
    print(f"    ΔE ≥ 5.0 (poor):           {pct_poor:5.1f}%")
    print(f"  Top-3 acceptable (ΔE<3.5):   {pct_acc_top3:5.1f}%")

    print("\n  Per skin-tone breakdown (Top-1 mean ΔE):")
    tone_order = ["Fair (I-II)", "Light (II-III)", "Medium (III-IV)",
                  "Tan (IV-V)", "Deep (V-VI)"]
    tone_stats = {}
    for tone in tone_order:
        vals = tone_groups.get(tone, [])
        if vals:
            ts = {
                "n": len(vals),
                "mean_deltaE": round(float(np.mean(vals)), 3),
                "pct_acceptable": round(100 * sum(v < 3.5 for v in vals) / len(vals), 1),
            }
            tone_stats[tone] = ts
            print(f"    {tone:18s}  n={len(vals):2d}  "
                  f"mean ΔE={np.mean(vals):.2f}  "
                  f"acceptable={ts['pct_acceptable']:.0f}%")

    # ── CSV ───────────────────────────────────────────────────────────────────
    csv_path = os.path.join(OUT_DIR, "shade_match_eval.csv")
    with open(csv_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader(); writer.writerows(rows)

    # ── JSON ──────────────────────────────────────────────────────────────────
    summary = {
        "evaluation": "shade_match_real_images",
        "date": datetime.now().isoformat(),
        "n_images": n,
        "methodology": (
            "Dominant skin colour extracted from central face ROI (35-70% height, "
            "20-80% width) using HSV skin-hue masking. Fallback to full ROI mean "
            "when <50 skin pixels detected. RGB values submitted to POST /api/v1/shades/match. "
            "deltaE₀₀ sourced directly from CIE LAB distance computed in shade.controller.js."
        ),
        "top1_deltaE": {
            "mean": round(float(mean_d1), 3),
            "median": round(float(med_d1), 3),
            "p95": round(float(p95_d1), 3),
        },
        "top3_min_deltaE": {"mean": round(float(mean_d3), 3)},
        "acceptability_pct": {
            "top1_imperceptible_dE_lt_1": round(pct_imp, 1),
            "top1_jnd_dE_lt_2":          round(pct_jnd, 1),
            "top1_acceptable_dE_lt_3_5": round(pct_acc, 1),
            "top1_noticeable_dE_lt_5":   round(pct_not, 1),
            "top1_poor_dE_ge_5":         round(pct_poor, 1),
            "top3_acceptable_dE_lt_3_5": round(pct_acc_top3, 1),
        },
        "per_tone_breakdown": tone_stats,
        "limitation": (
            "Skin colour extracted via geometric ROI without face landmark detection. "
            "Hair, background, and non-skin pixels may bias the mean RGB. "
            "Ground-truth 'correct shade' is unavailable; deltaE measures colour "
            "distance between extracted skin colour and recommended shade, not "
            "dermatologist-validated foundation match."
        ),
    }
    json_path = os.path.join(OUT_DIR, "shade_match_eval_summary.json")
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2, ensure_ascii=False)

    # ── Figures ───────────────────────────────────────────────────────────────
    _plot_deltaE_distribution(top1_deltas, top3_min, n, pct_acc, pct_acc_top3)
    _plot_tone_breakdown(tone_stats, tone_order)
    _plot_scatter(rows)

    print(f"\n  CSV:  {csv_path}")
    print(f"  JSON: {json_path}")
    print(f"  Figures: {FIG_DIR}/shade_match_*.png")
    return summary


# ── Plotting ─────────────────────────────────────────────────────────────────

def _plot_deltaE_distribution(top1, top3, n, pct_acc, pct_acc_top3):
    fig, axes = plt.subplots(1, 2, figsize=(13, 5))

    # Left: histogram of top-1 ΔE
    ax = axes[0]
    bins = np.arange(0, max(top1) + 1.0, 0.5)
    ax.hist(top1, bins=bins, color="#5C85D6", edgecolor="white", alpha=0.85, label="Top-1 ΔE")
    ax.hist(top3, bins=bins, color="#D6775C", edgecolor="white", alpha=0.55, label="Top-3 best ΔE")
    for thr, col, lbl in [(1.0,"#2ecc71","ΔE=1 imperceptible"),
                           (2.0,"#f39c12","ΔE=2 JND"),
                           (3.5,"#e74c3c","ΔE=3.5 acceptable")]:
        ax.axvline(thr, color=col, linestyle="--", linewidth=1.5, label=lbl)
    ax.set_xlabel("ΔE₀₀"); ax.set_ylabel("Count")
    ax.set_title(f"ΔE₀₀ Distribution (n={n} real images)")
    ax.legend(fontsize=7.5); ax.grid(True, alpha=0.3)

    # Right: acceptability tier pie
    ax2 = axes[1]
    sizes = [
        sum(d < 1.0 for d in top1),
        sum(1.0 <= d < 2.0 for d in top1),
        sum(2.0 <= d < 3.5 for d in top1),
        sum(3.5 <= d < 5.0 for d in top1),
        sum(d >= 5.0 for d in top1),
    ]
    labels = [f"Imperceptible\n(ΔE<1)\n{sizes[0]}",
              f"JND\n(ΔE<2)\n{sizes[1]}",
              f"Acceptable\n(ΔE<3.5)\n{sizes[2]}",
              f"Noticeable\n(ΔE<5)\n{sizes[3]}",
              f"Poor\n(ΔE≥5)\n{sizes[4]}"]
    colors = ["#2ecc71","#a8e063","#f39c12","#e67e22","#e74c3c"]
    wedge_filter = [(s, l, c) for s, l, c in zip(sizes, labels, colors) if s > 0]
    if wedge_filter:
        ws, ls, cs = zip(*wedge_filter)
        ax2.pie(ws, labels=ls, colors=cs, autopct="%1.0f%%",
                startangle=140, textprops={"fontsize": 8})
    ax2.set_title(f"Top-1 Acceptability Tiers\n(ΔE<3.5 acceptable: {pct_acc:.0f}%  |  Top-3: {pct_acc_top3:.0f}%)")

    plt.tight_layout()
    out = os.path.join(FIG_DIR, "shade_match_distribution.png")
    plt.savefig(out, dpi=150, bbox_inches="tight"); plt.close()
    print(f"  Saved: {out}")


def _plot_tone_breakdown(tone_stats, tone_order):
    tones   = [t for t in tone_order if t in tone_stats]
    means   = [tone_stats[t]["mean_deltaE"] for t in tones]
    pcts    = [tone_stats[t]["pct_acceptable"] for t in tones]
    ns      = [tone_stats[t]["n"] for t in tones]
    colors  = ["#F5CBA7","#F0B27A","#CA6F1E","#884EA0","#1A5276"][:len(tones)]

    fig, axes = plt.subplots(1, 2, figsize=(13, 5))

    ax = axes[0]
    bars = ax.bar(tones, means, color=colors, edgecolor="white", alpha=0.9)
    ax.axhline(3.5, color="#e74c3c", linestyle="--", linewidth=1.5, label="ΔE=3.5 threshold")
    for bar, n_val, m in zip(bars, ns, means):
        ax.text(bar.get_x()+bar.get_width()/2, m+0.05, f"n={n_val}\nΔE={m:.2f}",
                ha="center", va="bottom", fontsize=8)
    ax.set_ylabel("Mean Top-1 ΔE₀₀"); ax.set_ylim(0, max(means)+1.5)
    ax.set_title("Mean ΔE₀₀ by Fitzpatrick Skin Tone Category")
    ax.legend(fontsize=8); ax.tick_params(axis='x', rotation=20)
    ax.grid(True, alpha=0.3, axis="y")

    ax2 = axes[1]
    bars2 = ax2.bar(tones, pcts, color=colors, edgecolor="white", alpha=0.9)
    ax2.axhline(50, color="gray", linestyle=":", linewidth=1)
    for bar, n_val, p in zip(bars2, ns, pcts):
        ax2.text(bar.get_x()+bar.get_width()/2, p+1, f"{p:.0f}%",
                 ha="center", va="bottom", fontsize=9, fontweight="bold")
    ax2.set_ylabel("% with Top-1 ΔE < 3.5 (acceptable)")
    ax2.set_ylim(0, 115); ax2.set_title("Acceptable Match Rate by Skin Tone")
    ax2.tick_params(axis='x', rotation=20)
    ax2.grid(True, alpha=0.3, axis="y")

    plt.tight_layout()
    out = os.path.join(FIG_DIR, "shade_match_by_tone.png")
    plt.savefig(out, dpi=150, bbox_inches="tight"); plt.close()
    print(f"  Saved: {out}")


def _plot_scatter(rows):
    fig, ax = plt.subplots(figsize=(10, 5))
    tone_colors = {
        "Fair (I-II)":    "#F5CBA7",
        "Light (II-III)": "#F0B27A",
        "Medium (III-IV)":"#CA6F1E",
        "Tan (IV-V)":     "#884EA0",
        "Deep (V-VI)":    "#1A5276",
    }
    for row in rows:
        tc = row["skin_tone_cat"]
        ax.scatter(row["L_star"], row["top1_deltaE"],
                   color=tone_colors.get(tc, "#888"),
                   edgecolors="white", s=70, zorder=5)
    ax.axhline(3.5, color="#e74c3c", linestyle="--", linewidth=1.5, label="ΔE=3.5 threshold")
    ax.axhline(2.0, color="#f39c12", linestyle=":", linewidth=1.2, label="ΔE=2.0 JND")
    patches = [mpatches.Patch(color=c, label=t) for t, c in tone_colors.items()
               if any(r["skin_tone_cat"] == t for r in rows)]
    leg1 = ax.legend(handles=patches, fontsize=8, loc="upper left", title="Skin Tone")
    ax.add_artist(leg1)
    ax.legend(fontsize=8, loc="upper right")
    ax.set_xlabel("Skin Lightness L* (extracted from image)")
    ax.set_ylabel("Top-1 ΔE₀₀")
    ax.set_title("Shade Match ΔE₀₀ vs Skin Lightness — n=34 Real Images")
    ax.grid(True, alpha=0.3)
    out = os.path.join(FIG_DIR, "shade_match_scatter.png")
    plt.savefig(out, dpi=150, bbox_inches="tight"); plt.close()
    print(f"  Saved: {out}")


if __name__ == "__main__":
    run()
