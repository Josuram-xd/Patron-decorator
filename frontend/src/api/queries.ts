import {
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
} from '@tanstack/react-query';
import { apiClient } from './client';
import { ApiError } from './apiError';
import type {
  ActionRequest,
  CreateExpeditionRequest,
  ExpeditionDTO,
  PreviewRequest,
} from './types';

/** Query keys, centralised so invalidation stays consistent. */
export const queryKeys = {
  all: ['rpg'] as const,
  catalog: () => [...queryKeys.all, 'catalog'] as const,
  heroClasses: () => [...queryKeys.catalog(), 'classes'] as const,
  equipment: () => [...queryKeys.catalog(), 'equipment'] as const,
  effects: () => [...queryKeys.catalog(), 'effects'] as const,
  enemies: () => [...queryKeys.catalog(), 'enemies'] as const,
  expeditions: () => [...queryKeys.all, 'expeditions'] as const,
  expedition: (expeditionId: string) => [...queryKeys.expeditions(), expeditionId] as const,
};

/** Client errors (4xx) are deterministic: retrying them is pointless. */
function shouldRetry(failureCount: number, error: Error): boolean {
  if (error instanceof ApiError && error.status >= 400 && error.status < 500) {
    return false;
  }
  return failureCount < 2;
}

// ---------------------------------------------------------------------------
// Catalog — static data, cached for the whole session
// ---------------------------------------------------------------------------

const catalogOptions = { staleTime: Infinity, gcTime: Infinity, retry: shouldRetry } as const;

export function useHeroClasses() {
  return useQuery({ queryKey: queryKeys.heroClasses(), queryFn: () => apiClient.getHeroClasses(), ...catalogOptions });
}

export function useEquipment() {
  return useQuery({ queryKey: queryKeys.equipment(), queryFn: () => apiClient.getEquipment(), ...catalogOptions });
}

export function useEffects() {
  return useQuery({ queryKey: queryKeys.effects(), queryFn: () => apiClient.getEffects(), ...catalogOptions });
}

export function useEnemies() {
  return useQuery({ queryKey: queryKeys.enemies(), queryFn: () => apiClient.getEnemies(), ...catalogOptions });
}

// ---------------------------------------------------------------------------
// Expedition
// ---------------------------------------------------------------------------

/**
 * Server state of an expedition. Disabled while `expeditionId` is `null`.
 * The server is the source of truth: the cache is only written with server responses.
 */
export function useExpedition(expeditionId: string | null) {
  return useQuery({
    queryKey: queryKeys.expedition(expeditionId ?? ''),
    queryFn: () => apiClient.getExpedition(expeditionId as string),
    enabled: expeditionId !== null,
    staleTime: Infinity,
    retry: shouldRetry,
  });
}

/** Writes a server-returned expedition into the cache. */
export function setExpeditionData(queryClient: QueryClient, expedition: ExpeditionDTO): void {
  queryClient.setQueryData(queryKeys.expedition(expedition.id), expedition);
}

export function useCreateExpedition() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateExpeditionRequest) => apiClient.createExpedition(request),
    onSuccess: (expedition) => setExpeditionData(queryClient, expedition),
  });
}

export interface ActVariables {
  expeditionId: string;
  action: ActionRequest;
}

/**
 * Plays a round. Deliberately does NOT write the cache: per architecture §3.3 the
 * `events` are animated first and the final `expedition` is painted when the queue
 * drains. The event player must call `setExpeditionData` at that moment.
 */
export function useAct() {
  return useMutation({
    mutationFn: ({ expeditionId, action }: ActVariables) => apiClient.act(expeditionId, action),
  });
}

export interface ChooseRewardVariables {
  expeditionId: string;
  /** `null` skips the reward. */
  itemId: string | null;
}

export function useChooseReward() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ expeditionId, itemId }: ChooseRewardVariables) =>
      apiClient.chooseReward(expeditionId, { itemId }),
    onSuccess: (expedition) => setExpeditionData(queryClient, expedition),
  });
}

/** Stats preview (loadout and reward screens). Debouncing is the caller's job. */
export function usePreview() {
  return useMutation({
    mutationFn: (request: PreviewRequest) => apiClient.preview(request),
  });
}

export function useDeleteExpedition() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (expeditionId: string) => apiClient.deleteExpedition(expeditionId),
    onSuccess: (_data, expeditionId) => {
      queryClient.removeQueries({ queryKey: queryKeys.expedition(expeditionId) });
    },
  });
}
