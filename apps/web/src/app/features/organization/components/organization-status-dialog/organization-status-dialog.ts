import { Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';

export interface OrganizationStatusDialogData {
  title: string;
  currentStatus: string;
  options: string[];
}

@Component({
  selector: 'app-organization-status-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      <mat-form-field appearance="outline">
        <mat-label>Estado</mat-label>
        <mat-select [formControl]="status">
          @for (option of data.options; track option) {
            <mat-option [value]="option">{{ option }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="null">Cancelar</button>
      <button mat-flat-button type="button" (click)="save()">Aplicar</button>
    </mat-dialog-actions>
  `,
  styles: `
    mat-form-field {
      width: min(24rem, 75vw);
    }
  `,
})
export class OrganizationStatusDialog {
  protected readonly data = inject<OrganizationStatusDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<OrganizationStatusDialog, string | null>);
  protected readonly status = new FormControl(this.data.currentStatus, { nonNullable: true });

  protected save(): void {
    this.ref.close(this.status.value);
  }
}
