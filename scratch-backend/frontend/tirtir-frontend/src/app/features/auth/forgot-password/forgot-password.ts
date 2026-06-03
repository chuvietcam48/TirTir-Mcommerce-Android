import { Component, inject } from '@angular/core';
import { RouterModule, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';

@Component({
    selector: 'app-forgot-password',
    standalone: true,
    imports: [CommonModule, RouterModule, ReactiveFormsModule],
    template: `
    <div class="forgot-container">
      <div class="forgot-card">

        <div class="forgot-header">
          <div class="brand-wordmark">TIRTIR</div>
          <h1 class="forgot-title">Reset Password</h1>
          <p class="forgot-subtitle">Enter your email and we'll send you reset instructions.</p>
        </div>

        <!-- Success State -->
        <div *ngIf="submitted" class="success-state" role="alert">
          <div class="success-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
              <polyline points="22 4 12 14.01 9 11.01"/>
            </svg>
          </div>
          <div class="success-text">
            <strong>Check your inbox</strong>
            <p>We've sent password reset instructions to <strong>{{ emailSent }}</strong>.</p>
          </div>
          <a routerLink="/login" class="back-btn">Return to Sign In</a>
        </div>

        <!-- Form State -->
        <form *ngIf="!submitted" [formGroup]="forgotForm" (ngSubmit)="onSubmit()" class="forgot-form">
          <div class="form-field">
            <label for="email" class="field-label">Email Address</label>
            <input
              id="email"
              type="email"
              formControlName="email"
              placeholder="your@email.com"
              class="field-input"
              autocomplete="email"
              [class.input-error]="forgotForm.get('email')?.invalid && forgotForm.get('email')?.touched"
            />
            <span *ngIf="forgotForm.get('email')?.invalid && forgotForm.get('email')?.touched" class="field-error" role="alert">
              Please enter a valid email address
            </span>
          </div>

          <button type="submit" id="sendResetLink" class="submit-button" [disabled]="loading">
            <span *ngIf="!loading">Send Reset Link</span>
            <span *ngIf="loading" class="btn-loading">
              <span class="btn-spinner"></span>
              Sending...
            </span>
          </button>

          <div class="back-to-login">
            <a routerLink="/login" id="backToLogin" class="back-link">← Back to Sign In</a>
          </div>
        </form>

        <div class="help-note">
          <p>Didn't receive an email? Check your spam folder or <a routerLink="/login">try again</a>.</p>
        </div>

      </div>
    </div>
  `,
    styles: [`
    .forgot-container {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: linear-gradient(135deg, #fafafa 0%, #f5f5f5 100%);
      padding: 2rem 1rem;
    }
    .forgot-card {
      background: white;
      padding: 3rem 2.5rem;
      border-radius: 8px;
      box-shadow: 0 2px 24px rgba(0, 0, 0, 0.08);
      max-width: 440px;
      width: 100%;
    }
    .forgot-header {
      text-align: center;
      margin-bottom: 2.5rem;
    }
    .brand-wordmark {
      font-family: 'Playfair Display', Georgia, serif;
      font-size: 1.25rem;
      font-weight: 700;
      letter-spacing: 0.25em;
      text-transform: uppercase;
      color: #1a1a1a;
      margin-bottom: 1.25rem;
    }
    .forgot-title {
      font-size: 1.875rem;
      font-weight: 600;
      color: #1a1a1a;
      margin: 0 0 0.5rem;
      letter-spacing: -0.5px;
    }
    .forgot-subtitle {
      font-size: 0.938rem;
      color: #666;
      margin: 0;
    }
    .forgot-form {
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
    }
    .form-field {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    .field-label {
      font-size: 0.875rem;
      font-weight: 500;
      color: #1a1a1a;
    }
    .field-input {
      padding: 0.875rem 1rem;
      font-size: 0.938rem;
      color: #1a1a1a;
      background: #fff;
      border: 1.5px solid #e5e5e5;
      border-radius: 6px;
      transition: border-color 0.2s;
      font-family: inherit;
      width: 100%;
    }
    .field-input:focus { outline: none; border-color: #1a1a1a; }
    .field-input.input-error { border-color: #dc2626; }
    .field-error { font-size: 0.813rem; color: #dc2626; }
    .submit-button {
      padding: 1rem;
      font-size: 1rem;
      font-weight: 600;
      color: #fff;
      background: #1a1a1a;
      border: none;
      border-radius: 6px;
      cursor: pointer;
      transition: all 0.2s;
      min-height: 52px;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      font-family: inherit;
    }
    .submit-button:hover:not(:disabled) { background: #333; }
    .submit-button:disabled { opacity: 0.65; cursor: not-allowed; }
    .btn-loading { display: flex; align-items: center; gap: 8px; }
    .btn-spinner {
      width: 14px; height: 14px;
      border: 2px solid rgba(255,255,255,0.35);
      border-top-color: #fff;
      border-radius: 50%;
      animation: spin 0.7s linear infinite;
    }
    @keyframes spin { to { transform: rotate(360deg); } }
    .back-to-login { text-align: center; }
    .back-link { font-size: 0.875rem; color: #666; text-decoration: none; }
    .back-link:hover { color: #1a1a1a; }
    .help-note {
      margin-top: 2rem;
      text-align: center;
      font-size: 0.813rem;
      color: #999;
    }
    .help-note a { color: #1a1a1a; text-decoration: underline; }
    /* Success state */
    .success-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 1rem;
      text-align: center;
      padding: 1rem 0;
    }
    .success-icon {
      width: 56px; height: 56px;
      border-radius: 50%;
      background: #f0fdf4;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #16a34a;
    }
    .success-icon svg { width: 28px; height: 28px; }
    .success-text strong { display: block; font-size: 1rem; color: #1a1a1a; margin-bottom: 0.5rem; }
    .success-text p { font-size: 0.875rem; color: #666; }
    .back-btn {
      margin-top: 0.5rem;
      display: inline-block;
      padding: 0.875rem 2rem;
      background: #1a1a1a;
      color: #fff;
      font-size: 0.875rem;
      font-weight: 600;
      border-radius: 6px;
      text-decoration: none;
      transition: background 0.2s;
    }
    .back-btn:hover { background: #333; }
  `],
})
export class ForgotPasswordComponent {
    private fb = inject(FormBuilder);

    forgotForm: FormGroup;
    loading = false;
    submitted = false;
    emailSent = '';

    constructor() {
        this.forgotForm = this.fb.group({
            email: ['', [Validators.required, Validators.email]]
        });
    }

    onSubmit(): void {
        if (this.forgotForm.invalid) {
            this.forgotForm.markAllAsTouched();
            return;
        }
        this.loading = true;
        this.emailSent = this.forgotForm.value.email;

        // Simulate API call — TODO: wire up to auth service when endpoint is ready
        setTimeout(() => {
            this.loading = false;
            this.submitted = true;
        }, 1200);
    }
}
