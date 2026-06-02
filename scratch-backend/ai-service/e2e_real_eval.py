"""
End-to-End Real-Image Evaluation via Production Pipeline
=========================================================
Sends each image to the ACTUAL /api/ai/analyze-face endpoint
(Python AI service: MediaPipe FaceLandmarker → CLAHE → LAB → concern detection)
then to /api/v1/shades/match with the extracted skin colour.

This IS true end-to-end validation — same code path as the frontend uses.

Ground truth from real_image_eval.csv (manual single-annotator labels).
"""

import os, sys, cv2, json, csv, base64, requests
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from datetime import datetime

IMG_DIR   = os.path.join(os.path.dirname(__file__), "real_eval_images")
OUT_DIR   = os.path.join(os.path.dirname(__file__), "eval_results")
FIG_DIR   = os.path.join(OUT_DIR, "figures")
GT_CSV    = os.path.join(OUT_DIR, "real_image_eval.csv")
AI_URL    = "http://localhost:8000/analyze"
SHADE_URL = "http://localhost:5001/api/v1/shades/match"

CONCERN_LABELS = [
    "Acne/Blemishes", "Sensitive/Redness",
    "Dark Circles",   "Visible Pores", "Oily Skin",
]

os.makedirs(FIG_DIR, exist_ok=True)

# ── Load ground truth from previous manual labelling ────────────────────────
def load_ground_truth():
    gt = {}
    if not os.path.exists(GT_CSV):
        return gt
    with open(GT_CSV, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            labels = row.get("ground_truth_labels", "")
            gt[row["filename"]] = set(
                x.strip() for x in labels.split("|") if x.strip() and x.strip() != "None"
            )
    return gt

# ── Encode image to base64 data URI (same format as frontend canvas) ────────
def to_base64(path):
    img = cv2.imread(path)
    if img is None:
        return None
    img = cv2.resize(img, (640, 480))
    _, buf = cv2.imencode(".jpg", img, [cv2.IMWRITE_JPEG_QUALITY, 85])
    b64 = base64.b64encode(buf).decode("utf-8")
    return f"data:image/jpeg;base64,{b64}"

# ── Call production AI endpoint ──────────────────────────────────────────────
def call_analyze(image_data):
    import time
    for attempt in range(4):
        try:
            r = requests.post(AI_URL,
                              json={"image_base64": image_data},
                              timeout=30)
            if r.status_code == 429:
                wait = 12 * (attempt + 1)
                print(f"    [rate limit] waiting {wait}s...")
                time.sleep(wait)
                continue
            r.raise_for_status()
            return r.json()
        except requests.HTTPError:
            return {"error": f"HTTP {r.status_code}"}
        except Exception as e:
            return {"error": str(e)}
    return {"error": "max retries exceeded (429)"}

def call_shade_match(r_val, g_val, b_val):
    try:
        r = requests.post(SHADE_URL,
                          json={"r": r_val, "g": g_val, "b": b_val, "skinType": "Normal"},
                          timeout=8)
        r.raise_for_status()
        return r.json()
    except Exception as e:
        return []

# ── Metrics ─────────────────────────────────────────────────────────────────
def metrics(tp, fp, fn, tn):
    P  = tp/(tp+fp) if (tp+fp) > 0 else 0.0
    R  = tp/(tp+fn) if (tp+fn) > 0 else 0.0
    F1 = 2*P*R/(P+R) if (P+R) > 0 else 0.0
    return round(P,4), round(R,4), round(F1,4)

# ── Main ─────────────────────────────────────────────────────────────────────
def run():
    ground_truth = load_ground_truth()
    img_files = sorted(
        f for f in os.listdir(IMG_DIR)
        if f.lower().endswith((".png",".jpg",".jpeg"))
    )

    print("=" * 70)
    print("  End-to-End Real-Image Evaluation — Production AI Pipeline")
    print(f"  n = {len(img_files)} images  |  AI: {AI_URL}")
    print("=" * 70)

    rows      = []
    confusion = {c: {"tp":0,"fp":0,"fn":0,"tn":0} for c in CONCERN_LABELS}
    failed    = []
    deltaEs   = []
    tone_data = {}

    for fname in img_files:
        path  = os.path.join(IMG_DIR, fname)
        b64   = to_base64(path)
        if b64 is None:
            continue

        # ── Call production AI service ──────────────────────────────────────
        result = call_analyze(b64)

        if not result.get("success", False):
            reason = str(result.get("error") or result.get("message") or "failed")
            print(f"  SKIP {fname:20s} → {reason[:60]}")
            failed.append({"filename": fname, "reason": reason})
            continue

        # Parse nested response: { success, data: { skinTone, undertone, ... } }
        data = result.get("data", {})
        skin_tone    = data.get("skinTone",  data.get("skin_tone",  "Unknown"))
        undertone    = data.get("undertone",  "Unknown")
        skin_type    = data.get("skinType",  data.get("skin_type",  "Unknown"))
        concerns_raw = data.get("concerns",   [])
        confidence   = float(data.get("confidence", 0.0))

        # Extract RGB from debug_values (LAB) or average_color
        debug = data.get("debug_values", {})
        avg_color = data.get("average_color", {})
        # Convert LAB debug values back to rough RGB via OpenCV if no direct RGB
        r_val = avg_color.get("r", 0)
        g_val = avg_color.get("g", 0)
        b_val = avg_color.get("b", 0)
        if not r_val and debug.get("L"):
            import numpy as np_cv
            L, a, b_ch = debug.get("L",128), debug.get("a",128), debug.get("b",128)
            lab_px = np_cv.array([[[int(L), int(a), int(b_ch)]]], dtype=np_cv.uint8)
            import cv2 as _cv2
            bgr = _cv2.cvtColor(lab_px, _cv2.COLOR_Lab2BGR)[0][0]
            b_val, g_val, r_val = int(bgr[0]), int(bgr[1]), int(bgr[2])

        predicted_concerns = set(concerns_raw) if isinstance(concerns_raw, list) else set()

        # ── Shade match using AI-extracted colour ───────────────────────────
        shade_results = call_shade_match(r_val, g_val, b_val) if r_val else []
        top1_dE  = float(shade_results[0]["deltaE"]) if shade_results else None
        top1_name = shade_results[0].get("Shade_Name","?") if shade_results else "N/A"

        if top1_dE is not None:
            deltaEs.append(top1_dE)
            if skin_tone not in tone_data:
                tone_data[skin_tone] = []
            tone_data[skin_tone].append(top1_dE)

        # ── Compare vs ground truth ─────────────────────────────────────────
        gt_set = ground_truth.get(fname, set())
        for concern in CONCERN_LABELS:
            gt_pos   = concern in gt_set
            pred_pos = concern in predicted_concerns
            if   gt_pos and pred_pos:     confusion[concern]["tp"] += 1
            elif not gt_pos and pred_pos: confusion[concern]["fp"] += 1
            elif gt_pos and not pred_pos: confusion[concern]["fn"] += 1
            else:                         confusion[concern]["tn"] += 1

        tier = ("Imperceptible" if top1_dE and top1_dE < 1 else
                "JND"           if top1_dE and top1_dE < 2 else
                "Acceptable"    if top1_dE and top1_dE < 3.5 else
                "Noticeable"    if top1_dE and top1_dE < 5 else
                "Poor"          if top1_dE else "N/A")

        rows.append({
            "filename":          fname,
            "ai_skin_tone":      skin_tone,
            "ai_undertone":      undertone,
            "ai_skin_type":      skin_type,
            "ai_concerns":       "|".join(sorted(predicted_concerns)) or "None",
            "gt_concerns":       "|".join(sorted(gt_set)) or "None",
            "confidence":        round(confidence, 3),
            "extracted_R":       r_val, "extracted_G": g_val, "extracted_B": b_val,
            "top1_shade":        top1_name,
            "top1_deltaE":       round(top1_dE, 3) if top1_dE else None,
            "deltaE_tier":       tier,
        })

        match_str = f"ΔE={top1_dE:.2f} [{tier}]" if top1_dE else "shade N/A"
        print(f"  {fname:20s} | {skin_tone:8s}/{undertone:7s} | "
              f"conf={confidence:.2f} | concerns={list(predicted_concerns) or ['None']} | {match_str}")

    n = len(rows)
    print(f"\n  Processed: {n}/{len(img_files)} images "
          f"({len(failed)} skipped — face not detected by MediaPipe)")

    if n == 0:
        print("  No images processed. Exiting.")
        return

    # ── Concern detection metrics ────────────────────────────────────────────
    print("\n" + "=" * 70)
    print("  CONCERN DETECTION (end-to-end, production pipeline)")
    print(f"  {'Concern':<22} {'P':>6} {'R':>6} {'F1':>6} | TP FP FN TN")
    print("  " + "-" * 60)

    class_results = {}
    macro_P = macro_R = macro_F1 = 0.0
    for concern in CONCERN_LABELS:
        c = confusion[concern]
        P, R, F1 = metrics(c["tp"], c["fp"], c["fn"], c["tn"])
        macro_P += P; macro_R += R; macro_F1 += F1
        class_results[concern] = {"precision":P,"recall":R,"f1":F1,**c}
        print(f"  {concern:<22} {P:>6.3f} {R:>6.3f} {F1:>6.3f} |"
              f" {c['tp']:>2} {c['fp']:>2} {c['fn']:>2} {c['tn']:>2}")

    n_cls = len(CONCERN_LABELS)
    macro_P /= n_cls; macro_R /= n_cls; macro_F1 /= n_cls
    print("  " + "-" * 60)
    print(f"  {'Macro Average':<22} {macro_P:>6.3f} {macro_R:>6.3f} {macro_F1:>6.3f}")

    # ── Shade match metrics ──────────────────────────────────────────────────
    if deltaEs:
        mean_dE  = float(np.mean(deltaEs))
        med_dE   = float(np.median(deltaEs))
        p95_dE   = float(np.percentile(deltaEs, 95))
        pct_acc  = 100 * sum(d < 3.5 for d in deltaEs) / len(deltaEs)
        pct_not  = 100 * sum(d < 5.0 for d in deltaEs) / len(deltaEs)
        print(f"\n  SHADE MATCH (AI-extracted colour via MediaPipe landmarks)")
        print(f"  Top-1 ΔE₀₀: mean={mean_dE:.2f}  median={med_dE:.2f}  P95={p95_dE:.2f}")
        print(f"  Acceptable (ΔE<3.5): {pct_acc:.1f}%  |  Noticeable (ΔE<5): {pct_not:.1f}%")

    # ── Figures ──────────────────────────────────────────────────────────────
    _plot_comparison(class_results, macro_F1)
    if deltaEs:
        _plot_deltaE_e2e(deltaEs, pct_acc)

    # ── Save outputs ─────────────────────────────────────────────────────────
    csv_path = os.path.join(OUT_DIR, "e2e_real_eval.csv")
    with open(csv_path, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader(); w.writerows(rows)

    summary = {
        "evaluation":      "e2e_production_pipeline_real_images",
        "date":            datetime.now().isoformat(),
        "n_processed":     n,
        "n_skipped":       len(failed),
        "skipped_files":   failed,
        "pipeline":        f"base64 → POST {AI_URL} (MediaPipe FaceLandmarker → CLAHE → LAB) → POST {SHADE_URL}",
        "ground_truth":    "Manual single-annotator labels from real_image_eval.csv",
        "concern_detection": {
            "class_results": class_results,
            "macro": {"precision": round(macro_P,4), "recall": round(macro_R,4), "f1": round(macro_F1,4)},
        },
        "shade_match": {
            "n": len(deltaEs),
            "mean_deltaE":   round(mean_dE,3)  if deltaEs else None,
            "median_deltaE": round(med_dE,3)   if deltaEs else None,
            "p95_deltaE":    round(p95_dE,3)   if deltaEs else None,
            "pct_acceptable_lt_3_5": round(pct_acc,1) if deltaEs else None,
            "pct_noticeable_lt_5":   round(pct_not,1) if deltaEs else None,
        } if deltaEs else {},
    }
    json_path = os.path.join(OUT_DIR, "e2e_real_eval_summary.json")
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2, ensure_ascii=False)

    print(f"\n  CSV:  {csv_path}")
    print(f"  JSON: {json_path}")
    print("  Done.")
    return summary


def _plot_comparison(class_results, macro_e2e):
    # Load previous offline results for side-by-side comparison
    offline_f1 = {"Acne/Blemishes":0.0,"Sensitive/Redness":0.500,
                  "Dark Circles":0.118,"Visible Pores":0.0,"Oily Skin":0.529}
    offline_macro = 0.229

    labels = CONCERN_LABELS
    e2e_f1     = [class_results[c]["f1"] for c in labels]
    off_f1     = [offline_f1[c] for c in labels]

    x = np.arange(len(labels)); w = 0.35
    fig, ax = plt.subplots(figsize=(12, 5))
    b1 = ax.bar(x-w/2, off_f1, w, label="Offline (geometric ROI)", color="#9DB8D2", alpha=0.85)
    b2 = ax.bar(x+w/2, e2e_f1, w, label="End-to-End (MediaPipe pipeline)", color="#D6775C", alpha=0.85)

    for bar in list(b1)+list(b2):
        h = bar.get_height()
        if h > 0:
            ax.text(bar.get_x()+bar.get_width()/2, h+0.01, f"{h:.3f}",
                    ha="center", va="bottom", fontsize=7.5)

    ax.axhline(offline_macro, color="#9DB8D2", linestyle="--", linewidth=1.2,
               label=f"Offline macro F1={offline_macro:.3f}")
    ax.axhline(macro_e2e, color="#D6775C", linestyle="--", linewidth=1.2,
               label=f"E2E macro F1={macro_e2e:.3f}")

    ax.set_xticks(x); ax.set_xticklabels(labels, rotation=15, ha="right", fontsize=9)
    ax.set_ylabel("F1 Score"); ax.set_ylim(0, 1.15)
    ax.set_title("Concern Detection F1 — Offline vs End-to-End Production Pipeline\n"
                 f"(Real images n=34, ground truth: manual single-annotator labels)")
    ax.legend(fontsize=8.5); ax.grid(True, alpha=0.3, axis="y")

    plt.tight_layout()
    out = os.path.join(FIG_DIR, "e2e_vs_offline_f1.png")
    plt.savefig(out, dpi=150, bbox_inches="tight"); plt.close()
    print(f"  Saved: {out}")


def _plot_deltaE_e2e(deltaEs, pct_acc):
    fig, axes = plt.subplots(1, 2, figsize=(12, 4))

    ax = axes[0]
    bins = np.arange(0, max(deltaEs)+1.5, 0.5)
    ax.hist(deltaEs, bins=bins, color="#D6775C", edgecolor="white", alpha=0.85)
    for thr, col, lbl in [(1.0,"#2ecc71","ΔE=1"),(2.0,"#f39c12","ΔE=2"),(3.5,"#e74c3c","ΔE=3.5 acceptable")]:
        ax.axvline(thr, color=col, linestyle="--", linewidth=1.5, label=lbl)
    ax.set_xlabel("ΔE₀₀ (MediaPipe-extracted colour)"); ax.set_ylabel("Count")
    ax.set_title("Shade Match ΔE₀₀ — End-to-End Pipeline")
    ax.legend(fontsize=8); ax.grid(True, alpha=0.3)

    # Compare offline vs e2e ΔE
    ax2 = axes[1]
    offline_dE = 6.19
    e2e_dE     = float(np.mean(deltaEs))
    bars = ax2.bar(["Offline\n(geometric ROI)", "End-to-End\n(MediaPipe)"],
                   [offline_dE, e2e_dE],
                   color=["#9DB8D2","#D6775C"], edgecolor="white", alpha=0.9, width=0.5)
    ax2.axhline(3.5, color="#e74c3c", linestyle="--", linewidth=1.5, label="ΔE=3.5 threshold")
    for bar, val in zip(bars, [offline_dE, e2e_dE]):
        ax2.text(bar.get_x()+bar.get_width()/2, val+0.1, f"ΔE={val:.2f}",
                 ha="center", va="bottom", fontsize=11, fontweight="bold")
    ax2.set_ylabel("Mean Top-1 ΔE₀₀"); ax2.set_ylim(0, max(offline_dE,e2e_dE)+2)
    ax2.set_title(f"Mean ΔE₀₀: Offline vs End-to-End\n(E2E acceptable: {pct_acc:.1f}%)")
    ax2.legend(fontsize=8); ax2.grid(True, alpha=0.3, axis="y")

    plt.tight_layout()
    out = os.path.join(FIG_DIR, "e2e_deltaE_comparison.png")
    plt.savefig(out, dpi=150, bbox_inches="tight"); plt.close()
    print(f"  Saved: {out}")


if __name__ == "__main__":
    run()
