import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'app-analytics',
    standalone: true,
    imports: [CommonModule],
    template: `<div class="placeholder-page"><h1>Analytics</h1><p>Analytics & reporting will be implemented in Phase 7.</p></div>`,
    styles: [`.placeholder-page { font-family: 'Inter', sans-serif; } .placeholder-page h1 { font-size: 1.75rem; font-weight: 700; margin: 0 0 8px; } .placeholder-page p { color: #666; }`],
})
export class AnalyticsComponent { }
