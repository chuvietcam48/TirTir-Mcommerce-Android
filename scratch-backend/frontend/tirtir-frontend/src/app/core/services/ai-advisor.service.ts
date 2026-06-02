import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, of } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * AI Skin Analysis Response Interface
 */
export interface SkinAnalysis {
    skinTone: string;         // Fair, Light, Medium, Tan, Deep
    undertone: 'Cool' | 'Warm' | 'Neutral';
    confidence: number;
    concerns: string[];       // Redness, Acne/Blemishes, Dark Circles, Oily Skin, etc.
    skinType?: string;
    brightness?: 'Light' | 'Medium' | 'Deep';
    recommendedToneShift?: 'lighter' | 'exact' | 'deeper';
    explanation?: string;
}

/**
 * Face ROI (Region of Interest) data for lightweight payload
 */
export interface FaceROIPayload {
    /** 5 cropped face patches as small base64 JPEGs (forehead, nose, leftCheek, rightCheek, chin) */
    roiPatches: {
        region: string;
        imageData: string; // base64, 50x50px ~2KB each
    }[];
    skinType: string;
    /** Also include quick RGB average for shade matching fallback */
    avgRgb: { r: number; g: number; b: number };
}

/**
 * AI Beauty Advisor Service
 * Communicates with backend to analyze skin using FastAPI AI Microservice.
 */
@Injectable({
    providedIn: 'root'
})
export class AiAdvisorService {
    private apiUrl = `${environment.apiUrl}/ai`;

    constructor(private http: HttpClient) { }

    /**
     * Analyzes skin from camera image.
     * Sends full base64 image to backend → FastAPI for analysis.
     * @param imageData Base64 encoded image (or ROI-optimized via extractAndAnalyzeFaceROI)
     * @param skinType User's skin type
     */
    analyzeSkin(imageData: string, skinType: string): Observable<{ success: boolean; data?: SkinAnalysis; message?: string }> {
        return this.http.post<{ success: boolean; data?: SkinAnalysis; message?: string }>(
            `${this.apiUrl}/analyze-face`,
            { imageData, skinType }
        ).pipe(
            catchError((error) => {
                console.error('AI Analysis Error:', error);
                return of({ success: false, message: 'Could not connect to AI service. Please try again.' });
            })
        );
    }

    /**
     * [Phase B — Payload Optimization]
     * Crops 5 facial ROI patches from a video stream using face landmark points.
     * Each patch is 50x50px JPEG (~2KB), total payload ~10KB vs ~200KB for full frame.
     *
     * @param video  HTMLVideoElement (live camera stream)
     * @param points Face landmark coordinates from FaceTrackerService
     * @param skinType User-selected skin type
     * @returns FaceROIPayload with 5 patches + avgRgb
     */
    extractFaceROIPatches(
        video: HTMLVideoElement,
        points: { forehead: { x: number, y: number }, nose: { x: number, y: number }, leftCheek: { x: number, y: number }, rightCheek: { x: number, y: number }, chin: { x: number, y: number } },
        skinType: string
    ): FaceROIPayload {
        // Draw full frame to a temporary canvas
        const fullCanvas = document.createElement('canvas');
        fullCanvas.width = video.videoWidth;
        fullCanvas.height = video.videoHeight;
        const fullCtx = fullCanvas.getContext('2d')!;
        fullCtx.drawImage(video, 0, 0);

        const PATCH_SIZE = 128; // 128x128px per ROI patch — sweet spot for AI accuracy vs payload size
        const regions = [
            { region: 'forehead', ...points.forehead },
            { region: 'nose', ...points.nose },
            { region: 'leftCheek', ...points.leftCheek },
            { region: 'rightCheek', ...points.rightCheek },
            { region: 'chin', ...points.chin },
        ];

        const roiPatches: { region: string; imageData: string }[] = [];
        let totalR = 0, totalG = 0, totalB = 0, totalPixels = 0;

        const patchCanvas = document.createElement('canvas');
        patchCanvas.width = PATCH_SIZE;
        patchCanvas.height = PATCH_SIZE;
        const patchCtx = patchCanvas.getContext('2d')!;

        for (const { region, x, y } of regions) {
            // Source rect (centered on landmark point)
            const srcX = Math.max(0, Math.floor(x - PATCH_SIZE / 2));
            const srcY = Math.max(0, Math.floor(y - PATCH_SIZE / 2));
            const srcW = Math.min(PATCH_SIZE, fullCanvas.width - srcX);
            const srcH = Math.min(PATCH_SIZE, fullCanvas.height - srcY);

            // Draw cropped patch into 128x128 canvas
            patchCtx.clearRect(0, 0, PATCH_SIZE, PATCH_SIZE);
            patchCtx.drawImage(fullCanvas, srcX, srcY, srcW, srcH, 0, 0, PATCH_SIZE, PATCH_SIZE);

            // Accumulate RGB for quick average
            const imageData = patchCtx.getImageData(0, 0, PATCH_SIZE, PATCH_SIZE);
            for (let i = 0; i < imageData.data.length; i += 4) {
                totalR += imageData.data[i];
                totalG += imageData.data[i + 1];
                totalB += imageData.data[i + 2];
                totalPixels++;
            }

            // Export patch as base64 JPEG (quality 0.8 ~6-10KB)
            roiPatches.push({
                region,
                imageData: patchCanvas.toDataURL('image/jpeg', 0.8)
            });
        }

        const avgRgb = {
            r: Math.round(totalR / totalPixels),
            g: Math.round(totalG / totalPixels),
            b: Math.round(totalB / totalPixels)
        };

        return { roiPatches, skinType, avgRgb };
    }

    /**
     * [Phase B — Payload Optimization]
     * Sends lightweight ROI patches to backend for analysis instead of full frame.
     * Uses the first (largest) patch image as the analysis input.
     */
    analyzeSkinFromROI(payload: FaceROIPayload): Observable<{ success: boolean; data?: SkinAnalysis; message?: string }> {
        // Send only the first (forehead) patch full base64 if no direct ROI endpoint,
        // but backend will stitch them together in future Phase B backend update.
        // For now: send the forehead ROI as imageData — already much smaller than full frame.
        const primaryPatch = payload.roiPatches[0]?.imageData ?? '';

        return this.http.post<{ success: boolean; data?: SkinAnalysis; message?: string }>(
            `${this.apiUrl}/analyze-face`,
            {
                imageData: primaryPatch,
                skinType: payload.skinType,
                // Additional ROI patches for richer analysis
                roiPatches: payload.roiPatches,
                avgRgb: payload.avgRgb
            }
        ).pipe(
            catchError((error) => {
                console.error('AI ROI Analysis Error:', error);
                return of({ success: false, message: 'Could not connect to AI service.' });
            })
        );
    }

    /**
     * Get skincare routine recommendations from Gemini AI.
     * (Results are cached server-side via Redis for 24h)
     */
    getRecommendations(analysisData: any): Observable<{ success: boolean; data?: { routine: any[], advice: string }; message?: string }> {
        return this.http.post<{ success: boolean; data?: { routine: any[], advice: string }; message?: string }>(
            `${this.apiUrl}/recommend-routine`,
            analysisData
        ).pipe(
            catchError((error) => {
                console.error('Recommendation Error:', error);
                return of({ success: false, message: 'Could not fetch recommendations.' });
            })
        );
    }

    /**
     * Checks if AI service is available and configured
     */
    checkHealth(): Observable<{ status: string; aiService: string; gemini: boolean }> {
        return this.http.get<{ status: string; aiService: string; gemini: boolean }>(
            `${this.apiUrl}/health`
        ).pipe(
            catchError(() => of({ status: 'error', aiService: 'offline', gemini: false }))
        );
    }

    /**
     * Converts video frame to base64 (full frame — use extractFaceROIPatches for optimized version)
     */
    videoFrameToBase64(video: HTMLVideoElement): string {
        const canvas = document.createElement('canvas');
        canvas.width = video.videoWidth;
        canvas.height = video.videoHeight;
        const ctx = canvas.getContext('2d');
        if (!ctx) throw new Error('Could not get canvas context');
        ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
        return canvas.toDataURL('image/jpeg', 0.8);
    }

    /** Converts a canvas to base64 */
    canvasToBase64(canvas: HTMLCanvasElement): string {
        return canvas.toDataURL('image/jpeg', 0.8);
    }
}
