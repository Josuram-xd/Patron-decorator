/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** `'true'` makes `apiClient` use the scripted mock in `src/mocks/` instead of the backend. */
  readonly VITE_USE_MOCKS?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
