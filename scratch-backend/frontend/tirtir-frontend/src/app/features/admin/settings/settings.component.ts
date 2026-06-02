import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SettingService } from '../../../core/services/setting.service';

@Component({
    selector: 'app-admin-settings',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './settings.component.html',
    styleUrls: ['./settings.component.scss']
})
export class AdminSettingsComponent implements OnInit {
    private settingService = inject(SettingService);

    freeShippingThreshold: number = 400000;
    isLoading: boolean = true;
    isSaving: boolean = false;
    successMessage: string = '';
    errorMessage: string = '';

    ngOnInit(): void {
        this.settingService.getFreeShippingThreshold().subscribe({
            next: (threshold) => {
                this.freeShippingThreshold = threshold;
                this.isLoading = false;
            },
            error: (err) => {
                this.errorMessage = 'Failed to load settings';
                this.isLoading = false;
            }
        });
    }

    saveSettings(): void {
        if (this.freeShippingThreshold < 0) {
            this.errorMessage = 'Threshold cannot be negative';
            return;
        }

        this.isSaving = true;
        this.errorMessage = '';
        this.successMessage = '';

        this.settingService.updateSettings({ freeShippingThreshold: this.freeShippingThreshold }).subscribe({
            next: () => {
                this.isSaving = false;
                this.successMessage = 'Settings saved successfully!';
                setTimeout(() => this.successMessage = '', 3000);
            },
            error: (err) => {
                this.isSaving = false;
                this.errorMessage = 'Failed to save settings. Make sure you are an Admin.';
            }
        });
    }
}
