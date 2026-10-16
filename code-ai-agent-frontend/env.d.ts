/// <reference types="vite/client" />

interface Window {
  __APP_RUNTIME_CONFIG__?: {
    deployBaseUrl?: string
    apiBaseUrl?: string
  }
}
