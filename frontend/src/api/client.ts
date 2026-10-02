import { mockClient } from '../mocks/mockClient';
import { ApiError } from './apiError';
import type {
  ActionRequest,
  ActionResultDTO,
  CreateExpeditionRequest,
  EffectInfoDTO,
  EnemyInfoDTO,
  ErrorCode,
  ExpeditionDTO,
  HealthDTO,
  HeroClassDTO,
  ItemDTO,
  PreviewDTO,
  PreviewRequest,
  RewardRequest,
} from './types';

export { ApiError, isApiError } from './apiError';
export type { ClientErrorCode } from './apiError';

/** One method per endpoint of api-contract §2. Implemented by the HTTP client and the mock. */
export interface ApiClient {
  health(): Promise<HealthDTO>;
  getHeroClasses(): Promise<HeroClassDTO[]>;
  getEquipment(): Promise<ItemDTO[]>;
  getEffects(): Promise<EffectInfoDTO[]>;
  getEnemies(): Promise<EnemyInfoDTO[]>;
  preview(request: PreviewRequest): Promise<PreviewDTO>;
  createExpedition(request: CreateExpeditionRequest): Promise<ExpeditionDTO>;
  getExpedition(expeditionId: string): Promise<ExpeditionDTO>;
  act(expeditionId: string, request: ActionRequest): Promise<ActionResultDTO>;
  chooseReward(expeditionId: string, request: RewardRequest): Promise<ExpeditionDTO>;
  deleteExpedition(expeditionId: string): Promise<void>;
}

/** In development Vite proxies `/api` to `http://localhost:8080`. */
const BASE_URL = '/api';

const ERROR_CODES: ReadonlySet<string> = new Set<ErrorCode>([
  'INVALID_JSON',
  'REQUIRED_FIELD',
  'INVALID_VALUE',
  'NOT_FOUND',
  'METHOD_NOT_ALLOWED',
  'INVALID_STATE',
  'ABILITY_ON_COOLDOWN',
  'ACTION_NOT_ALLOWED',
  'INTERNAL_ERROR',
]);

function isErrorCode(value: unknown): value is ErrorCode {
  return typeof value === 'string' && ERROR_CODES.has(value);
}

/** Converts a non-2xx response into an `ApiError` using the contract §1 error body when present. */
async function toApiError(response: Response): Promise<ApiError> {
  try {
    const body: unknown = await response.json();
    if (typeof body === 'object' && body !== null && 'error' in body) {
      const { error } = body as { error: unknown };
      if (typeof error === 'object' && error !== null) {
        const { code, message } = error as { code?: unknown; message?: unknown };
        if (isErrorCode(code)) {
          return new ApiError(code, typeof message === 'string' ? message : code, response.status);
        }
      }
    }
  } catch {
    // Body was not JSON: fall through to a generic error.
  }
  return new ApiError(
    'INVALID_RESPONSE',
    `Respuesta inesperada del servidor (HTTP ${response.status})`,
    response.status,
  );
}

async function request<T>(method: 'GET' | 'POST' | 'DELETE', path: string, body?: unknown): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method,
      headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError('NETWORK_ERROR', 'No se pudo conectar con el servidor', 0);
  }

  if (!response.ok) {
    throw await toApiError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  try {
    return (await response.json()) as T;
  } catch {
    throw new ApiError('INVALID_RESPONSE', 'Respuesta inesperada del servidor', response.status);
  }
}

const expeditionPath = (expeditionId: string): string =>
  `/expeditions/${encodeURIComponent(expeditionId)}`;

/** Real client against the Java backend. */
export const httpClient: ApiClient = {
  health: () => request<HealthDTO>('GET', '/health'),
  getHeroClasses: () => request<HeroClassDTO[]>('GET', '/catalog/classes'),
  getEquipment: () => request<ItemDTO[]>('GET', '/catalog/equipment'),
  getEffects: () => request<EffectInfoDTO[]>('GET', '/catalog/effects'),
  getEnemies: () => request<EnemyInfoDTO[]>('GET', '/catalog/enemies'),
  preview: (body) => request<PreviewDTO>('POST', '/preview', body),
  createExpedition: (body) => request<ExpeditionDTO>('POST', '/expeditions', body),
  getExpedition: (expeditionId) => request<ExpeditionDTO>('GET', expeditionPath(expeditionId)),
  act: (expeditionId, body) =>
    request<ActionResultDTO>('POST', `${expeditionPath(expeditionId)}/actions`, body),
  chooseReward: (expeditionId, body) =>
    request<ExpeditionDTO>('POST', `${expeditionPath(expeditionId)}/reward`, body),
  deleteExpedition: (expeditionId) => request<void>('DELETE', expeditionPath(expeditionId)),
};

/** Active client: the scripted mock when `VITE_USE_MOCKS=true`, the HTTP client otherwise. */
export const apiClient: ApiClient = import.meta.env.VITE_USE_MOCKS === 'true' ? mockClient : httpClient;
