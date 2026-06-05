import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'app-reset-password',
    standalone: true,
    imports: [CommonModule, RouterModule],
    template: `
    <div class="page-container">
      <div class="content-card">
        <h1>Reset Password</h1>
        <p>Password reset feature coming soon.</p>
        <a routerLink="/login" class="back-link">Back to Login</a>
      </div>
    </div>
  `,
    styles: [`
    .page-container {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: linear-gradient(135deg, #fafafa 0%, #f5f5f5 100%);
      padding: 2rem;
    }
    .content-card {
      background: white;
      padding: 3rem 2.5rem;
      border-radius: 8px;
      box-shadow: 0 2px 24px rgba(0, 0, 0, 0.08);
      text-align: center;
      max-width: 440px;
    }
    h1 {
      font-size: 1.875rem;
      margin-bottom: 1rem;
      color: #1a1a1a;
    }
    p {
      color: #666;
      margin-bottom: 2rem;
    }
    .back-link {
      color: #1a1a1a;
      font-weight: 600;
      text-decoration: none;
    }
    .back-link:hover {
      text-decoration: underline;
    }
  `],
})
export class ResetPasswordComponent { }
