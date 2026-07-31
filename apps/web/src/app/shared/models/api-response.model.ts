export interface ApiResponse<T> {
  timestamp: string;
  status: number;
  code: string;
  reason: string;
  message: string;
  data: T | null;
  errors: unknown | null;
  path: string;
  correlationId: string;
}
