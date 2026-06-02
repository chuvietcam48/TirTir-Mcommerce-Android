import { Component, OnInit, inject, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { UserService } from '../../../core/services/user.service';
import { Address } from '../../../core/models';
import { LocationService, LocationItem } from '../../../core/services/location.service';

@Component({
    selector: 'app-address-book',
    standalone: true,
    imports: [CommonModule, ReactiveFormsModule],
    templateUrl: './address-book.html',
    styleUrl: './address-book.css'
})
export class AddressBookComponent implements OnInit {
    private userService = inject(UserService);
    private fb = inject(FormBuilder);
    private locationService = inject(LocationService);
    private http = inject(HttpClient);

    addresses: Address[] = [];
    loading = false;
    successMessage = '';
    errorMessage = '';

    showForm = false;
    editMode = false;
    editingAddressId: string | null = null;
    addressForm!: FormGroup;

    provinces: LocationItem[] = [];
    districts: LocationItem[] = [];
    wards: LocationItem[] = [];

    // Names for autocomplete context
    selectedProvinceName = '';
    selectedDistrictName = '';
    selectedWardName = '';

    // Street autocomplete
    streetSuggestions: string[] = [];
    showSuggestions = false;
    private streetTimer: any = null;

    ngOnInit(): void {
        this.initForm();
        this.loadAddresses();
        this.loadProvinces();
    }

    loadProvinces(): void {
        this.locationService.getProvinces().subscribe(data => {
            this.provinces = data;
        });
    }

    initForm(): void {
        this.addressForm = this.fb.group({
            fullName: ['', [Validators.required, Validators.minLength(2)]],
            phone: ['', [Validators.required, Validators.pattern(/^[0-9]{10,11}$/)]],
            city: ['', [Validators.required]],
            district: [{ value: '', disabled: true }, [Validators.required]],
            ward: [{ value: '', disabled: true }, [Validators.required]],
            street: [{ value: '', disabled: true }, [Validators.required]],
            isDefault: [false]
        });
    }

    // ── Cascade handlers ────────────────────────────────────────────────

    onProvinceChange(event: Event): void {
        const val = (event.target as HTMLSelectElement).value;

        // Reset downstream
        this.districts = [];
        this.wards = [];
        this.selectedDistrictName = '';
        this.selectedWardName = '';
        this.streetSuggestions = [];
        this.showSuggestions = false;

        this.addressForm.patchValue({ district: '', ward: '', street: '' });
        this.addressForm.get('district')?.disable();
        this.addressForm.get('ward')?.disable();
        this.addressForm.get('street')?.disable();

        if (!val) {
            this.selectedProvinceName = '';
            return;
        }

        const [id, name] = val.split('|');
        this.selectedProvinceName = name;

        this.locationService.getDistricts(id).subscribe(data => {
            this.districts = data;
            this.addressForm.get('district')?.enable();
        });
    }

    onDistrictChange(event: Event): void {
        const val = (event.target as HTMLSelectElement).value;

        this.wards = [];
        this.selectedWardName = '';
        this.streetSuggestions = [];
        this.showSuggestions = false;

        this.addressForm.patchValue({ ward: '', street: '' });
        this.addressForm.get('ward')?.disable();
        this.addressForm.get('street')?.disable();

        if (!val) {
            this.selectedDistrictName = '';
            return;
        }

        const [id, name] = val.split('|');
        this.selectedDistrictName = name;

        this.locationService.getWards(id).subscribe(data => {
            this.wards = data;
            this.addressForm.get('ward')?.enable();
        });
    }

    onWardChange(event: Event): void {
        const val = (event.target as HTMLSelectElement).value;

        this.streetSuggestions = [];
        this.showSuggestions = false;
        this.addressForm.patchValue({ street: '' });

        if (!val) {
            this.selectedWardName = '';
            this.addressForm.get('street')?.disable();
            return;
        }

        const [, name] = val.split('|');
        this.selectedWardName = name;
        this.addressForm.get('street')?.enable();
    }

    // ── Street Autocomplete ──────────────────────────────────────────────

    onStreetInput(event: Event): void {
        const input = (event.target as HTMLInputElement).value.trim();
        clearTimeout(this.streetTimer);

        if (input.length < 3) {
            this.streetSuggestions = [];
            this.showSuggestions = false;
            return;
        }

        this.streetTimer = setTimeout(() => this.fetchStreetSuggestions(input), 400);
    }

    private fetchStreetSuggestions(input: string): void {
        const context = [this.selectedWardName, this.selectedDistrictName, this.selectedProvinceName]
            .filter(Boolean).join(', ');
        const q = context ? `${input}, ${context}` : input;

        this.http.get<any[]>('https://nominatim.openstreetmap.org/search', {
            params: {
                format: 'json',
                q,
                countrycodes: 'vn',
                addressdetails: '1',
                limit: '8',
                'accept-language': 'vi'
            },
            headers: { 'User-Agent': 'TirTirShop/1.0 (contact@tirtir.vn)' }
        }).subscribe({
            next: (results) => {
                const seen = new Set<string>();
                results.forEach(r => {
                    const addr = r.address || {};
                    if (addr.road) {
                        const suggestion = [addr.house_number, addr.road].filter(Boolean).join(' ');
                        seen.add(suggestion);
                    }
                });
                this.streetSuggestions = Array.from(seen).slice(0, 6);
                this.showSuggestions = this.streetSuggestions.length > 0;
            },
            error: () => {
                this.streetSuggestions = [];
                this.showSuggestions = false;
            }
        });
    }

    selectSuggestion(suggestion: string): void {
        this.addressForm.patchValue({ street: suggestion });
        this.streetSuggestions = [];
        this.showSuggestions = false;
    }

    @HostListener('document:click')
    closeSuggestions(): void {
        this.showSuggestions = false;
    }

    // ── CRUD ─────────────────────────────────────────────────────────────

    loadAddresses(): void {
        this.loading = true;
        this.userService.getAddresses().subscribe({
            next: (addresses) => {
                this.addresses = addresses;
                this.loading = false;
            },
            error: (error) => {
                this.errorMessage = error.message || 'Failed to load addresses';
                this.loading = false;
            }
        });
    }

    openAddForm(): void {
        this.showForm = true;
        this.editMode = false;
        this.editingAddressId = null;
        this.resetCascade();
        this.addressForm.reset({ isDefault: false });
        this.addressForm.get('district')?.disable();
        this.addressForm.get('ward')?.disable();
        this.addressForm.get('street')?.disable();
    }

    openEditForm(address: Address): void {
        this.showForm = true;
        this.editMode = true;
        this.editingAddressId = address._id || null;

        this.resetCascade();

        const p = this.provinces.find(pv => pv.full_name === address.city || pv.name === address.city);

        this.addressForm.patchValue({
            fullName: address.fullName,
            phone: address.phone,
            isDefault: address.isDefault,
            city: p ? `${p.id}|${p.full_name}` : '',
            district: '',
            ward: '',
            street: address.street
        });

        if (!p) return;
        this.selectedProvinceName = p.full_name;

        this.locationService.getDistricts(p.id).subscribe(dData => {
            this.districts = dData;
            this.addressForm.get('district')?.enable();

            const d = dData.find(di => di.full_name === address.district || di.name === address.district);
            if (!d) return;
            this.selectedDistrictName = d.full_name;
            this.addressForm.patchValue({ district: `${d.id}|${d.full_name}` });

            this.locationService.getWards(d.id).subscribe(wData => {
                this.wards = wData;
                this.addressForm.get('ward')?.enable();

                const w = wData.find(wi => wi.full_name === address.ward || wi.name === address.ward);
                if (!w) return;
                this.selectedWardName = w.full_name;
                this.addressForm.patchValue({ ward: `${w.id}|${w.full_name}` });
                this.addressForm.get('street')?.enable();
            });
        });
    }

    closeForm(): void {
        this.showForm = false;
        this.editMode = false;
        this.editingAddressId = null;
        this.resetCascade();
        this.addressForm.reset();
    }

    private resetCascade(): void {
        this.districts = [];
        this.wards = [];
        this.selectedProvinceName = '';
        this.selectedDistrictName = '';
        this.selectedWardName = '';
        this.streetSuggestions = [];
        this.showSuggestions = false;
        clearTimeout(this.streetTimer);
    }

    onSubmit(): void {
        if (this.addressForm.invalid) {
            this.addressForm.markAllAsTouched();
            return;
        }

        this.loading = true;
        this.successMessage = '';
        this.errorMessage = '';

        const raw = this.addressForm.getRawValue();
        const cityParts = (raw.city || '').split('|');
        const districtParts = (raw.district || '').split('|');
        const wardParts = (raw.ward || '').split('|');

        const formData = {
            ...raw,
            city: cityParts.length > 1 ? cityParts[1] : cityParts[0],
            district: districtParts.length > 1 ? districtParts[1] : districtParts[0],
            ward: wardParts.length > 1 ? wardParts[1] : wardParts[0]
        };

        const req$ = this.editMode && this.editingAddressId
            ? this.userService.updateAddress(this.editingAddressId, formData)
            : this.userService.addAddress(formData);

        req$.subscribe({
            next: (addresses) => {
                this.addresses = addresses;
                this.successMessage = this.editMode ? 'Address updated!' : 'Address added!';
                this.loading = false;
                this.closeForm();
            },
            error: (error) => {
                this.errorMessage = error.message || 'Failed to save address';
                this.loading = false;
            }
        });
    }

    setAsDefault(addressId: string | undefined): void {
        if (!addressId) return;
        this.loading = true;
        this.userService.setDefaultAddress(addressId).subscribe({
            next: (addresses) => {
                this.addresses = addresses;
                this.successMessage = 'Default address updated!';
                this.loading = false;
            },
            error: (error) => {
                this.errorMessage = error.message || 'Failed to set default address';
                this.loading = false;
            }
        });
    }

    deleteAddress(addressId: string | undefined): void {
        if (!addressId) return;
        if (!confirm('Delete this address?')) return;

        this.loading = true;
        this.userService.deleteAddress(addressId).subscribe({
            next: (addresses) => {
                this.addresses = addresses;
                this.successMessage = 'Address deleted.';
                this.loading = false;
            },
            error: (error) => {
                this.errorMessage = error.message || 'Failed to delete address';
                this.loading = false;
            }
        });
    }
}
