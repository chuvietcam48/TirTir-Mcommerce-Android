import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { HeaderComponent } from './shared/components/header/header';
import { FooterComponent } from './shared/components/footer/footer';
import { ChatWidgetComponent } from './features/chat-widget/chat-widget.component';
import { AccessibilityWidgetComponent } from './shared/components/accessibility-widget/accessibility-widget.component';
import { AnalyticsService } from './core/services/analytics.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, HeaderComponent, FooterComponent, ChatWidgetComponent, AccessibilityWidgetComponent],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class AppComponent {
  protected readonly title = signal('tirtir-frontend');

  constructor(private analytics: AnalyticsService) {
    this.analytics.startPageViewTracking();
  }
}
