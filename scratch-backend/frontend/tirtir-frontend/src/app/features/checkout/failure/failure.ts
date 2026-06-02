import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-order-failure',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './failure.html',
  styleUrls: ['./failure.css']
})
export class OrderFailureComponent implements OnInit {
   private route = inject(ActivatedRoute);
   errorMessage = 'An error occurred while creating your order.';
   
   ngOnInit() {
      this.route.queryParams.subscribe(p => {
         if (p['error'] === 'payment_failed') {
            this.errorMessage = 'The payment process was unsuccessful or cancelled.';
         } else if (p['error']) {
            this.errorMessage = p['error'];
         }
      });
   }
}
