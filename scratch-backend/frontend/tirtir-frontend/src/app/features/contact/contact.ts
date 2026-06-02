import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';

@Component({
    selector: 'app-contact',
    standalone: true,
    imports: [CommonModule, FormsModule, RouterModule],
    templateUrl: './contact.html',
    styleUrl: './contact.css'
})
export class ContactComponent implements OnInit {
    contactData = {
        firstName: '',
        lastName: '',
        email: '',
        phone: '',
        orderNumber: '',
        message: ''
    };

    submitted = false;

    constructor() { }

    ngOnInit(): void {
        window.scrollTo(0, 0);
    }

    onSubmit() {
        console.log('Contact form submitted:', this.contactData);
        this.submitted = true;
        // Reset form after 5 seconds
        setTimeout(() => {
            this.submitted = false;
            this.resetForm();
        }, 5000);
    }

    resetForm() {
        this.contactData = {
            firstName: '',
            lastName: '',
            email: '',
            phone: '',
            orderNumber: '',
            message: ''
        };
    }
}
