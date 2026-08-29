import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { vi } from 'vitest';

import { ConfirmationDialog, ConfirmationDialogData } from './confirmation-dialog';

describe('ConfirmationDialog', () => {
  let fixture: ComponentFixture<ConfirmationDialog>;
  const dialogRef = { close: vi.fn() };
  const data: ConfirmationDialogData = {
    title: 'Desactivar marca',
    message: 'La marca dejará de estar disponible para operaciones nuevas.',
    confirmLabel: 'Desactivar',
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [ConfirmationDialog],
      providers: [
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ConfirmationDialog);
    fixture.detectChanges();
  });

  it('returns true when the user confirms', () => {
    const button = fixture.nativeElement.querySelector('[data-testid="confirm"]') as HTMLElement;
    button.click();
    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });

  it('returns false when the user cancels', () => {
    const button = fixture.nativeElement.querySelector('[data-testid="cancel"]') as HTMLElement;
    button.click();
    expect(dialogRef.close).toHaveBeenCalledWith(false);
  });
});
