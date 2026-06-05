import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MenuItem } from '../../../core/services/menu.service';

@Component({
    selector: 'app-makeup-mega-menu',
    standalone: true,
    imports: [CommonModule, RouterModule],
    templateUrl: './makeup-mega-menu.html',
    styleUrl: './makeup-mega-menu.css',
})
export class MakeupMegaMenuComponent {
    @Input() categories: MenuItem[] = [];

    get faceItems() {
        return this.categories.find(c => c.label === 'Face')?.children || [];
    }

    get lipItems() {
        return this.categories.find(c => c.label === 'Lip')?.children || [];
    }
}
