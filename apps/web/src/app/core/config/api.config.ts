import { InjectionToken } from '@angular/core';

export interface ApiConfig {
  baseUrl: string;
}

export const API_CONFIG = new InjectionToken<ApiConfig>('Configuración de la API');

export const apiConfig: ApiConfig = {
  baseUrl: '/api/v1',
};
