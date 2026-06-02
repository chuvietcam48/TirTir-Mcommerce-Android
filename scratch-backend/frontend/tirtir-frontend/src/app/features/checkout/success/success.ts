import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { CartService } from '../../../core/services/cart.service';

@Component({
  selector: 'app-order-success',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './success.html',
  styleUrls: ['./success.css']
})
export class OrderSuccessComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private cartService = inject(CartService);

  orderId: string = '';
  amount: string = '';
  isProcessing: boolean = true;

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      // Direct checkout (like COD) or VNPAY redirect check
      const vnpResponseCode = params['vnp_ResponseCode'];
      
      if (vnpResponseCode) {
        if (vnpResponseCode === '00') {
          // Success code from VNPAY
          this.orderId = params['vnp_TxnRef'] || '';
          this.amount = params['vnp_Amount'] ? (parseInt(params['vnp_Amount']) / 100).toString() : '';
          this.finalizeSuccess();
        } else {
          // Failed VNPAY transaction
          this.router.navigate(['/checkout/failure'], { queryParams: { error: 'payment_failed' } });
        }
      } else if (params['orderId']) {
        // Generic success (e.g. COD or free order)
        this.orderId = params['orderId'];
        this.finalizeSuccess();
      } else {
        // No valid params found, maybe accessed directly?
        // Check if there's any state, otherwise redirect home
        this.router.navigate(['/']);
      }
    });
  }

  private finalizeSuccess(): void {
    // 100% sure the order was placed successfully as per user request
    this.cartService.clearCart();
    console.log('Cart cleared successfully after order.');
    
    this.isProcessing = false;
  }
}
