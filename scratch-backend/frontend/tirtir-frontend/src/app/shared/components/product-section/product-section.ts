import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-product-section',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './product-section.html',
  styleUrl: './product-section.css',
})
export class ProductSection {
  @Input() title: string = '';
  @Input() viewAllLink: string = '';
}
