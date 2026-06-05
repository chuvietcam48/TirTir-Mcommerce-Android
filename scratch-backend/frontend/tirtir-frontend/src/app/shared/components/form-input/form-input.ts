import { Component, Input, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';

/**
 * Reusable form input component with TIRTIR premium styling
 * Supports text, email, password, tel types
 */
@Component({
    selector: 'app-form-input',
    standalone: true,
    imports: [CommonModule, ReactiveFormsModule],
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: forwardRef(() => FormInputComponent),
            multi: true,
        },
    ],
    template: `
    <div class="form-group">
      <label *ngIf="label" [for]="id" class="form-label">
        {{ label }}
        <span *ngIf="required" class="required">*</span>
      </label>
      <input
        [id]="id"
        [type]="type"
        [placeholder]="placeholder"
        [disabled]="disabled"
        [value]="value"
        (input)="onInput($event)"
        (blur)="onTouched()"
        class="form-input"
        [class.error]="error"
      />
      <span *ngIf="error" class="error-message">{{ error }}</span>
    </div>
  `,
    styles: [`
    .form-group {
      margin-bottom: 1.5rem;
      width: 100%;
    }

    .form-label {
      display: block;
      font-size: 0.875rem;
      font-weight: 500;
      color: #1a1a1a;
      margin-bottom: 0.5rem;
      letter-spacing: 0.3px;
    }

    .required {
      color: #d32f2f;
      margin-left: 2px;
    }

    .form-input {
      width: 100%;
      padding: 0.875rem 1rem;
      font-size: 0.938rem;
      line-height: 1.5;
      color: #1a1a1a;
      background-color: #ffffff;
      border: 1.5px solid #e0e0e0;
      border-radius: 4px;
      transition: all 0.2s ease;
      font-family: inherit;
    }

    .form-input:focus {
      outline: none;
      border-color: #1a1a1a;
      box-shadow: 0 0 0 3px rgba(26, 26, 26, 0.05);
    }

    .form-input:disabled {
      background-color: #f5f5f5;
      cursor: not-allowed;
      opacity: 0.6;
    }

    .form-input.error {
      border-color: #d32f2f;
    }

    .form-input.error:focus {
      box-shadow: 0 0 0 3px rgba(211, 47, 47, 0.1);
    }

    .error-message {
      display: block;
      margin-top: 0.5rem;
      font-size: 0.813rem;
      color: #d32f2f;
    }

    ::placeholder {
      color: #9e9e9e;
    }
  `],
})
export class FormInputComponent implements ControlValueAccessor {
    @Input() label = '';
    @Input() type: 'text' | 'email' | 'password' | 'tel' = 'text';
    @Input() placeholder = '';
    @Input() required = false;
    @Input() error = '';
    @Input() id = '';

    value = '';
    disabled = false;

    onChange: any = () => { };
    onTouched: any = () => { };

    writeValue(value: any): void {
        this.value = value || '';
    }

    registerOnChange(fn: any): void {
        this.onChange = fn;
    }

    registerOnTouched(fn: any): void {
        this.onTouched = fn;
    }

    setDisabledState(isDisabled: boolean): void {
        this.disabled = isDisabled;
    }

    onInput(event: Event): void {
        const input = event.target as HTMLInputElement;
        this.value = input.value;
        this.onChange(this.value);
    }
}
