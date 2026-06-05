import { Injectable, signal } from '@angular/core';
import { FilesetResolver, FaceLandmarker } from '@mediapipe/tasks-vision';

export interface FacePoints {
  forehead: {x: number, y: number};
  nose: {x: number, y: number};
  leftCheek: {x: number, y: number};
  rightCheek: {x: number, y: number};
  chin: {x: number, y: number};
}

@Injectable({ providedIn: 'root' })
export class FaceTrackerService {
  private faceLandmarker: FaceLandmarker | undefined;
  private isInitialized = false;
  private initPromise: Promise<void> | null = null;

  // Signals for UI state
  public isFaceDetected = signal(false);
  public facePoints = signal<FacePoints | null>(null);

  // Issue #10: Multiple face detection
  public multipleDetected = signal(false);

  // Issue #11: Differentiated pose issues
  public isPoseValid = signal(false);
  public poseIssue = signal<'noFace' | 'eyesClosed' | 'tilted' | null>(null);

  // Issue #13: MediaPipe init error
  public initError = signal<string | null>(null);

  async initialize() {
    if (this.isInitialized) return;
    if (this.initPromise) return this.initPromise;

    this.initPromise = (async () => {
      try {
        const filesetResolver = await FilesetResolver.forVisionTasks(
          'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.0/wasm'
        );
        this.faceLandmarker = await FaceLandmarker.createFromOptions(filesetResolver, {
          baseOptions: {
            modelAssetPath: 'https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task',
            delegate: 'GPU'
          },
          outputFaceBlendshapes: true,
          runningMode: 'VIDEO',
          numFaces: 2  // Issue #10: detect up to 2 faces so we can warn
        });
        this.isInitialized = true;
        this.initError.set(null);
        console.log('✅ Face detection model loaded successfully');
      } catch (error: any) {
        console.error('❌ Face detection model failed to load:', error);
        this.initError.set(`Face detection failed: ${error?.message || 'Unknown error'}`);
        this.isInitialized = false;
      }
    })();

    return this.initPromise;
  }

  detectFace(videoElement: HTMLVideoElement) {
    if (!this.faceLandmarker) return;

    const startTimeMs = performance.now();
    const results = this.faceLandmarker.detectForVideo(videoElement, startTimeMs);

    // Issue #10: Check for multiple faces
    if (results.faceLandmarks.length > 1) {
      this.isFaceDetected.set(false);
      this.multipleDetected.set(true);
      this.facePoints.set(null);
      this.isPoseValid.set(false);
      this.poseIssue.set(null);
      console.warn(`⚠️ Multiple faces detected (${results.faceLandmarks.length}). Please have only ONE person in frame.`);
      return;
    }

    this.multipleDetected.set(false);

    if (results.faceLandmarks.length === 0) {
      this.isFaceDetected.set(false);
      this.facePoints.set(null);
      this.isPoseValid.set(false);
      this.poseIssue.set('noFace');
      return;
    }

    const landmarks = results.faceLandmarks[0];
    const blendshapes = results.faceBlendshapes[0];

    // Pose Validation
    const noseX = landmarks[1].x;
    const leftCheekX = landmarks[117].x;
    const rightCheekX = landmarks[346].x;
    const cheekMidpoint = (leftCheekX + rightCheekX) / 2;
    const yawDeviation = Math.abs(noseX - cheekMidpoint);

    const eyeBlinkLeft = blendshapes.categories.find(c => c.categoryName === 'eyeBlinkLeft')?.score || 0;
    const eyeBlinkRight = blendshapes.categories.find(c => c.categoryName === 'eyeBlinkRight')?.score || 0;

    const isEyesOpen = eyeBlinkLeft < 0.5 && eyeBlinkRight < 0.5;
    const isHeadStraight = yawDeviation < 0.05;

    if (isEyesOpen && isHeadStraight) {
      this.isFaceDetected.set(true);
      this.isPoseValid.set(true);
      this.poseIssue.set(null);

      this.facePoints.set({
        forehead: { x: landmarks[151].x, y: landmarks[151].y },
        nose: { x: landmarks[1].x, y: landmarks[1].y },
        leftCheek: { x: landmarks[117].x, y: landmarks[117].y },
        rightCheek: { x: landmarks[346].x, y: landmarks[346].y },
        chin: { x: landmarks[199].x, y: landmarks[199].y }
      });
    } else {
      // Issue #11: Differentiate pose issues
      this.isFaceDetected.set(false);
      this.isPoseValid.set(false);
      this.facePoints.set(null);

      if (!isEyesOpen) {
        this.poseIssue.set('eyesClosed');
      } else if (!isHeadStraight) {
        this.poseIssue.set('tilted');
      }
    }
  }
}
