import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { ToastService } from './toast.service';
import { BehaviorSubject, firstValueFrom } from 'rxjs';
import { retry } from 'rxjs/operators';
import { AuthService } from './auth.service';

export interface MergeResult {
  mergedCart?: any;
  error?: string;
  itemsAdded?: number;
}

@Injectable({
  providedIn: 'root'
})
export class CartMergeService {
  private apiUrl = '/api/v1/cart/merge';
  public isMerging$ = new BehaviorSubject<boolean>(false);

  constructor(
    private http: HttpClient,
    private route: ActivatedRoute,
    private router: Router,
    private authService: AuthService,
    private toastService: ToastService
  ) {}

  async handlePostLoginMerge(): Promise<MergeResult> {
    const recoveryToken = this.route.snapshot.queryParamMap.get('recovery_token');
    
    if (!recoveryToken || !this.authService.isAuthenticated()) {
      return { error: 'NO_ACTION_NEEDED' }; 
    }

    this.isMerging$.next(true);

    try {
      const response = await firstValueFrom(
        this.http.post<MergeResult>(this.apiUrl, { guestCartToken: recoveryToken, mergeStrategy: 'union' })
        .pipe(retry(1))
      );

      // Assumes toastService has success(), warning(), error()
      if(this.toastService.success) {
        this.toastService.success(`Gộp giỏ hàng thành công. Đã thêm ${response.itemsAdded || 0} sản phẩm từ link khôi phục!`);
      }
      this.router.navigate(['/checkout'], { replaceUrl: true });
      return response;

    } catch (error) {
      const httpErr = error as HttpErrorResponse;
      const errorCode = httpErr.error?.error;

      if (errorCode === 'TOKEN_EXPIRED' || errorCode === 'INVALID_TOKEN') {
        if(this.toastService.warning) this.toastService.warning('Link ưu đãi khôi phục đã hết hạn. Giỏ hàng hiện tại vẫn được giữ nguyên.');
        this.router.navigate(['/cart'], { replaceUrl: true });
      } else {
        if(this.toastService.error) this.toastService.error('Có lỗi xảy ra khi đồng bộ giỏ hàng cũ. Vui lòng thử lại.');
      }
      return { error: errorCode || 'UNKNOWN_ERROR' };
    } finally {
      this.isMerging$.next(false);
    }
  }
}
