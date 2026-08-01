import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';

interface ModulePageData {
  title: string;
  description: string;
  icon: string;
}

@Component({
  selector: 'app-module-placeholder',
  imports: [MatIconModule],
  templateUrl: './module-placeholder.html',
  styleUrl: './module-placeholder.scss',
})
export class ModulePlaceholder {
  private readonly activatedRoute = inject(ActivatedRoute);

  protected readonly page = computed<ModulePageData>(() => {
    const data = this.activatedRoute.snapshot.data;

    return {
      title: this.asString(data['title'], 'Modulo'),
      description: this.asString(
        data['description'],
        'Esta seccion esta pendiente de implementar.',
      ),
      icon: this.asString(data['icon'], 'apps'),
    };
  });

  private asString(value: unknown, fallback: string): string {
    return typeof value === 'string' && value.trim().length > 0 ? value : fallback;
  }
}
