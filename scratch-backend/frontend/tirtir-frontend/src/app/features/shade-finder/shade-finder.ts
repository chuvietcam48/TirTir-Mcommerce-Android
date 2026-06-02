import { Component, ElementRef, ViewChild, OnDestroy, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router, RouterModule } from '@angular/router';
import { forkJoin, of, take } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { FaceTrackerService } from '../../core/services/face-tracker.service';
import { CartService } from '../../core/services/cart.service';
import { AuthService } from '../../core/services/auth.service';
import { ColorService } from '../../core/services/color.service';
import { environment } from '../../../environments/environment';

/** Response shape from POST /api/v1/shades/match */
interface ShadeMatch {
  _id: string;
  Product_ID: string;
  Product_Name: string;
  Shade_Name: string;
  Shade_Code: string;
  Hex_Code: string;
  Image_URL: string;
  Shade_Image: string;
  matchScore: number;
  deltaE: number;
  predictedUndertone: string;
  adjustmentNote: string;
  Undertone: string;
  Skin_Tone: string;
  Coverage: number;
  Hydration: number;
  Coverage_Profile: string;
  Finish_Type: string;
  Oxidation_Risk_Level: string;
}

/** Response from POST /api/ai/analyze-face */
interface SkinProfile {
  skinTone: string;
  undertone: string;
  skinType: string;
  concerns?: string[];
  confidence: number;
}

/** Single routine step from Gemini */
interface RoutineStep {
  step: string;
  product_id: string;
  reason: string;
  application_tip?: string;
  product?: {
    Name: string;
    Category: string;
    Price: number;
    Thumbnail_Images: string;
    slug?: string;
    _id: string;
    Product_ID: string;
  } | null;
}

interface SkinMetrics {
  hydration: number;
  elasticity: number;
  pigmentation: number;
  texture: number;
  sensitivity: number;
}

interface SkinEvolution {
  current: { hydration: number; texture: number };
  predicted: { hydration: number; texture: number };
}

/** Response from POST /api/ai/recommend-routine */
interface RoutineData {
  routine: RoutineStep[];
  advice: string;
  dermatologistNote?: string;
  skinMetrics?: SkinMetrics;
  skinEvolution?: SkinEvolution;
  totalPrice?: number;
  skinType?: string;
  isHeuristicGenerated?: boolean;
}

/** Full HTTP response wrapper */
interface RoutineResponse {
  success: boolean;
  data: RoutineData;
  fromCache?: boolean;
}


@Component({
  selector: 'app-shade-finder',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './shade-finder.html',
  styleUrls: ['./shade-finder.css']
})
export class ShadeFinderComponent implements OnInit, OnDestroy {
  @ViewChild('videoElement') videoElement!: ElementRef<HTMLVideoElement>;
  @ViewChild('canvasElement') canvasElement!: ElementRef<HTMLCanvasElement>;

  isCameraActive = false;
  isProcessing = false;
  stream: MediaStream | null = null;
  error: string | null = null;
  animationFrameId: number | null = null;

  selectedSkinType = 'Normal';
  skinTypeOptions = ['Normal', 'Dry', 'Oily', 'Combination', 'Sensitive'];

  // Instruction screen — shown before camera starts
  showInstructions = signal(true);
  isInitializingModel = signal(false);

  // Shade match results
  matchResults: ShadeMatch[] = [];
  bestMatch: ShadeMatch | null = null;
  userUndertone = '';

  // AI Routine results
  skinProfile = signal<SkinProfile | null>(null);
  routine = signal<RoutineData | null>(null);
  isLoadingRoutine = signal(false);
  routineError = signal<string | null>(null);
  lowConfidence = signal(false);  // F2: warn when AI confidence < 50%

  // Issue #16: Track shade match errors separately
  shadeMatchError = signal<string | null>(null);

  // UI
  showResultModal = signal(false);
  activeTab = signal<'shade' | 'report' | 'routine'>('shade');
  toastMessage = signal<string | null>(null);
  capturedImageUrl: string | null = null;
  private toastTimer: any = null;

  private readonly backendBase = environment.apiUrl.replace('/api/v1', ''); // http://localhost:5001
  private readonly apiBase = environment.apiUrl;
  private readonly aiBase = environment.apiUrl;

  // Lighting & Validation
  lightingStatus = signal<{ isValid: boolean, message: string, type: 'success' | 'warning' | 'error' }>({
    isValid: false,
    message: 'Starting camera...',
    type: 'warning'
  });

  // Live AI metrics (derived from real-time colour analysis)
  liveMetrics = signal<{ moisture: number; pores: number; redness: number; evenness: number } | null>(null);

  // FPS tracking
  fps = signal(0);
  private fpsFrames = 0;
  private fpsLastTime = performance.now();

  colorHistory: { r: number, g: number, b: number }[] = [];
  readonly HISTORY_SIZE = 15;

  constructor(
    public faceTracker: FaceTrackerService,
    private http: HttpClient,
    private cartService: CartService,
    private authService: AuthService,
    private router: Router,
    private colorService: ColorService
  ) { }

  async ngOnInit() {
    // Pre-load MediaPipe model in background while instructions are shown
    this.faceTracker.initialize();
  }

  async startFromInstructions() {
    this.isInitializingModel.set(true);
    await this.faceTracker.initialize(); // await in case still loading
    this.isInitializingModel.set(false);
    this.showInstructions.set(false);
    await this.startCamera();
  }

  ngOnDestroy() {
    this.stopCamera();
  }

  async startCamera() {
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({
        video: { width: 640, height: 480, facingMode: 'user' }
      });
      const video = this.videoElement.nativeElement;
      video.srcObject = this.stream;
      video.onloadeddata = () => {
        this.isCameraActive = true;
        this.detectLoop();
      };
    } catch (err: any) {
      // Issue #1: Differentiate camera errors
      if (err.name === 'NotAllowedError') {
        this.error = '🚫 Camera permission denied. Please allow camera access in your browser settings.';
      } else if (err.name === 'NotFoundError') {
        this.error = '📷 No camera found. Please connect a camera and try again.';
      } else if (err.name === 'NotReadableError') {
        this.error = '⚠️ Camera is in use by another application. Please close it and try again.';
      } else {
        this.error = `Cannot access camera: ${err.message || 'Unknown error'}. Please check permissions.`;
      }
    }
  }

  stopCamera() {
    if (this.stream) {
      this.stream.getTracks().forEach(track => track.stop());
      this.stream = null;
    }
    if (this.animationFrameId) {
      cancelAnimationFrame(this.animationFrameId);
    }
    this.isCameraActive = false;
    this.faceTracker.isFaceDetected.set(false);
  }

  detectLoop() {
    if (!this.isCameraActive || !this.videoElement?.nativeElement) return;

    // FPS counter
    this.fpsFrames++;
    const now = performance.now();
    if (now - this.fpsLastTime >= 1000) {
      this.fps.set(this.fpsFrames);
      this.fpsFrames = 0;
      this.fpsLastTime = now;
    }

    this.faceTracker.detectFace(this.videoElement.nativeElement);

    if (this.faceTracker.multipleDetected()) {
      // Issue #10: Multiple faces warning
      this.lightingStatus.set({
        isValid: false,
        message: '⚠️ Multiple faces detected. Please have only ONE person in frame.',
        type: 'error'
      });
      this.colorHistory = [];
      this.liveMetrics.set(null);
    } else if (this.faceTracker.isFaceDetected()) {
      this.processRealtimeValidation();
    } else {
      // Issue #11: Differentiated pose messages
      const pose = this.faceTracker.poseIssue();
      let msg = 'No face detected. Please look straight at the camera.';
      if (pose === 'eyesClosed') msg = '👁️ Eyes closed. Please open your eyes and look at the camera.';
      else if (pose === 'tilted') msg = '↔️ Head tilted. Please face the camera straight on.';

      this.lightingStatus.set({
        isValid: false,
        message: msg,
        type: 'warning'
      });
      this.colorHistory = [];
      this.liveMetrics.set(null);
    }
    this.animationFrameId = requestAnimationFrame(() => this.detectLoop());
  }

  processRealtimeValidation() {
    const points = this.faceTracker.facePoints();
    if (!points) return;

    // F3: Extract all 5 face points in one canvas draw
    const rawColors = this.extractColors([
      points.forehead, points.nose, points.leftCheek, points.rightCheek, points.chin
    ]);
    if (rawColors.length === 0) return;

    const avg = rawColors.reduce((a, c) => ({ r: a.r + c.r, g: a.g + c.g, b: a.b + c.b }), { r: 0, g: 0, b: 0 });
    avg.r /= rawColors.length; avg.g /= rawColors.length; avg.b /= rawColors.length;

    this.colorHistory.push(avg);
    if (this.colorHistory.length > this.HISTORY_SIZE) this.colorHistory.shift();

    const sm = this.colorHistory.reduce((a, c) => ({ r: a.r + c.r, g: a.g + c.g, b: a.b + c.b }), { r: 0, g: 0, b: 0 });
    sm.r = Math.round(sm.r / this.colorHistory.length);
    sm.g = Math.round(sm.g / this.colorHistory.length);
    sm.b = Math.round(sm.b / this.colorHistory.length);

    const v = this.colorService.validateSkinColor(sm.r, sm.g, sm.b);
    this.lightingStatus.set(v.isValid
      ? { isValid: true, message: 'Ready to scan', type: 'success' }
      : { isValid: false, message: v.reason || 'Lighting not adequate.', type: 'error' }
    );

    // Derive live metrics from colour analysis
    if (v.isValid && this.colorHistory.length >= 5) {
      const lum = 0.2126 * sm.r + 0.7152 * sm.g + 0.0722 * sm.b;
      // Moisture: higher blue channel relative to lum = more hydrated appearance
      const moisture = Math.min(99, Math.max(20, Math.round(40 + (sm.b / lum) * 35)));
      // Redness: elevated red channel relative to green
      const redness = Math.min(60, Math.max(5, Math.round(((sm.r - sm.g) / 255) * 120)));
      // Pores: variance across face points (lower variance = smoother)
      const variance = rawColors.reduce((acc, c) => acc + Math.abs(c.r - sm.r) + Math.abs(c.g - sm.g), 0) / rawColors.length;
      const pores = Math.min(50, Math.max(5, Math.round(variance * 0.8)));
      // Evenness: inverse of variance
      const evenness = Math.min(99, Math.max(40, Math.round(99 - pores)));
      this.liveMetrics.set({ moisture, redness, pores, evenness });
    }
  }

  /**
   * Capture a JPEG base64 snapshot from the video stream.
   * Reuses the existing hidden canvas element.
   */
  private captureBase64(): string | null {
    const video = this.videoElement?.nativeElement;
    const canvas = this.canvasElement?.nativeElement;
    if (!video || !canvas) return null;
    const ctx = canvas.getContext('2d');
    if (!ctx) return null;
    canvas.width = video.videoWidth || 640;
    canvas.height = video.videoHeight || 480;
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    return canvas.toDataURL('image/jpeg', 0.85);
  }

  scanShade() {
    if (this.isProcessing) return;
    if (!this.faceTracker.isFaceDetected() || !this.lightingStatus().isValid) {
      this.error = this.lightingStatus().message;
      return;
    }
    if (this.colorHistory.length < 5) {
      this.error = 'Not enough data. Hold still for 2 more seconds.';
      return;
    }

    this.isProcessing = true;
    this.error = null;
    this.routine.set(null);
    this.skinProfile.set(null);
    this.routineError.set(null);
    this.activeTab.set('shade');

    // ── 1. Average colour from history ─────────────────────────────────────
    const sum = this.colorHistory.reduce((a, c) => ({ r: a.r + c.r, g: a.g + c.g, b: a.b + c.b }), { r: 0, g: 0, b: 0 });
    const color = {
      r: Math.round(sum.r / this.colorHistory.length),
      g: Math.round(sum.g / this.colorHistory.length),
      b: Math.round(sum.b / this.colorHistory.length)
    };

    // ── 2. Capture base64 once, reuse for AI ───────────────────────────────
    const imageData = this.captureBase64();
    this.capturedImageUrl = imageData; // store for Skin Report face preview

    // ── 3. Shade match (RGB) ────────────────────────────────────────────────
    // Issue #16: Track shade match errors
    this.shadeMatchError.set(null);
    const shade$ = this.http.post<ShadeMatch[]>(`${this.apiBase}/shades/match`, {
      ...color,
      skinType: this.selectedSkinType
    }).pipe(catchError((error) => {
      console.error('Shade match error:', error);
      this.shadeMatchError.set('API');
      return of([] as ShadeMatch[]);
    }));

    // ── 4. AI face analyze (runs in parallel with shade match) ──────────────
    const analyze$ = imageData
      ? this.http.post<{ success: boolean; data: SkinProfile; saved?: boolean }>(
          `${this.aiBase}/ai/analyze-face`,
          { imageData }
        ).pipe(catchError(() => of(null)))
      : of(null);

    // ── 5. Run shade + analyze in parallel, THEN call routine with both results ──
    this.isLoadingRoutine.set(true);

    forkJoin({ shade: shade$, analyze: analyze$ })
      .pipe(
        take(1),
        switchMap(({ shade, analyze }) => {
          // Process shade results immediately
          const shadeResults = shade as ShadeMatch[];
          this.matchResults = shadeResults;
          this.bestMatch = shadeResults.length > 0 ? shadeResults[0] : null;
          this.userUndertone = this.bestMatch?.predictedUndertone || '';

          // Process skin profile
          if (analyze?.success && analyze.data) {
            this.skinProfile.set(analyze.data);
            // F2: Confidence threshold warning
            this.lowConfidence.set((analyze.data.confidence || 0) < 50);

            if (analyze.saved) {
              this.showToast('✅ Results saved to your profile');
            }
          }

          const profile = this.skinProfile();

          if (!profile) {
            return of(null);
          }

          // ── Call recommend-routine WITH shade match data ──
          return this.http.post<RoutineResponse>(
            `${this.aiBase}/ai/recommend-routine`,
            {
              skinType:  profile.skinType  || this.selectedSkinType,
              skinTone:  profile.skinTone,
              undertone: profile.undertone,
              concerns:  profile.concerns,
              shadeMatchProduct: this.bestMatch ? {
                Product_ID: this.bestMatch.Product_ID,
                Product_Name: this.bestMatch.Product_Name,
                Shade_Name: this.bestMatch.Shade_Name,
                Shade_Code: this.bestMatch.Shade_Code
              } : null
            }
          ).pipe(catchError(() => of(null)));
        })
      )
      .subscribe({
        next: (routine: RoutineResponse | null) => {
          if (routine?.success && routine.data) {
            this.routine.set(routine.data);
          } else {
            this.routineError.set('AI Routine is unavailable at the moment. Please try again later.');
          }

          // Save scan result to localStorage if guest — flushed to account after login
          if (!this.authService.isAuthenticated()) {
            const profile = this.skinProfile();
            if (profile) {
              localStorage.setItem('tirtir_pending_scan', JSON.stringify({
                skinTone:   profile.skinTone,
                undertone:  profile.undertone,
                skinType:   profile.skinType,
                concerns:   profile.concerns || [],
                confidence: profile.confidence,
                routine:    routine?.data?.routine ?? [],
                timestamp:  new Date().toISOString(),
              }));
            }
          }

          this.isLoadingRoutine.set(false);
          this.isProcessing = false;
          this.showResultModal.set(true);
        },
        error: () => {
          this.error = 'Cannot connect to server. Please try again.';
          this.isProcessing = false;
          this.isLoadingRoutine.set(false);
        }
      });
  }

  /** Resolve image path: relative → full URL, absolute → as-is */
  resolveImage(path: string | undefined): string {
    if (!path) return 'https://placehold.co/300x300/f5f5f5/999?text=No+Image';
    if (path.startsWith('http')) return path;
    return `${this.backendBase}/${path.startsWith('/') ? path.slice(1) : path}`;
  }

  /**
   * Convert deltaE-based matchScore to a human-readable match percentage.
   * deltaE 2000 perceptual scale:
   *   0-1   → imperceptible difference → 100%
   *   1-2   → barely perceptible       → ~95%
   *   2-5   → noticeable               → ~85–70%
   *   5-10  → clearly different        → ~60–40%
   *   10+   → different colours        → <40%
   * Formula: 100 * e^(-deltaE / 7) capped to [0, 100].
   */
  matchPercent(score: number): number {
    // score is matchScore = deltaE + undertone/brightness penalties
    // Use the deltaE portion (score) in an exponential decay
    return Math.max(0, Math.min(100, Math.round(100 * Math.exp(-score / 7))));
  }

  addToCart(match: ShadeMatch) {
    if (!match.Product_ID) return;
    if (!this.authService.isAuthenticated()) {
      this.cartService.savePendingItems([{ productId: match.Product_ID, quantity: 1, shade: match.Shade_Name }]);
      this.showToast('Please login to add to cart — your selection has been saved!');
      this.router.navigate(['/login'], { queryParams: { returnUrl: '/shade-finder' } });
      return;
    }
    this.cartService.addToCart({
      productId: match.Product_ID,
      quantity: 1,
      shade: match.Shade_Name
    }).subscribe({
      next: () => this.showToast(`✅ Added ${match.Product_Name} - ${match.Shade_Name} to cart!`),
      error: () => this.showToast('❌ Failed to add. Please try again.')
    });
  }

  addRoutineProductToCart(step: RoutineStep) {
    if (!step.product?.Product_ID) return;
    if (!this.authService.isAuthenticated()) {
      this.cartService.savePendingItems([{ productId: step.product.Product_ID, quantity: 1 }]);
      this.showToast('Please login to add to cart — your selection has been saved!');
      this.router.navigate(['/login'], { queryParams: { returnUrl: '/shade-finder' } });
      return;
    }
    this.cartService.addToCart({
      productId: step.product.Product_ID,
      quantity: 1
    }).subscribe({
      next: () => this.showToast(`Added ${step.product?.Name} to cart!`),
      error: () => this.showToast('Failed to add. Please try again.')
    });
  }

  addAllToCart() {
    const steps = this.routine()?.routine ?? [];
    const items = steps
      .filter(s => s.product?.Product_ID && !this.skippedSteps().has(s.step))
      .map(s => ({ productId: s.product!.Product_ID, quantity: 1 }));
    if (items.length === 0) return;

    if (!this.authService.isAuthenticated()) {
      this.cartService.savePendingItems(items);
      this.showToast(`Please login to add to cart — ${items.length} product(s) have been saved!`);
      this.router.navigate(['/login'], { queryParams: { returnUrl: '/shade-finder' } });
      return;
    }

    const adds = items.map(item => this.cartService.addToCart(item));
    forkJoin(adds).subscribe({
      next: () => this.showToast(`Added ${adds.length} product(s) to cart!`),
      error: () => this.showToast('Error adding to cart.')
    });
  }

  /** Skip/unskip a routine step */
  skippedSteps = signal<Set<string>>(new Set());

  /**
   * Returns adjusted evolution data based on how many steps are skipped.
   * Each skipped step: hydration -3%, texture -2% (capped at -15%/-10%).
   * Called reactively from template since it reads signals.
   */
  getAdjustedEvolution() {
    const evo = this.routine()?.skinEvolution;
    if (!evo) return null;
    const skipCount = this.skippedSteps().size;
    const hydrationPenalty = Math.min(15, skipCount * 3);
    const texturePenalty   = Math.min(10, skipCount * 2);
    return {
      current: { ...evo.current },
      predicted: {
        hydration: Math.max(evo.current.hydration, evo.predicted.hydration - hydrationPenalty),
        texture:   Math.max(evo.current.texture,   evo.predicted.texture   - texturePenalty)
      },
      skippedCount: skipCount,
      isReduced: skipCount > 0
    };
  }

  toggleSkipStep(stepName: string) {
    const current = new Set(this.skippedSteps());
    if (current.has(stepName)) {
      current.delete(stepName);
    } else {
      current.add(stepName);
    }
    this.skippedSteps.set(current);
  }

  closeModal() {
    this.showResultModal.set(false);
  }

  showToast(message: string) {
    this.toastMessage.set(message);
    if (this.toastTimer) clearTimeout(this.toastTimer);
    this.toastTimer = setTimeout(() => this.toastMessage.set(null), 3000);
  }

  /**
   * F3: Optimized — draw canvas ONCE, then sample all 5 points.
   * Returns array of colors for all requested points.
   */
  extractColors(points: { x: number; y: number }[]): { r: number; g: number; b: number }[] {
    const video = this.videoElement.nativeElement;
    const canvas = this.canvasElement.nativeElement;
    const ctx = canvas.getContext('2d');
    if (!ctx) return [];
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height); // Draw ONCE

    const results: { r: number; g: number; b: number }[] = [];
    const sz = 10;
    for (const pt of points) {
      const px = Math.floor(pt.x * canvas.width);
      const py = Math.floor(pt.y * canvas.height);
      try {
        const frame = ctx.getImageData(Math.max(0, px - sz / 2), Math.max(0, py - sz / 2), sz, sz);
        let r = 0, g = 0, b = 0;
        const n = frame.data.length / 4;
        for (let i = 0; i < frame.data.length; i += 4) {
          r += frame.data[i]; g += frame.data[i + 1]; b += frame.data[i + 2];
        }
        results.push({ r: Math.round(r / n), g: Math.round(g / n), b: Math.round(b / n) });
      } catch { /* skip invalid point */ }
    }
    return results;
  }
}
