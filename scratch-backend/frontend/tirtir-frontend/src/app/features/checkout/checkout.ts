import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule, FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CartService } from '../../core/services/cart.service';
import { OrderService } from '../../core/services/order.service';
import { Cart, Address } from '../../core/models';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { ToastService } from '../../core/services/toast.service';
import { UserService } from '../../core/services/user.service';
import { LocationService, LocationItem } from '../../core/services/location.service';

@Component({
    selector: 'app-checkout',
    standalone: true,
    imports: [CommonModule, ReactiveFormsModule, FormsModule],
    templateUrl: './checkout.html',
    styleUrls: ['./checkout.css'],
})
export class CheckoutComponent implements OnInit {
    private fb = inject(FormBuilder);
    private cartService = inject(CartService);
    private orderService = inject(OrderService);
    private router = inject(Router);
    private http = inject(HttpClient);
    private toastService = inject(ToastService);
    private userService = inject(UserService);
    private locationService = inject(LocationService);

    checkoutForm: FormGroup;
    cart: Cart | null = null;
    loading = false;
    error = '';
    
    // Address Selection State
    savedAddresses: Address[] = [];
    selectedAddressId: string = 'new'; // 'new' means enter manually
    
    // Location API Data
    provinces: LocationItem[] = [];
    districts: LocationItem[] = [];
    wards: LocationItem[] = [];

    constructor() {
        this.checkoutForm = this.fb.group({
            fullName: ['', Validators.required],
            phone: ['', [Validators.required, Validators.pattern(/^(0|\+84)[0-9]{9}$/)]],
            address: ['', Validators.required],
            ward: ['', Validators.required],
            district: ['', Validators.required],
            city: ['', Validators.required],
            paymentMethod: ['VNPAY', Validators.required],
            saveToAddressBook: [false]
        });
    }

    ngOnInit(): void {
        // Load cart data first
        this.loading = true;
        this.cartService.getCart().subscribe({
            next: (cart) => {
                this.cart = cart;
                this.loading = false;
                if (!cart || cart.items.length === 0) {
                    this.router.navigate(['/cart']);
                }
            },
            error: (err) => {
                this.loading = false;
                this.error = 'Failed to load cart';
                this.router.navigate(['/cart']);
            }
        });
        // Load addresses & locations
        this.userService.getAddresses().subscribe(addrs => {
            this.savedAddresses = addrs;
            const defaultAddr = addrs.find(a => a.isDefault);
            if (defaultAddr) {
                this.onSelectAddress(defaultAddr._id);
            }
        });
        this.locationService.getProvinces().subscribe(data => this.provinces = data);
    }

    onSelectAddress(id: string | undefined): void {
        this.selectedAddressId = id || 'new';
        if (id === 'new') {
            this.checkoutForm.reset({ paymentMethod: 'VNPAY', saveToAddressBook: false });
            this.districts = [];
            this.wards = [];
            return;
        }

        const addr = this.savedAddresses.find(a => a._id === id);
        if (addr) {
            // Find province safely to patch cascading ID|Name
            const p = this.provinces.find(prov => prov.full_name === addr.city || prov.name === addr.city);
            
            const patchData = {
                fullName: addr.fullName,
                phone: addr.phone,
                address: addr.street,
                city: p ? `${p.id}|${p.full_name}` : '',
                district: '',
                ward: '',
                saveToAddressBook: false
            };
            this.checkoutForm.patchValue(patchData);

            if (p) {
                this.locationService.getDistricts(p.id).subscribe(dData => {
                    this.districts = dData;
                    const d = this.districts.find(dist => dist.full_name === addr.district || dist.name === addr.district);
                    if (d) {
                        this.checkoutForm.patchValue({ district: `${d.id}|${d.full_name}` });
                        this.locationService.getWards(d.id).subscribe(wData => {
                            this.wards = wData;
                            const w = this.wards.find(ward => ward.full_name === addr.ward || ward.name === addr.ward);
                            if (w) {
                                this.checkoutForm.patchValue({ ward: `${w.id}|${w.full_name}` });
                            }
                        });
                    }
                });
            }
        }
    }

    onProvinceChange(event: Event): void {
        const select = event.target as HTMLSelectElement;
        const val = select.value;
        if (!val) {
            this.districts = [];
            this.wards = [];
            this.checkoutForm.patchValue({ district: '', ward: '' });
            return;
        }
        const provinceId = val.split('|')[0];
        this.locationService.getDistricts(provinceId).subscribe(data => {
            this.districts = data;
            this.wards = [];
            this.checkoutForm.patchValue({ district: '', ward: '' });
        });
    }

    onDistrictChange(event: Event): void {
        const select = event.target as HTMLSelectElement;
        const val = select.value;
        if (!val) {
            this.wards = [];
            this.checkoutForm.patchValue({ ward: '' });
            return;
        }
        const districtId = val.split('|')[0];
        this.locationService.getWards(districtId).subscribe(data => {
            this.wards = data;
            this.checkoutForm.patchValue({ ward: '' });
        });
    }

    onSubmit(): void {
        if (this.checkoutForm.invalid || !this.cart) {
            this.checkoutForm.markAllAsTouched();
            return;
        }

        this.loading = true;
        this.error = '';

        const rawVal = this.checkoutForm.value;
        
        // Block Momo
        if (rawVal.paymentMethod === 'MOMO') {
            this.toastService.warning('Ví Momo đang bảo trì. Vui lòng chọn VNPay.');
            this.loading = false;
            return;
        }

        // Clean city/district/ward strings from IDs
        const cityParts = (rawVal.city || '').split('|');
        const districtParts = (rawVal.district || '').split('|');
        const wardParts = (rawVal.ward || '').split('|');

        const shippingAddress = {
            fullName: rawVal.fullName,
            phone: rawVal.phone,
            address: rawVal.address,
            city: cityParts.length > 1 ? cityParts[1] : cityParts[0],
            district: districtParts.length > 1 ? districtParts[1] : districtParts[0],
            ward: wardParts.length > 1 ? wardParts[1] : wardParts[0],
        };

        this.orderService.createOrder({
            shippingAddress,
            paymentMethod: rawVal.paymentMethod,
            saveToAddressBook: rawVal.saveToAddressBook
        }).subscribe({
            next: (response: any) => {
                // 2. CHUYỂN HƯỚNG THANH TOÁN LUÔN (KHÔNG CÒN CASE COD)
                if (rawVal.paymentMethod === 'VNPAY' || rawVal.paymentMethod === 'CARD') {
                    this.createVnPayUrl(response.orderId, rawVal.paymentMethod);
                }
            },
            error: (err) => {
                this.loading = false;
                console.error('Full Checkout Error:', err);
                this.error = this.getCheckoutErrorMessage(err);
            },
        });
    }

    // Hàm createVnPayUrl KHÔNG truyền amount từ UI nữa
    createVnPayUrl(orderId: string, method: string): void {
        const body = {
            orderId: orderId,
            paymentMethod: method === 'CARD' ? 'CARD' : 'VNPAY',
            bankCode: ''
        };
        this.http.post<{ paymentUrl: string }>(`${environment.apiUrl}/payments/create-url`, body)
            .subscribe({
                next: (res) => window.location.href = res.paymentUrl,
                error: (err) => {
                    this.loading = false;
                    this.toastService.error('Lỗi kết nối cổng thanh toán');
                }
            });
    }
    getImageUrl(url: string): string {
        if (!url) return '';
        if (url.startsWith('http')) return url;
        const backendBase = environment.apiUrl.replace('/api/v1', '');
        return `${backendBase}/${url.startsWith('/') ? url.substring(1) : url}`;
    }

    onImageError(event: any): void {
        event.target.src = 'assets/placeholder-product.png';
    }

    getSubtotal(): number {
        if (!this.cart || !this.cart.items) return 0;
        return this.cart.items.reduce((sum, item) => sum + ((item.product?.Price || 0) * item.quantity), 0);
    }

    getTotal(): number {
        const subtotal = this.getSubtotal();
        const discountAmount = this.cart?.discountAmount || 0;
        return subtotal - discountAmount;
    }

    getCheckoutErrorMessage(err: any): string {
        const errorCode = err.error?.errorCode;
        const fallback = err.error?.message || err.message || 'Đặt hàng thất bại. Vui lòng thử lại.';
        switch (errorCode) {
            case 'INSUFFICIENT_STOCK':
                return `Sản phẩm "${err.error?.productName || ''}" đã hết hàng hoặc không đủ số lượng.`;
            case 'CART_EMPTY':
                return 'Giỏ hàng của bạn đang trống. Vui lòng thêm sản phẩm trước khi đặt hàng.';
            case 'INVALID_PAYMENT':
                return 'Phương thức thanh toán không hợp lệ. Vui lòng chọn lại.';
            case 'AUTH_EXPIRED':
                return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại để tiếp tục.';
            case 'SERVER_ERROR':
                return 'Lỗi máy chủ. Vui lòng thử lại sau ít phút.';
            default:
                return fallback;
        }
    }

    getFieldError(fieldName: string): string {
        const field = this.checkoutForm.get(fieldName);
        return field && field.invalid && field.touched ? 'This field is required' : '';
    }
}
