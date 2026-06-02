import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AccessibilityService } from '../../../core/services/accessibility.service';

@Component({
    selector: 'app-accessibility-widget',
    standalone: true,
    imports: [CommonModule],
    template: `
    <div class="accessibility-widget" [class.open]="isOpen">
      <button class="toggle-btn" (click)="toggleMenu()" aria-label="Accessibility Options">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"></circle>
            <path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20"></path>
            <path d="M2 12h20"></path>
        </svg>
      </button>

      <div class="options-menu" *ngIf="isOpen">
        <h4>Accessibility Tools</h4>
        
        <button class="option-btn" 
                [class.active]="accessibilityService.isHighContrast()" 
                (click)="toggleHighContrast()">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 2v20"></path>
            <path d="M12 2a10 10 0 1 0 0 20z" fill="currentColor"></path>
          </svg>
          High Contrast
        </button>
      </div>
    </div>
  `,
    styles: [`
    .accessibility-widget {
      position: fixed;
      bottom: 20px;
      left: 20px;
      z-index: 9999;
    }
    
    .toggle-btn {
      width: 50px;
      height: 50px;
      border-radius: 50%;
      background: #111;
      color: white;
      border: none;
      box-shadow: 0 4px 10px rgba(0,0,0,0.2);
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: transform 0.2s;
    }
    
    .toggle-btn:hover {
      transform: scale(1.05);
    }
    
    .options-menu {
      position: absolute;
      bottom: 60px;
      left: 0;
      background: white;
      border-radius: 8px;
      box-shadow: 0 4px 15px rgba(0,0,0,0.1);
      padding: 15px;
      width: 220px;
      border: 1px solid #eee;
    }
    
    .options-menu h4 {
      margin-top: 0;
      margin-bottom: 15px;
      font-size: 14px;
      color: #333;
      border-bottom: 1px solid #eee;
      padding-bottom: 10px;
    }
    
    .option-btn {
      display: flex;
      align-items: center;
      gap: 10px;
      width: 100%;
      padding: 10px;
      border: 1px solid #ddd;
      background: #f9f9f9;
      border-radius: 4px;
      cursor: pointer;
      font-weight: 500;
      font-size: 13px;
      transition: all 0.2s;
    }
    
    .option-btn:hover {
      background: #eee;
    }
    
    .option-btn.active {
      background: #111;
      color: white;
      border-color: #111;
    }
  `]
})
export class AccessibilityWidgetComponent {
    isOpen = false;
    accessibilityService = inject(AccessibilityService);

    toggleMenu() {
        this.isOpen = !this.isOpen;
    }

    toggleHighContrast() {
        this.accessibilityService.toggleHighContrast();
    }
}
