"""
Concern Detection Evaluation — Precision / Recall / F1 + Threshold Sensitivity
================================================================================
Methodology: Controlled Synthetic Stimuli with Known Ground Truth

Each concern is tested with N_POS positive and N_NEG negative images.
Images are synthetically constructed so that the exact LAB / Laplacian / HSV
properties that trigger each threshold are known precisely.

This is a standard "controlled stimuli" evaluation used in computer vision
algorithm validation when labelled real-world data is unavailable.

Outputs:
  eval_results/concern_detection_eval.csv              — per-case detail
  eval_results/concern_detection_eval_summary.json     — P/R/F1 + threshold sweep
  eval_results/figures/confusion_matrix.png
  eval_results/figures/prf1_bar.png
  eval_results/figures/threshold_sensitivity.png

Run:
  PYTHONIOENCODING=utf-8 /c/Python313/python.exe concern_detection_eval.py
"""

import sys, os, cv2, json, csv
import numpy as np
import matplotlib
matplotlib.use("Agg")           # headless — no display needed
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from datetime import datetime

# ── Configuration ──────────────────────────────────────────────────────────────
W, H   = 640, 480
N_POS  = 50     # positive samples per concern class
N_NEG  = 50     # negative samples per concern class
OUT_DIR     = os.path.join(os.path.dirname(__file__), "eval_results")
FIGURES_DIR = os.path.join(OUT_DIR, "figures")

# Thresholds — identical to skin_analyzer.py _detect_concerns()
THR_ACNE_STD   = 10
THR_RED_MEAN   = 145
THR_DARK_DELTA = 15
THR_PORES_LAP  = 700
THR_OILY_V     = 200
THR_OILY_RATIO = 0.10

CONCERN_LABELS = [
    "Acne/Blemishes",
    "Sensitive/Redness",
    "Dark Circles",
    "Visible Pores",
    "Oily Skin",
]

# ── Mock landmarks ─────────────────────────────────────────────────────────────
POSITIONS = {
    116: (0.25, 0.48), 117: (0.27, 0.54), 118: (0.25, 0.60),
    100: (0.27, 0.65), 123: (0.30, 0.67), 147: (0.34, 0.67),
    213: (0.36, 0.60), 192: (0.32, 0.50),
    345: (0.75, 0.48), 346: (0.73, 0.54), 347: (0.75, 0.60),
    329: (0.73, 0.65), 352: (0.70, 0.67), 376: (0.66, 0.67),
    433: (0.64, 0.60), 416: (0.68, 0.50),
    226: (0.30, 0.38),  31: (0.32, 0.40), 228: (0.34, 0.39),
    229: (0.36, 0.38), 230: (0.38, 0.37), 231: (0.40, 0.38),
    232: (0.42, 0.39), 233: (0.44, 0.40), 244: (0.46, 0.38),
    446: (0.70, 0.38), 261: (0.68, 0.40), 448: (0.66, 0.39),
    449: (0.64, 0.38), 450: (0.62, 0.37), 451: (0.60, 0.38),
    452: (0.58, 0.39), 453: (0.56, 0.40), 464: (0.54, 0.38),
    10:  (0.50, 0.20), 109: (0.40, 0.25), 338: (0.60, 0.25), 9: (0.50, 0.22),
}
FOREHEAD_ROI_INDICES = [10, 109, 338, 9]


class _Lm:
    __slots__ = ("x", "y")
    def __init__(self, x, y): self.x, self.y = x, y


def make_landmarks(n=478):
    lms = [_Lm(0.5, 0.5) for _ in range(n)]
    for idx, (x, y) in POSITIONS.items():
        lms[idx] = _Lm(x, y)
    return lms


LANDMARKS = make_landmarks()


def px(idx):
    lm = LANDMARKS[idx]
    return int(lm.x * W), int(lm.y * H)


# ── Precomputed region masks ───────────────────────────────────────────────────

def _cheek_mask():
    lc = np.array([px(i) for i in [116,117,118,100,123,147,213,192]], np.int32)
    rc = np.array([px(i) for i in [345,346,347,329,352,376,433,416]], np.int32)
    m  = np.zeros((H, W), np.uint8)
    cv2.fillPoly(m, [lc, rc], 255)
    return m

def _eye_mask():
    el = np.array([px(i) for i in [226,31,228,229,230,231,232,233,244]], np.int32)
    er = np.array([px(i) for i in [446,261,448,449,450,451,452,453,464]], np.int32)
    m  = np.zeros((H, W), np.uint8)
    cv2.fillPoly(m, [el, er], 255)
    return m

def _forehead_center():
    coords = [px(i) for i in FOREHEAD_ROI_INDICES]
    cx = int(sum(c[0] for c in coords) / len(coords))
    cy = int(sum(c[1] for c in coords) / len(coords))
    return cx, cy


CHEEK_MASK     = _cheek_mask()
EYE_MASK       = _eye_mask()
FH_CX, FH_CY   = _forehead_center()
FH_Y1, FH_Y2   = max(0, FH_CY-30), min(H, FH_CY+30)
FH_X1, FH_X2   = max(0, FH_CX-30), min(W, FH_CX+30)


# ── Feature extraction (raw values, threshold-agnostic) ───────────────────────

def extract_features(image: np.ndarray) -> dict:
    """Extract raw discriminant features — same computation as skin_analyzer.py."""
    cheeks_bgr  = cv2.bitwise_and(image, image, mask=CHEEK_MASK)
    cheeks_lab  = cv2.cvtColor(cheeks_bgr, cv2.COLOR_BGR2Lab)
    l_c, a_c, _ = cv2.split(cheeks_lab)

    valid_a      = a_c[CHEEK_MASK > 0]
    a_std        = float(np.std(valid_a))  if len(valid_a) > 0 else 0.0
    a_mean       = float(np.mean(valid_a)) if len(valid_a) > 0 else 0.0

    lab_full     = cv2.cvtColor(image, cv2.COLOR_BGR2Lab)
    mean_eye_L   = float(cv2.mean(lab_full[:, :, 0], mask=EYE_MASK)[0])
    mean_cheek_L = float(cv2.mean(l_c, mask=CHEEK_MASK)[0])
    dark_delta   = mean_cheek_L - mean_eye_L

    cheeks_gray  = cv2.cvtColor(cheeks_bgr, cv2.COLOR_BGR2GRAY)
    lap_var      = float(cv2.Laplacian(cheeks_gray, cv2.CV_64F).var())

    fh_patch     = image[FH_Y1:FH_Y2, FH_X1:FH_X2]
    v_fh         = cv2.split(cv2.cvtColor(fh_patch, cv2.COLOR_BGR2HSV))[2]
    oily_ratio   = float(np.count_nonzero(v_fh > THR_OILY_V) / v_fh.size)

    return {
        "a_std":       a_std,
        "a_mean":      a_mean,
        "dark_delta":  dark_delta,
        "lap_var":     lap_var,
        "oily_ratio":  oily_ratio,
    }


def classify_from_features(feats: dict,
                             acne_std_thr=THR_ACNE_STD,
                             red_mean_thr=THR_RED_MEAN,
                             dark_thr=THR_DARK_DELTA,
                             pores_thr=THR_PORES_LAP,
                             oily_thr=THR_OILY_RATIO) -> list:
    concerns = []
    if feats["a_std"] > acne_std_thr:
        concerns.append("Acne/Blemishes")
    elif feats["a_mean"] > red_mean_thr:
        concerns.append("Sensitive/Redness")
    if feats["dark_delta"] > dark_thr:
        concerns.append("Dark Circles")
    if feats["lap_var"] > pores_thr:
        concerns.append("Visible Pores")
    if feats["oily_ratio"] > oily_thr:
        concerns.append("Oily Skin")
    return concerns


# ── Image synthesis ────────────────────────────────────────────────────────────
BASE_BGR = np.array([165, 150, 180], dtype=np.uint8)   # neutral medium skin tone


def _base():
    return np.full((H, W, 3), BASE_BGR, dtype=np.uint8)


def _set_cheek_lab(img, l_val=None, a_val=None, b_val=None, a_noise_std=0.0, seed=None):
    if seed is not None:
        np.random.seed(seed)
    lab = cv2.cvtColor(img, cv2.COLOR_BGR2Lab).astype(np.float32)
    n   = int(np.count_nonzero(CHEEK_MASK))
    if l_val is not None:
        lab[CHEEK_MASK > 0, 0] = float(l_val)
    if a_val is not None:
        base = np.full(n, float(a_val))
        if a_noise_std > 0:
            base += np.random.normal(0, a_noise_std, n)
        lab[CHEEK_MASK > 0, 1] = np.clip(base, 0, 255)
    if b_val is not None:
        lab[CHEEK_MASK > 0, 2] = float(b_val)
    return cv2.cvtColor(np.clip(lab, 0, 255).astype(np.uint8), cv2.COLOR_Lab2BGR)


def _set_eye_lab(img, l_val):
    lab = cv2.cvtColor(img, cv2.COLOR_BGR2Lab).astype(np.float32)
    lab[EYE_MASK > 0, 0] = float(l_val)
    return cv2.cvtColor(np.clip(lab, 0, 255).astype(np.uint8), cv2.COLOR_Lab2BGR)


def _add_lum_noise(img, noise_std, seed):
    """Same scalar noise to all BGR channels → chroma preserved, luminance textured."""
    np.random.seed(seed)
    noise = np.random.normal(0, noise_std, (H, W)).astype(np.int16)
    out   = img.astype(np.int16)
    for c in range(3):
        ch = out[:, :, c]
        ch[CHEEK_MASK > 0] += noise[CHEEK_MASK > 0]
        out[:, :, c] = ch
    return np.clip(out, 0, 255).astype(np.uint8)


def _paint_forehead(img, bright_fraction):
    out = img.copy()
    ph, pw = FH_Y2-FH_Y1, FH_X2-FH_X1
    n_bright = max(1, int(bright_fraction * ph * pw))
    patch = out[FH_Y1:FH_Y2, FH_X1:FH_X2].reshape(-1, 3).copy()
    patch[:n_bright] = [220, 220, 250]   # V ~ 250 >> 200
    out[FH_Y1:FH_Y2, FH_X1:FH_X2] = patch.reshape(ph, pw, 3)
    return out


def _paint_forehead_dim(img, dim_fraction):
    """Sub-threshold forehead: V ~ 192 < 200."""
    out = img.copy()
    ph, pw = FH_Y2-FH_Y1, FH_X2-FH_X1
    n_px = max(0, int(dim_fraction * ph * pw))
    patch = out[FH_Y1:FH_Y2, FH_X1:FH_X2].reshape(-1, 3).copy()
    if n_px > 0:
        patch[:n_px] = [185, 178, 192]   # V ~ 192 < 200
    out[FH_Y1:FH_Y2, FH_X1:FH_X2] = patch.reshape(ph, pw, 3)
    return out


# ── Test-case generators (50 pos + 50 neg each) ────────────────────────────────

def gen_acne(n_pos=N_POS, n_neg=N_NEG):
    pos_stds = np.linspace(12, 60, n_pos)    # a* noise std >> 10
    neg_stds = np.linspace(0,  8,  n_neg)    # a* noise std << 10
    cases = []
    for i, s in enumerate(pos_stds):
        img = _set_cheek_lab(_base(), a_val=130, b_val=130, a_noise_std=s, seed=i)
        cases.append(("Acne/Blemishes", img, 1, f"acne_pos_s{s:.1f}"))
    for i, s in enumerate(neg_stds):
        img = _set_cheek_lab(_base(), a_val=130, b_val=130, a_noise_std=s, seed=100+i)
        cases.append(("Acne/Blemishes", img, 0, f"acne_neg_s{s:.1f}"))
    return cases


def gen_redness(n_pos=N_POS, n_neg=N_NEG):
    pos_means = np.linspace(147, 175, n_pos)
    neg_means = np.linspace(110, 143, n_neg)
    cases = []
    for i, m in enumerate(pos_means):
        img = _set_cheek_lab(_base(), a_val=m, b_val=130)
        cases.append(("Sensitive/Redness", img, 1, f"red_pos_a{m:.1f}"))
    for i, m in enumerate(neg_means):
        img = _set_cheek_lab(_base(), a_val=m, b_val=130)
        cases.append(("Sensitive/Redness", img, 0, f"red_neg_a{m:.1f}"))
    return cases


def gen_dark_circles(n_pos=N_POS, n_neg=N_NEG):
    pos_deltas = np.linspace(17, 60, n_pos)
    neg_deltas = np.linspace(0,  13, n_neg)
    cases = []
    for i, d in enumerate(pos_deltas):
        img = _set_cheek_lab(_base(), l_val=140, a_val=128, b_val=130)
        img = _set_eye_lab(img, l_val=140 - d)
        cases.append(("Dark Circles", img, 1, f"dark_pos_d{d:.1f}"))
    for i, d in enumerate(neg_deltas):
        img = _set_cheek_lab(_base(), l_val=140, a_val=128, b_val=130)
        img = _set_eye_lab(img, l_val=140 - d)
        cases.append(("Dark Circles", img, 0, f"dark_neg_d{d:.1f}"))
    return cases


def gen_pores(n_pos=N_POS, n_neg=N_NEG):
    pos_stds = np.linspace(32, 85, n_pos)
    cases = []
    for i, s in enumerate(pos_stds):
        img = _set_cheek_lab(_base(), a_val=128, b_val=130)
        img = _add_lum_noise(img, noise_std=s, seed=200+i)
        cases.append(("Visible Pores", img, 1, f"pores_pos_s{s:.1f}"))
    for i in range(n_neg):
        img = _set_cheek_lab(_base(), l_val=130, a_val=128, b_val=130)
        cases.append(("Visible Pores", img, 0, f"pores_neg_{i}"))
    return cases


def gen_oily(n_pos=N_POS, n_neg=N_NEG):
    pos_fracs = np.linspace(0.15, 0.95, n_pos)
    neg_fracs = np.linspace(0.00, 0.08, n_neg)
    cases = []
    for i, f in enumerate(pos_fracs):
        img = _paint_forehead(_base(), bright_fraction=f)
        cases.append(("Oily Skin", img, 1, f"oily_pos_f{f:.2f}"))
    for i, f in enumerate(neg_fracs):
        img = _paint_forehead_dim(_base(), dim_fraction=f)
        cases.append(("Oily Skin", img, 0, f"oily_neg_f{f:.2f}"))
    return cases


# ── Metrics ────────────────────────────────────────────────────────────────────

def metrics(tp, fp, fn, tn):
    P   = tp / (tp+fp)   if (tp+fp) > 0 else 0.0
    R   = tp / (tp+fn)   if (tp+fn) > 0 else 0.0
    F1  = 2*P*R/(P+R)    if (P+R)   > 0 else 0.0
    Acc = (tp+tn)/(tp+fp+fn+tn) if (tp+fp+fn+tn) > 0 else 0.0
    return P, R, F1, Acc


# ── Threshold sensitivity sweep ────────────────────────────────────────────────

SWEEP_CONFIGS = {
    "Acne/Blemishes": {
        "feature": "a_std",
        "thresholds": np.arange(3, 25, 0.5),
        "nominal": THR_ACNE_STD,
        "xlabel": "a* std threshold",
    },
    "Sensitive/Redness": {
        "feature": "a_mean",
        "thresholds": np.arange(125, 165, 1.0),
        "nominal": THR_RED_MEAN,
        "xlabel": "a* mean threshold",
    },
    "Dark Circles": {
        "feature": "dark_delta",
        "thresholds": np.arange(5, 35, 0.5),
        "nominal": THR_DARK_DELTA,
        "xlabel": "cheek_L - eye_L threshold",
    },
    "Visible Pores": {
        "feature": "lap_var",
        "thresholds": np.arange(200, 1400, 20),
        "nominal": THR_PORES_LAP,
        "xlabel": "Laplacian variance threshold",
    },
    "Oily Skin": {
        "feature": "oily_ratio",
        "thresholds": np.arange(0.02, 0.30, 0.005),
        "nominal": THR_OILY_RATIO,
        "xlabel": "V>200 pixel ratio threshold",
    },
}


def threshold_sweep(cases_feats_gt):
    """
    For each concern, sweep the threshold and compute P/R/F1.
    Returns dict: concern -> list of {thr, P, R, F1}
    """
    results = {}
    for concern, cfg in SWEEP_CONFIGS.items():
        feat_key = cfg["feature"]
        rows = [(feats[feat_key], gt)
                for (tc, feats, gt, _) in cases_feats_gt if tc == concern]
        sweep_rows = []
        for thr in cfg["thresholds"]:
            tp = fp = fn = tn = 0
            for val, gt in rows:
                pred = int(val > thr)
                if   gt == 1 and pred == 1: tp += 1
                elif gt == 0 and pred == 1: fp += 1
                elif gt == 1 and pred == 0: fn += 1
                else:                       tn += 1
            P, R, F1, _ = metrics(tp, fp, fn, tn)
            sweep_rows.append({"thr": round(float(thr), 4),
                                "P": round(P, 4), "R": round(R, 4),
                                "F1": round(F1, 4)})
        results[concern] = sweep_rows
    return results


# ── Visualization ──────────────────────────────────────────────────────────────

COLORS = {
    "Acne/Blemishes":    "#E57373",
    "Sensitive/Redness": "#FF8A65",
    "Dark Circles":      "#7986CB",
    "Visible Pores":     "#4DB6AC",
    "Oily Skin":         "#FFD54F",
}


def plot_confusion_matrix(class_results, save_path):
    fig, axes = plt.subplots(1, 5, figsize=(16, 3.5))
    fig.suptitle("Confusion Matrix per Concern Class\n(Controlled Synthetic Evaluation, n=100/class)",
                 fontsize=10, fontweight="bold", y=1.02)
    for ax, concern in zip(axes, CONCERN_LABELS):
        r  = class_results[concern]
        cm = np.array([[r["tn"], r["fp"]],
                       [r["fn"], r["tp"]]])
        im = ax.imshow(cm, cmap="Blues", vmin=0, vmax=N_POS)
        for i in range(2):
            for j in range(2):
                ax.text(j, i, str(cm[i, j]),
                        ha="center", va="center",
                        fontsize=14, fontweight="bold",
                        color="white" if cm[i, j] > N_POS * 0.5 else "black")
        ax.set_xticks([0, 1]); ax.set_yticks([0, 1])
        ax.set_xticklabels(["Pred-", "Pred+"], fontsize=8)
        ax.set_yticklabels(["True-", "True+"], fontsize=8)
        short = concern.replace("/", "/\n")
        ax.set_title(f"{short}\nF1={r['f1']:.3f}", fontsize=8, color=COLORS[concern],
                     fontweight="bold")
    plt.tight_layout()
    plt.savefig(save_path, dpi=150, bbox_inches="tight")
    plt.close()
    print(f"  Saved: {save_path}")


def plot_prf1_bar(class_results, save_path):
    x      = np.arange(len(CONCERN_LABELS))
    width  = 0.25
    P_vals  = [class_results[c]["precision"] for c in CONCERN_LABELS]
    R_vals  = [class_results[c]["recall"]    for c in CONCERN_LABELS]
    F1_vals = [class_results[c]["f1"]        for c in CONCERN_LABELS]

    fig, ax = plt.subplots(figsize=(11, 5))
    bars_P  = ax.bar(x - width, P_vals,  width, label="Precision", color="#5C85D6", alpha=0.85)
    bars_R  = ax.bar(x,          R_vals,  width, label="Recall",    color="#5CAD8E", alpha=0.85)
    bars_F1 = ax.bar(x + width, F1_vals, width, label="F1",         color="#D6775C", alpha=0.85)

    for bars in [bars_P, bars_R, bars_F1]:
        for bar in bars:
            h = bar.get_height()
            ax.text(bar.get_x() + bar.get_width()/2, h + 0.005,
                    f"{h:.3f}", ha="center", va="bottom", fontsize=7)

    ax.set_ylim(0, 1.12)
    ax.set_xticks(x)
    ax.set_xticklabels(CONCERN_LABELS, rotation=15, ha="right", fontsize=9)
    ax.set_ylabel("Score")
    ax.set_title("Precision / Recall / F1 per Concern Class\n"
                 f"(n={N_POS+N_NEG} per class; {N_POS} pos + {N_NEG} neg — controlled synthetic stimuli)",
                 fontsize=10)
    ax.legend(fontsize=9)
    ax.axhline(0.9, color="gray", linestyle="--", linewidth=0.8, alpha=0.6, label="_")
    ax.text(len(CONCERN_LABELS)-0.4, 0.91, "F1=0.90", fontsize=7, color="gray")
    ax.set_ylim(0.0, 1.12)
    plt.tight_layout()
    plt.savefig(save_path, dpi=150, bbox_inches="tight")
    plt.close()
    print(f"  Saved: {save_path}")


def plot_threshold_sensitivity(sweep_results, save_path):
    fig, axes = plt.subplots(2, 3, figsize=(15, 8))
    axes = axes.flatten()

    for idx, concern in enumerate(CONCERN_LABELS):
        ax   = axes[idx]
        cfg  = SWEEP_CONFIGS[concern]
        rows = sweep_results[concern]
        thrs = [r["thr"] for r in rows]
        Ps   = [r["P"]   for r in rows]
        Rs   = [r["R"]   for r in rows]
        F1s  = [r["F1"]  for r in rows]

        ax.plot(thrs, Ps,  color="#5C85D6", linewidth=1.8, label="Precision")
        ax.plot(thrs, Rs,  color="#5CAD8E", linewidth=1.8, label="Recall")
        ax.plot(thrs, F1s, color="#D6775C", linewidth=2.2, label="F1", zorder=5)
        ax.axvline(cfg["nominal"], color="black", linestyle="--",
                   linewidth=1.2, label=f"Nominal={cfg['nominal']}")

        # Find best F1 threshold
        best_idx = int(np.argmax(F1s))
        best_thr = thrs[best_idx]
        best_f1  = F1s[best_idx]
        ax.scatter([best_thr], [best_f1], color="#D6775C", zorder=10, s=50)
        ax.annotate(f"best F1={best_f1:.3f}\n@thr={best_thr:.2f}",
                    xy=(best_thr, best_f1),
                    xytext=(0, -32), textcoords="offset points",
                    ha="center", fontsize=6.5,
                    arrowprops=dict(arrowstyle="-", color="gray", lw=0.8))

        ax.set_title(f"{concern}", fontsize=9, fontweight="bold",
                     color=COLORS[concern])
        ax.set_xlabel(cfg["xlabel"], fontsize=7)
        ax.set_ylabel("Score", fontsize=7)
        ax.set_ylim(-0.05, 1.10)
        ax.legend(fontsize=6.5, loc="lower left")
        ax.grid(True, alpha=0.3, linewidth=0.5)
        ax.tick_params(labelsize=7)

    axes[-1].set_visible(False)    # hide empty 6th cell
    fig.suptitle("Threshold Sensitivity Analysis — Concern Detection\n"
                 "(nominal thresholds from skin_analyzer.py shown as dashed line)",
                 fontsize=11, fontweight="bold")
    plt.tight_layout()
    plt.savefig(save_path, dpi=150, bbox_inches="tight")
    plt.close()
    print(f"  Saved: {save_path}")


# ── Main evaluation ────────────────────────────────────────────────────────────

def run_evaluation():
    os.makedirs(OUT_DIR, exist_ok=True)
    os.makedirs(FIGURES_DIR, exist_ok=True)

    # Generate all test cases
    all_cases = (gen_acne() + gen_redness() + gen_dark_circles()
                 + gen_pores() + gen_oily())

    print("=" * 68)
    print("  Concern Detection Evaluation - Controlled Synthetic Stimuli")
    print(f"  n = {len(all_cases)} ({N_POS} pos + {N_NEG} neg per concern class)")
    print("  Extracting features...")
    print("=" * 68)

    # Extract features once; reuse for both classification and threshold sweep
    cases_feats_gt = []
    detail_rows    = []
    confusion      = {c: {"tp":0,"fp":0,"fn":0,"tn":0} for c in CONCERN_LABELS}

    for target_concern, image, ground_truth, case_id in all_cases:
        feats  = extract_features(image)
        preds  = classify_from_features(feats)
        pred_p = target_concern in preds

        if   ground_truth == 1 and pred_p:     outcome = "TP"; confusion[target_concern]["tp"] += 1
        elif ground_truth == 0 and pred_p:     outcome = "FP"; confusion[target_concern]["fp"] += 1
        elif ground_truth == 1 and not pred_p: outcome = "FN"; confusion[target_concern]["fn"] += 1
        else:                                  outcome = "TN"; confusion[target_concern]["tn"] += 1

        cases_feats_gt.append((target_concern, feats, ground_truth, case_id))
        detail_rows.append({
            "case_id":        case_id,
            "target_concern": target_concern,
            "ground_truth":   ground_truth,
            "predicted":      int(pred_p),
            "outcome":        outcome,
            "all_predicted":  "|".join(preds) if preds else "None",
            "a_std":          round(feats["a_std"], 3),
            "a_mean":         round(feats["a_mean"], 3),
            "dark_delta":     round(feats["dark_delta"], 3),
            "lap_var":        round(feats["lap_var"], 1),
            "oily_ratio":     round(feats["oily_ratio"], 4),
        })

    # ── Per-class metrics table ────────────────────────────────────────────────
    class_results = {}
    macro_P = macro_R = macro_F1 = 0.0

    print(f"\n{'Concern':<22} {'P':>6} {'R':>6} {'F1':>6} {'Acc':>6} | TP FP FN TN")
    print("-" * 72)

    for concern in CONCERN_LABELS:
        c = confusion[concern]
        P, R, F1, Acc = metrics(c["tp"], c["fp"], c["fn"], c["tn"])
        macro_P += P; macro_R += R; macro_F1 += F1
        class_results[concern] = {
            "precision": round(P, 4), "recall": round(R, 4),
            "f1":        round(F1, 4), "accuracy": round(Acc, 4),
            **c,
        }
        tag = "OK" if F1 >= 0.90 else ("~" if F1 >= 0.75 else "FAIL")
        print(f"{concern:<22} {P:>6.3f} {R:>6.3f} {F1:>6.3f} {Acc:>6.3f} |"
              f" {c['tp']:>2} {c['fp']:>2} {c['fn']:>2} {c['tn']:>2}  {tag}")

    n_cls = len(CONCERN_LABELS)
    macro_P /= n_cls; macro_R /= n_cls; macro_F1 /= n_cls
    print("-" * 72)
    print(f"{'Macro Average':<22} {macro_P:>6.3f} {macro_R:>6.3f} {macro_F1:>6.3f}")
    print("=" * 68)

    # ── Mis-classified cases ───────────────────────────────────────────────────
    errors = [r for r in detail_rows if r["outcome"] in ("FP", "FN")]
    if errors:
        print(f"\nMis-classified ({len(errors)}):")
        for r in errors:
            print(f"  [{r['outcome']}] {r['case_id']} "
                  f"a_std={r['a_std']} a_mean={r['a_mean']} "
                  f"lap={r['lap_var']} delta={r['dark_delta']} oily={r['oily_ratio']}")
    else:
        print(f"\nAll {len(all_cases)} cases correctly classified.")

    # ── Threshold sensitivity sweep ────────────────────────────────────────────
    print("\nRunning threshold sensitivity sweep...")
    sweep_results = threshold_sweep(cases_feats_gt)

    # Best-F1 threshold per concern
    best_thrs = {}
    for concern, rows in sweep_results.items():
        best = max(rows, key=lambda r: r["F1"])
        best_thrs[concern] = best
    print("\nBest-F1 threshold vs nominal:")
    print(f"  {'Concern':<22} {'Nominal':>9} {'Best-thr':>9} {'Best-F1':>9}")
    print(f"  {'-'*55}")
    for concern in CONCERN_LABELS:
        nom = SWEEP_CONFIGS[concern]["nominal"]
        bt  = best_thrs[concern]
        print(f"  {concern:<22} {nom:>9.3f} {bt['thr']:>9.3f} {bt['F1']:>9.3f}")

    # ── Save CSV ───────────────────────────────────────────────────────────────
    csv_path = os.path.join(OUT_DIR, "concern_detection_eval.csv")
    with open(csv_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(detail_rows[0].keys()))
        writer.writeheader(); writer.writerows(detail_rows)

    # ── Save JSON ──────────────────────────────────────────────────────────────
    summary = {
        "evaluation": "concern_detection_controlled_stimuli",
        "date":        datetime.now().isoformat(),
        "methodology": (
            "Controlled synthetic stimuli with algorithmically specified ground truth "
            "(n=100 per class: 50 positive + 50 negative). Each image is constructed "
            "so that exactly one discriminant feature (LAB a* std/mean, "
            "cheek-eye L delta, Laplacian variance, or forehead V ratio) is set "
            "above or below the detection threshold. Concern detection logic inlined "
            "verbatim from skin_analyzer.py. Feature values extracted once; "
            "threshold sweep re-classifies without re-running image processing."
        ),
        "limitations": (
            "Synthetic stimuli do not capture real-world noise sources "
            "(camera sensor noise, lighting variation, ethnic skin diversity, "
            "motion blur). Results are therefore an upper bound on in-the-wild "
            "performance. Real-image validation with dermatologist-labelled photos "
            "is needed for clinical-grade claims."
        ),
        "n_total":    len(all_cases),
        "n_per_class": {"positive": N_POS, "negative": N_NEG},
        "thresholds": {
            "Acne/Blemishes":    {"feature": "a_std",      "value": THR_ACNE_STD},
            "Sensitive/Redness": {"feature": "a_mean",     "value": THR_RED_MEAN},
            "Dark Circles":      {"feature": "dark_delta",  "value": THR_DARK_DELTA},
            "Visible Pores":     {"feature": "lap_var",     "value": THR_PORES_LAP},
            "Oily Skin":         {"feature": "oily_ratio",  "value": THR_OILY_RATIO},
        },
        "class_results":   class_results,
        "macro_average":   {"precision": round(macro_P,4),
                            "recall":    round(macro_R,4),
                            "f1":        round(macro_F1,4)},
        "threshold_sweep": sweep_results,
        "best_thresholds": {c: best_thrs[c] for c in CONCERN_LABELS},
    }
    json_path = os.path.join(OUT_DIR, "concern_detection_eval_summary.json")
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2, ensure_ascii=False)

    # ── Figures ────────────────────────────────────────────────────────────────
    print("\nGenerating figures...")
    plot_confusion_matrix(class_results,
                          os.path.join(FIGURES_DIR, "confusion_matrix.png"))
    plot_prf1_bar(class_results,
                  os.path.join(FIGURES_DIR, "prf1_bar.png"))
    plot_threshold_sensitivity(sweep_results,
                               os.path.join(FIGURES_DIR, "threshold_sensitivity.png"))

    print(f"\nCSV detail : {csv_path}")
    print(f"JSON summary: {json_path}")
    print(f"Figures     : {FIGURES_DIR}/")
    print("\nDone.")
    return summary


if __name__ == "__main__":
    run_evaluation()
