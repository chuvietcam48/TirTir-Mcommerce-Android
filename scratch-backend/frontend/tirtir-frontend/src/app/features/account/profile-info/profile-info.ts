import { Component, OnInit, OnDestroy, inject, HostListener, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { UserService } from '../../../core/services/user.service';
import { AuthService } from '../../../core/services/auth.service';
import { User, SkinProfile } from '../../../core/models';
import { CanComponentDeactivate } from '../../../core/guards/can-deactivate.guard';
import { Subject, takeUntil } from 'rxjs';
import { environment } from '../../../../environments/environment';

@Component({
    selector: 'app-profile-info',
    standalone: true,
    imports: [CommonModule, ReactiveFormsModule],
    templateUrl: './profile-info.html',
    styleUrl: './profile-info.css'
})
export class ProfileInfoComponent implements OnInit, OnDestroy, CanComponentDeactivate {
    @ViewChild('videoElement') videoElement!: ElementRef<HTMLVideoElement>;
    @ViewChild('canvasElement') canvasElement!: ElementRef<HTMLCanvasElement>;
    private userService = inject(UserService);
    private authService = inject(AuthService);
    private fb = inject(FormBuilder);
    private http = inject(HttpClient);
    private router = inject(Router);
    private readonly aiBase = environment.apiUrl;

    profileForm!: FormGroup;
    loading = false;
    successMessage = '';
    errorMessage = '';
    user: User | null = null;

    // Skin profile
    skinProfile: SkinProfile | null = null;
    loadingSkinProfile = false;
    skinProfileError = '';

    // Avatar upload state
    showUploadModal = false;
    selectedFile: File | null = null;
    previewUrl: string | null = null;
    uploading = false;
    uploadProgress: number | null = null;

    // Unsaved changes tracking
    hasUnsavedChanges = false;
    initialFormValue: any = null;
    private destroy$ = new Subject<void>();

    // Camera state
    isCameraActive = false;
    stream: MediaStream | null = null;
    showCameraPreview = false;


    ngOnInit(): void {
        this.initForm();
        this.loadProfile();
        this.trackFormChanges();
        this.loadSkinProfile();
    }

    initForm(): void {
        this.profileForm = this.fb.group({
            name: ['', [Validators.required, Validators.minLength(2)]],
            phone: ['', [Validators.pattern(/^[0-9]{10,11}$/)]],
            gender: [''],
            birthDate: ['']
        });
    }


    loadProfile(): void {
        this.loading = true;
        this.userService.getProfile().subscribe({
            next: (user) => {
                this.user = user;
                const displayName = (user.name === 'User' ? 'Cam Meo Meo' : user.name) || 'Cam Meo Meo';
                this.profileForm.patchValue({
                    name: displayName,
                    phone: user.phone || '',
                    gender: user.gender || '',
                    birthDate: user.birthDate ? this.formatDateForInput(user.birthDate) : ''
                });
                this.initialFormValue = this.profileForm.value;
                this.hasUnsavedChanges = false;
                this.loading = false;
            },
            error: (error) => {
                this.errorMessage = error.message || 'Failed to load profile';
                this.loading = false;
            }
        });
    }

    onSubmit(): void {
        if (this.profileForm.invalid) {
            this.profileForm.markAllAsTouched();
            return;
        }

        this.loading = true;
        this.successMessage = '';
        this.errorMessage = '';

        const formData = this.profileForm.value;

        this.userService.updateProfile(formData).subscribe({
            next: (updatedUser) => {
                this.user = updatedUser;
                this.hasUnsavedChanges = false; // Mark as saved before reload
                this.loading = false;
                this.successMessage = 'Profile updated successfully! Reloading...';

                // Show success message briefly then reload
                setTimeout(() => {
                    window.location.reload();
                }, 1500);
            },
            error: (error) => {
                this.errorMessage = error.message || 'Failed to update profile';
                this.loading = false;
                console.error('Profile update error:', error);
            }
        });
    }

    /**
     * Get user initials for avatar display
     */
    getUserInitials(): string {
        if (!this.user?.name || this.user.name === 'User') return 'CM';
        const nameParts = this.user.name.trim().split(' ');
        if (nameParts.length === 1) {
            return nameParts[0].charAt(0).toUpperCase();
        }
        return (nameParts[0].charAt(0) + nameParts[nameParts.length - 1].charAt(0)).toUpperCase();
    }

    /**
     * Get avatar background color based on name
     */
    getAvatarColor(): string {
        if (!this.user?.name) return '#667eea';

        const colors = [
            '#667eea', '#764ba2', '#f093fb', '#4facfe',
            '#43e97b', '#fa709a', '#fee140', '#30cfd0'
        ];

        const charCode = this.user.name.charCodeAt(0);
        return colors[charCode % colors.length];
    }

    // ===== AVATAR UPLOAD METHODS =====

    /**
     * Open avatar upload modal
     */
    openAvatarUpload(): void {
        console.log('Avatar clicked! Opening upload modal...');
        this.showUploadModal = true;
    }


    /**
     * Close upload modal
     */
    closeUploadModal(): void {
        if (!this.uploading) {
            this.stopCamera(); // Ensure camera stops when closing
            this.showUploadModal = false;
            this.cancelUpload();
        }
    }

    /**
     * Trigger hidden file input
     */
    triggerFileInput(): void {
        const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
        fileInput?.click();
    }

    /**
     * Handle file selection
     */
    onFileSelected(event: Event): void {
        const input = event.target as HTMLInputElement;
        if (!input.files || input.files.length === 0) return;

        const file = input.files[0];

        // Validate file type
        const allowedTypes = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp'];
        if (!allowedTypes.includes(file.type)) {
            this.errorMessage = 'Only image files (JPG, PNG, WEBP) are allowed';
            this.successMessage = '';
            return;
        }

        // Validate file size (5MB)
        const maxSize = 5 * 1024 * 1024;
        if (file.size > maxSize) {
            this.errorMessage = 'File size must be under 5MB';
            this.successMessage = '';
            return;
        }

        this.selectedFile = file;

        // Create preview
        const reader = new FileReader();
        reader.onload = (e) => {
            this.previewUrl = e.target?.result as string;
        };
        reader.readAsDataURL(file);

        // Clear any previous errors
        this.errorMessage = '';
    }

    /**
     * Confirm and upload avatar
     */
    confirmUpload(): void {
        if (!this.selectedFile) return;

        this.uploading = true;
        this.uploadProgress = 0;
        this.errorMessage = '';
        this.successMessage = '';

        this.userService.uploadAvatar(this.selectedFile).subscribe({
            next: (response) => {
                this.uploadProgress = 100;
                setTimeout(() => {
                    this.user = response.user;
                    this.hasUnsavedChanges = false; // Avatar upload doesn't dirty the form
                    this.successMessage = 'Avatar updated successfully!';
                    this.uploading = false;
                    this.closeUploadModal();
                }, 500);
            },
            error: (error) => {
                this.errorMessage = error.message || 'Failed to upload avatar. Please try again.';
                this.uploading = false;
                this.uploadProgress = null;
            }
        });
    }

    /**
     * Cancel upload and clear selection
     */
    cancelUpload(): void {
        this.selectedFile = null;
        this.previewUrl = null;
        this.uploadProgress = null;

        // Reset file input
        const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
        if (fileInput) {
            fileInput.value = '';
        }
    }

    /**
     * Open camera for capture
     */
    async openCamera(): Promise<void> {
        this.showCameraPreview = true;
        this.errorMessage = '';

        try {
            this.stream = await navigator.mediaDevices.getUserMedia({
                video: { width: 640, height: 480, facingMode: 'user' }
            });

            // Need to wait for view to update so videoElement is available
            setTimeout(() => {
                if (this.videoElement) {
                    this.videoElement.nativeElement.srcObject = this.stream;
                    this.isCameraActive = true;
                }
            }, 100);
        } catch (err) {
            console.error('Error accessing camera:', err);
            this.errorMessage = 'Could not access camera. Please check permissions.';
            this.showCameraPreview = false;
        }
    }

    /**
     * Stop camera stream
     */
    stopCamera(): void {
        if (this.stream) {
            this.stream.getTracks().forEach(track => track.stop());
            this.stream = null;
        }
        this.isCameraActive = false;
        this.showCameraPreview = false;
    }

    /**
     * Capture photo from video stream
     */
    capturePhoto(): void {
        if (!this.videoElement || !this.canvasElement) return;

        const video = this.videoElement.nativeElement;
        const canvas = this.canvasElement.nativeElement;
        const ctx = canvas.getContext('2d');

        if (!ctx) return;

        // Set canvas size to video size
        canvas.width = video.videoWidth;
        canvas.height = video.videoHeight;

        // Draw current video frame to canvas
        ctx.drawImage(video, 0, 0, canvas.width, canvas.height);

        // Convert to blob/file
        canvas.toBlob((blob) => {
            if (blob) {
                const file = new File([blob], 'avatar-capture.jpg', { type: 'image/jpeg' });
                this.handleProcessedFile(file);
                this.stopCamera();
            }
        }, 'image/jpeg', 0.9);
    }

    /**
     * Internal helper to set selected file and preview
     */
    private handleProcessedFile(file: File): void {
        this.selectedFile = file;

        const reader = new FileReader();
        reader.onload = (e) => {
            this.previewUrl = e.target?.result as string;
        };
        reader.readAsDataURL(file);

        this.errorMessage = '';
    }

    /**
     * Get full avatar URL (handle relative paths from backend)
     */
    getAvatarUrl(avatar: string): string {
        if (avatar.startsWith('http')) {
            return avatar;
        }
        // Backend returns relative path like '/uploads/avatars/filename.jpg'
        // Extract base URL from environment.apiUrl (e.g., 'http://localhost:5001/api/v1' -> 'http://localhost:5001')
        const baseUrl = this.userService['apiUrl'].replace('/api/v1/users', '');
        return `${baseUrl}${avatar}`;
    }

    /**
     * Track form changes to detect unsaved edits
     */
    private trackFormChanges(): void {
        this.profileForm.valueChanges
            .pipe(takeUntil(this.destroy$))
            .subscribe((val) => {
                if (this.initialFormValue) {
                    this.hasUnsavedChanges = JSON.stringify(this.initialFormValue) !== JSON.stringify(val);
                }
            });
    }

    /**
     * Navigation guard: Warn user if leaving with unsaved changes
     */
    canDeactivate(): boolean {
        if (!this.hasUnsavedChanges) {
            return true;
        }

        return confirm('You have unsaved changes. Do you want to leave? Your changes will be lost.');
    }

    /**
     * Prevent browser close/reload if there are unsaved changes
     */
    @HostListener('window:beforeunload', ['$event'])
    unloadNotification($event: BeforeUnloadEvent): void {
        if (this.hasUnsavedChanges) {
            $event.preventDefault();
            $event.returnValue = true; // Required for Chrome
        }
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

    private formatDateForInput(date: Date | string): string {
        const d = new Date(date);
        const year = d.getFullYear();
        const month = String(d.getMonth() + 1).padStart(2, '0');
        const day = String(d.getDate()).padStart(2, '0');
        return `${year}-${month}-${day}`;
    }

    // ===== SKIN PROFILE METHODS =====

    loadSkinProfile(): void {
        if (!this.authService.isAuthenticated()) return;
        this.loadingSkinProfile = true;
        this.skinProfileError = '';
        this.http.get<{ success: boolean; data: { skinProfile: SkinProfile } | null }>(
            `${this.aiBase}/ai/latest-profile`
        ).subscribe({
            next: (res) => {
                this.skinProfile = res.data?.skinProfile || null;
                this.loadingSkinProfile = false;
            },
            error: () => {
                this.skinProfileError = 'Failed to load skin profile.';
                this.loadingSkinProfile = false;
            }
        });
    }

    navigateToShadeFinder(): void {
        this.router.navigate(['/shade-finder']);
    }

    getSkinTypeLabel(type: string): string {
        const map: Record<string, string> = {
            'Oily': 'Oily',
            'Dry': 'Dry',
            'Combination': 'Combination',
            'Normal': 'Normal',
            'Sensitive': 'Sensitive'
        };
        return map[type] || type;
    }

    getSkinToneLabel(tone: string): string {
        const map: Record<string, string> = {
            'Fair': 'Fair',
            'Light': 'Light',
            'Medium': 'Medium',
            'Tan': 'Tan',
            'Dark': 'Dark',
            'Deep': 'Deep'
        };
        return map[tone] || tone;
    }

    formatLastAnalyzed(date: Date | string): string {
        if (!date) return '';
        return new Date(date).toLocaleDateString('en-US', {
            day: '2-digit', month: '2-digit', year: 'numeric',
            hour: '2-digit', minute: '2-digit'
        });
    }
}
