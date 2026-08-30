export type SyncOperationType = 'INVENTORY_COUNT_CREATE' | 'CART_UPSERT';
export type SyncResolution = 'SERVER_WINS' | 'CLIENT_WINS' | 'MERGED' | 'REJECTED';

export interface SyncOperationRequest {
  operationId: string;
  deviceId: string;
  deviceSequence: number;
  idempotencyKey: string;
  operationType: SyncOperationType;
  aggregateType: string;
  aggregateId: string | null;
  payload: Record<string, unknown>;
  clientCreatedAt: string;
}

export interface SyncOperation extends SyncOperationRequest {
  requestHash: string;
  receivedAt: string;
  processedAt: string | null;
  status: string;
  result: Record<string, unknown> | null;
  errorCode: string | null;
  errorMessage: string | null;
}

export interface DeviceCheckpoint {
  deviceId: string;
  lastReceivedSequence: number;
  lastProcessedSequence: number;
  lastAcknowledgedOutboxSequence: number;
  lastSyncAt: string | null;
  updatedAt: string;
}

export interface OutboxEvent {
  outboxEventId: string;
  globalSequence: number;
  branchId: string | null;
  warehouseId: string | null;
  aggregateType: string;
  aggregateId: string;
  eventType: string;
  payload: Record<string, unknown>;
  correlationId: string | null;
  createdAt: string;
}

export interface OutboxBatch {
  deviceId: string;
  afterSequence: number;
  nextSequence: number;
  events: OutboxEvent[];
}

export interface SyncConflict {
  conflictId: string;
  operationId: string;
  deviceId: string;
  deviceSequence: number;
  operationType: SyncOperationType;
  conflictType: string;
  serverState: Record<string, unknown> | null;
  clientState: Record<string, unknown> | null;
  resolution: SyncResolution | null;
  resolutionNotes: string | null;
  resolvedBy: string | null;
  createdAt: string;
  resolvedAt: string | null;
}

export interface ResolveConflictRequest {
  resolution: SyncResolution;
  resolutionNotes: string | null;
  mergedPayload: Record<string, unknown> | null;
}

export function allowedConflictResolutions(operationType: SyncOperationType): SyncResolution[] {
  return operationType === 'CART_UPSERT'
    ? ['SERVER_WINS', 'CLIENT_WINS', 'MERGED', 'REJECTED']
    : ['SERVER_WINS', 'REJECTED'];
}
