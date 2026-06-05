import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { UserService } from '../../../core/services/user.service';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  templateUrl: './user-list.component.html',
  styleUrls: ['./user-list.component.css']
})
export class UserListComponent implements OnInit {
  users: any[] = [];
  loading = false;
  error: string | null = null;

  constructor(private userService: UserService) { }

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.loading = true;
    this.userService.getAdminUsers().subscribe({
      next: (data: any) => {
        // getAdminUsers returns a plain array of staff/admin users
        this.users = Array.isArray(data) ? data : (data.users || data.data || []);
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load staff users';
        this.loading = false;
      }
    });
  }

  deleteUser(id: string): void {
    if (confirm('Are you sure you want to delete this user?')) {
      this.userService.deleteUser(id).subscribe({
        next: () => {
          this.loadUsers();
        },
        error: (err) => {
          alert('Failed to delete user: ' + (err.error?.message || err.message));
        }
      });
    }
  }
}
