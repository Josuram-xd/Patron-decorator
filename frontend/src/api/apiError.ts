import type { ErrorCode } from './types';

/**
 * Client-side failures that never come from the backend contract:
 * - `NETWORK_ERROR`: the request did not reach the server (status `0`).
 * - `INVALID_RESPONSE`: the server answered with something that is not the expected JSON.
 */
export type ClientErrorCode = 'NETWORK_ERROR' | 'INVALID_RESPONSE';

/**
 * Error thrown by every `ApiClient` call.
 * `message` is player-visible (Spanish) when it comes from the backend.
 *
 * Lives in its own module so the real client and the mock client can both
 * import it without a runtime import cycle.
 */
export class ApiError extends Error {
  readonly code: ErrorCode | ClientErrorCode;
  readonly status: number;

  constructor(code: ErrorCode | ClientErrorCode, message: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}
