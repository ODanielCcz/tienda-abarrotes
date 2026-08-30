import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface PeriodSelection {
  from: string;
  to: string;
}

@Component({
  selector: 'app-period-filter',
  imports: [MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './period-filter.html',
  styleUrl: './period-filter.scss',
})
export class PeriodFilter {
  readonly from = input.required<string>();
  readonly to = input.required<string>();
  readonly applied = output<PeriodSelection>();

  protected apply(from: string, to: string): void {
    if (!from || !to || from > to) return;
    this.applied.emit({ from, to });
  }
}
