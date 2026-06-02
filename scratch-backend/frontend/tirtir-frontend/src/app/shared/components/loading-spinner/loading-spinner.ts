import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Loading spinner component for async operations
 */
@Component({
    selector: 'app-loading-spinner',
    standalone: true,
    imports: [CommonModule],
    template: `
    <div class="spinner-container">
      <div class="spinner"></div>
    </div>
  `,
    styles: [`
    .spinner-container {
      display: flex;
      justify-content: center;
      align-items: center;
      padding: 2rem;
    }

    .spinner {
      width: 40px;
      height: 40px;
      border: 3px solid #f3f3f3;
      border-top: 3px solid #1a1a1a;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin {
      0% { transform: rotate(0deg); }
      100% { transform: rotate(360deg); }
    }
  `],
})
export class LoadingSpinnerComponent { }
