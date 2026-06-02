"""
Real-Image Concern Detection Evaluation
=========================================
Companion to concern_detection_eval.py.

Usage — 2 steps:

  STEP 1: Collect face images (JPEG/PNG) into a folder, e.g. real_eval_images/
          You can use the AI service's /analyze endpoint to process them,
          or point this script at the folder directly.

  STEP 2: Run this script — it processes each image through the concern
          detection algorithm and opens an interactive labelling prompt so
          you can enter the ground-truth labels manually.

          PYTHONIOENCODING=utf-8 /c/Python313/python.exe real_image_eval.py \
              --images real_eval_images/ \
              --labels real_eval_labels.csv   # optional: resume from previous run

  Output:
    eval_results/real_image_eval.csv
    eval_results/real_image_eval_summary.json
    eval_results/figures/real_prf1_bar.png  (if >= 5 images per class)

Labelling guide (enter comma-separated numbers or leave blank for None):
  1 = Acne/Blemishes
  2 = Sensitive/Redness
  3 = Dark Circles
  4 = Visible Pores
  5 = Oily Skin
  e.g. "1,4" means Acne and Visible Pores visible in this photo

Note: This is a minimum-viable labelling tool for a pilot study.
For clinical-grade validation, use consensus labels from >= 2 annotators.
"""

import sys, os, cv2, json, csv, argparse
import numpy as np
from datetime import datetime

# ── Import concern detection from concern_detection_eval (same logic) ──────────
# Add current directory to path
sys.path.insert(0, os.path.dirname(__file__))
from concern_detection_eval import (
    extract_features, classify_from_features,
    CONCERN_LABELS, metrics, OUT_DIR, FIGURES_DIR,
    N_POS, N_NEG
)

LABEL_MAP = {
    "1": "Acne/Blemishes",
    "2": "Sensitive/Redness",
    "3": "Dark Circles",
    "4": "Visible Pores",
    "5": "Oily Skin",
}


def load_image(path: str):
    img = cv2.imread(path)
    if img is None:
        return None
    # Resize to 640x480 to match expected face proportions
    return cv2.resize(img, (640, 480))


def get_ground_truth_interactive(img_path: str) -> list[str]:
    print(f"\n  Image: {os.path.basename(img_path)}")
    print("  Which concerns are VISIBLY PRESENT in this face photo?")
    print("  1=Acne/Blemishes  2=Sensitive/Redness  3=Dark Circles")
    print("  4=Visible Pores   5=Oily Skin")
    raw = input("  Enter numbers (comma-separated) or leave blank for None: ").strip()
    if not raw:
        return []
    concerns = []
    for token in raw.split(","):
        token = token.strip()
        if token in LABEL_MAP:
            concerns.append(LABEL_MAP[token])
        elif token:
            print(f"  Ignored unknown label: {token}")
    return concerns


def load_existing_labels(csv_path: str) -> dict:
    """Load previously annotated labels so you can resume a session."""
    if not os.path.exists(csv_path):
        return {}
    with open(csv_path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        return {row["filename"]: row for row in reader}


def run_real_eval(image_dir: str, labels_csv: str = None):
    os.makedirs(OUT_DIR, exist_ok=True)
    os.makedirs(FIGURES_DIR, exist_ok=True)

    if not os.path.isdir(image_dir):
        print(f"ERROR: {image_dir} is not a directory.")
        sys.exit(1)

    exts      = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}
    img_paths = sorted(
        p for p in (os.path.join(image_dir, f) for f in os.listdir(image_dir))
        if os.path.splitext(p)[1].lower() in exts
    )

    if not img_paths:
        print(f"No images found in {image_dir}.")
        sys.exit(1)

    print(f"\nFound {len(img_paths)} images in {image_dir}.")
    print("==========================================================")
    print("  LABELLING GUIDE")
    print("  For each image, review it and enter which concerns you")
    print("  can see. Press Ctrl+C to stop and save partial results.")
    print("==========================================================")

    existing = load_existing_labels(labels_csv) if labels_csv else {}
    rows     = []

    try:
        for img_path in img_paths:
            fname = os.path.basename(img_path)
            img   = load_image(img_path)
            if img is None:
                print(f"  SKIP (cannot read): {fname}")
                continue

            feats = extract_features(img)
            preds = classify_from_features(feats)

            # Check if already labelled
            if fname in existing:
                gt_str = existing[fname].get("ground_truth_labels", "")
                gt = [x.strip() for x in gt_str.split("|") if x.strip()] if gt_str else []
                print(f"  {fname}: resuming from saved label ({gt or 'None'})")
            else:
                gt = get_ground_truth_interactive(img_path)

            rows.append({
                "filename":           fname,
                "ground_truth_labels": "|".join(gt) if gt else "None",
                "predicted_labels":    "|".join(preds) if preds else "None",
                "a_std":     round(feats["a_std"], 3),
                "a_mean":    round(feats["a_mean"], 3),
                "dark_delta": round(feats["dark_delta"], 3),
                "lap_var":   round(feats["lap_var"], 1),
                "oily_ratio": round(feats["oily_ratio"], 4),
            })

    except KeyboardInterrupt:
        print("\n\nStopped early — saving partial results...")

    if not rows:
        print("No images processed.")
        return

    # ── Save raw annotations CSV ───────────────────────────────────────────────
    csv_out = os.path.join(OUT_DIR, "real_image_eval.csv")
    with open(csv_out, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader(); writer.writerows(rows)
    print(f"\nAnnotations saved: {csv_out}")

    # ── Compute per-concern P/R/F1 ─────────────────────────────────────────────
    confusion = {c: {"tp":0,"fp":0,"fn":0,"tn":0} for c in CONCERN_LABELS}
    for row in rows:
        gt_set   = set(row["ground_truth_labels"].split("|")) - {"None"}
        pred_set = set(row["predicted_labels"].split("|"))    - {"None"}
        for concern in CONCERN_LABELS:
            gt_pos   = concern in gt_set
            pred_pos = concern in pred_set
            if   gt_pos and pred_pos:      confusion[concern]["tp"] += 1
            elif not gt_pos and pred_pos:  confusion[concern]["fp"] += 1
            elif gt_pos and not pred_pos:  confusion[concern]["fn"] += 1
            else:                          confusion[concern]["tn"] += 1

    class_results = {}
    macro_P = macro_R = macro_F1 = 0.0
    print(f"\n{'Concern':<22} {'P':>6} {'R':>6} {'F1':>6} | TP FP FN TN")
    print("-" * 60)
    for concern in CONCERN_LABELS:
        c = confusion[concern]
        P, R, F1, Acc = metrics(c["tp"], c["fp"], c["fn"], c["tn"])
        macro_P += P; macro_R += R; macro_F1 += F1
        class_results[concern] = {
            "precision": round(P,4), "recall": round(R,4),
            "f1": round(F1,4), "accuracy": round(Acc,4), **c
        }
        print(f"{concern:<22} {P:>6.3f} {R:>6.3f} {F1:>6.3f} |"
              f" {c['tp']:>2} {c['fp']:>2} {c['fn']:>2} {c['tn']:>2}")
    n_cls = len(CONCERN_LABELS)
    macro_P /= n_cls; macro_R /= n_cls; macro_F1 /= n_cls
    print("-" * 60)
    print(f"{'Macro Average':<22} {macro_P:>6.3f} {macro_R:>6.3f} {macro_F1:>6.3f}")

    # ── Save JSON ──────────────────────────────────────────────────────────────
    summary = {
        "evaluation":  "concern_detection_real_images",
        "date":         datetime.now().isoformat(),
        "n_images":     len(rows),
        "image_dir":    image_dir,
        "class_results": class_results,
        "macro_average": {
            "precision": round(macro_P,4),
            "recall":    round(macro_R,4),
            "f1":        round(macro_F1,4),
        },
        "note": (
            "Manual single-annotator labels. For academic reporting, disclose "
            "annotator expertise (self/peer, not dermatologist) and consider "
            "inter-annotator agreement (Cohen's kappa) if multiple raters used."
        ),
    }
    json_out = os.path.join(OUT_DIR, "real_image_eval_summary.json")
    with open(json_out, "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2, ensure_ascii=False)
    print(f"\nSummary JSON: {json_out}")

    # ── Bar chart (only if enough data) ───────────────────────────────────────
    min_support = min(
        c["tp"] + c["fn"] for c in confusion.values()
    )
    if min_support >= 3:
        try:
            import matplotlib
            matplotlib.use("Agg")
            import matplotlib.pyplot as plt
            x  = np.arange(len(CONCERN_LABELS))
            w  = 0.25
            fig, ax = plt.subplots(figsize=(11, 5))
            ax.bar(x-w, [class_results[c]["precision"] for c in CONCERN_LABELS], w, label="P", color="#5C85D6", alpha=0.85)
            ax.bar(x,   [class_results[c]["recall"]    for c in CONCERN_LABELS], w, label="R", color="#5CAD8E", alpha=0.85)
            ax.bar(x+w, [class_results[c]["f1"]        for c in CONCERN_LABELS], w, label="F1",color="#D6775C", alpha=0.85)
            ax.set_xticks(x); ax.set_xticklabels(CONCERN_LABELS, rotation=15, ha="right")
            ax.set_ylim(0, 1.15); ax.set_ylabel("Score")
            ax.set_title(f"Real-Image Evaluation — P/R/F1 per Concern\n(n={len(rows)} images, manual single-annotator labels)")
            ax.legend(); plt.tight_layout()
            fig_path = os.path.join(FIGURES_DIR, "real_prf1_bar.png")
            plt.savefig(fig_path, dpi=150, bbox_inches="tight"); plt.close()
            print(f"Bar chart: {fig_path}")
        except Exception as e:
            print(f"(Figure skipped: {e})")
    else:
        print(f"(Bar chart skipped: min positive support = {min_support} < 3)")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Real-image concern detection eval")
    parser.add_argument("--images", default="real_eval_images",
                        help="Directory containing face JPEG/PNG images")
    parser.add_argument("--labels", default=None,
                        help="Existing labels CSV to resume from")
    args = parser.parse_args()
    run_real_eval(args.images, args.labels)
