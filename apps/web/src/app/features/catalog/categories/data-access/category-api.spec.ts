import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';

import { API_CONFIG } from '../../../../core/config/api.config';
import { CategoryApi } from './category-api.service';

describe('CategoryApi', () => {
  let service: CategoryApi;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        {
          provide: API_CONFIG,
          useValue: { baseUrl: '/api/v1' },
        },
      ],
    });
    service = TestBed.inject(CategoryApi);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
