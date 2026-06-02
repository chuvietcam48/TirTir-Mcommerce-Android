import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { SettingService, Settings } from '../../core/services/setting.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.css']
})
export class SettingsComponent implements OnInit {
  settingsForm: FormGroup;
  loading = false;
  saving = false;
  message: { type: 'success' | 'error', text: string } | null = null;
  bannerPreview: string | null = null;
  apiUrl = environment.apiUrl.replace('/api/v1', ''); // Base URL for images

  constructor(
    private fb: FormBuilder,
    private settingService: SettingService
  ) {
    this.settingsForm = this.fb.group({
      bannerUrl: [''],
      shippingFee: [0, [Validators.required, Validators.min(0)]],
      freeShippingThreshold: [0, [Validators.required, Validators.min(0)]],
      contactPhone: ['', Validators.required],
      contactEmail: ['', [Validators.required, Validators.email]],
      socialLinks: this.fb.group({
        facebook: [''],
        instagram: [''],
        tiktok: ['']
      }),
      bankInfo: this.fb.group({
        bankName: [''],
        accountNumber: [''],
        accountHolder: ['']
      })
    });
  }

  ngOnInit(): void {
    this.loadSettings();
  }

  loadSettings(): void {
    this.loading = true;
    this.settingService.getSettings().subscribe({
      next: (settings) => {
        this.settingsForm.patchValue(settings);
        if (settings.bannerUrl) {
            // Check if it's a full URL or relative path
            if (settings.bannerUrl.startsWith('http')) {
                this.bannerPreview = settings.bannerUrl;
            } else {
                this.bannerPreview = `${this.apiUrl}${settings.bannerUrl}`;
            }
        }
        this.loading = false;
      },
      error: (err) => {
        this.showMessage('error', 'Failed to load settings');
        this.loading = false;
      }
    });
  }

  onBannerSelected(event: any): void {
    const file = event.target.files[0];
    if (file) {
      // Show preview immediately
      const reader = new FileReader();
      reader.onload = (e: any) => {
        this.bannerPreview = e.target.result;
      };
      reader.readAsDataURL(file);

      // Upload immediately
      this.saving = true;
      this.settingService.uploadBanner(file).subscribe({
        next: (response) => {
          this.settingsForm.patchValue({ bannerUrl: response.data.url });
          this.saving = false;
          this.showMessage('success', 'Banner uploaded successfully');
        },
        error: (err) => {
          this.saving = false;
          this.showMessage('error', 'Failed to upload banner');
        }
      });
    }
  }

  onSubmit(): void {
    if (this.settingsForm.invalid) return;

    this.saving = true;
    this.settingService.updateSettings(this.settingsForm.value).subscribe({
      next: () => {
        this.saving = false;
        this.showMessage('success', 'Settings saved successfully');
      },
      error: (err) => {
        this.saving = false;
        this.showMessage('error', 'Failed to save settings');
      }
    });
  }

  showMessage(type: 'success' | 'error', text: string): void {
    this.message = { type, text };
    setTimeout(() => this.message = null, 3000);
  }
}
