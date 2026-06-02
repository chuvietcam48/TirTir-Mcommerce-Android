import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { UserService } from '../../../core/services/user.service';

@Component({
  selector: 'app-user-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './user-form.component.html',
  styleUrls: ['./user-form.component.css']
})
export class UserFormComponent {
  userForm: FormGroup;
  loading = false;
  error: string | null = null;
  roles = [
    { value: 'admin', label: 'Admin (Full Access)' },
    { value: 'inventory_staff', label: 'Inventory Staff (Inventory Only)' },
    { value: 'customer_service', label: 'Customer Service (Orders Only)' }
  ];

  constructor(
    private fb: FormBuilder,
    private userService: UserService,
    private router: Router
  ) {
    this.userForm = this.fb.group({
      name: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      role: ['inventory_staff', Validators.required]
    });
  }

  onSubmit(): void {
    if (this.userForm.invalid) return;

    this.loading = true;
    this.userService.createAdminUser(this.userForm.value).subscribe({
      next: () => {
        this.router.navigate(['/users']);
      },
      error: (err) => {
        this.error = err.error?.message || 'Failed to create user';
        this.loading = false;
      }
    });
  }
}
