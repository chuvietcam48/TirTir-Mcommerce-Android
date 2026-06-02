import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MenuItem } from '../../../core/services/menu.service';

@Component({
    selector: 'app-skincare-mega-menu',
    standalone: true,
    imports: [CommonModule, RouterModule],
    templateUrl: './skincare-mega-menu.html',
    styleUrl: './skincare-mega-menu.css',
})
export class SkincareMegaMenuComponent {
    @Input() categories: MenuItem[] = [];

    get cleanseTonerItems() {
        return this.categories.find(c =>
            c.label === 'Cleanse & Toner' ||
            c.label === 'Cleanse & Tone'
        )?.children || [];
    }

    get treatmentsItems() {
        return this.categories.find(c => c.label === 'Treatments')?.children || [];
    }

    get moisturizeSunscreenItems() {
        return this.categories.find(c =>
            c.label === 'Moisturize & Sunscreen' ||
            c.label === 'Moisturize & Cream'
        )?.children || [];
    }
}
