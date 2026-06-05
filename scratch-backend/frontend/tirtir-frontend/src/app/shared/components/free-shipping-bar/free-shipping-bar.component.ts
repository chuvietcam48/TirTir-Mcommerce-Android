import { Component, Input, OnChanges, SimpleChanges, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SettingService } from '../../../core/services/setting.service';

@Component({
    selector: 'app-free-shipping-bar',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './free-shipping-bar.component.html',
    styleUrls: ['./free-shipping-bar.component.scss']
})
export class FreeShippingBarComponent implements OnChanges, OnInit {
    @Input() currentTotal: number = 0;

    private settingService = inject(SettingService);
    FREE_SHIPPING_LIMIT = 400000; // Default fallback
    progressPercentage: number = 0;
    remainingAmount: number = 0;
    isAchieved: boolean = false;

    ngOnInit(): void {
        this.settingService.getFreeShippingThreshold().subscribe(threshold => {
            if (threshold > 0) {
                this.FREE_SHIPPING_LIMIT = threshold;
                this.calculateProgress(); // Recalculate with new limit
            }
        });
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes['currentTotal']) {
            this.calculateProgress();
        }
    }

    private calculateProgress(): void {
        this.remainingAmount = Math.max(0, this.FREE_SHIPPING_LIMIT - this.currentTotal);
        this.progressPercentage = Math.min(100, Math.max(0, (this.currentTotal / this.FREE_SHIPPING_LIMIT) * 100));
        this.isAchieved = this.progressPercentage >= 100;
    }
}
