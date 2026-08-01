import { DOCUMENT } from '@angular/common';
import { Injectable, effect, inject, signal } from '@angular/core';

export type ThemeMode = 'light' | 'dark';

const THEME_STORAGE_KEY = 'tienda.theme';

@Injectable({
  providedIn: 'root',
})
export class ThemeStore {
  private readonly document = inject(DOCUMENT);
  private readonly themeState = signal<ThemeMode>(this.restoreTheme());

  readonly theme = this.themeState.asReadonly();

  constructor() {
    effect(() => {
      const theme = this.themeState();
      const root = this.document.documentElement;

      root.classList.remove('theme-light', 'theme-dark');
      root.classList.add(`theme-${theme}`);

      localStorage.setItem(THEME_STORAGE_KEY, theme);
    });
  }

  toggle(): void {
    this.themeState.update((theme) => (theme === 'dark' ? 'light' : 'dark'));
  }

  isDark(): boolean {
    return this.themeState() === 'dark';
  }

  private restoreTheme(): ThemeMode {
    const storeTheme = localStorage.getItem(THEME_STORAGE_KEY);

    if (storeTheme === 'light' || storeTheme === 'dark') {
      return storeTheme;
    }

    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
}
