import { OAuthStorage } from 'angular-oauth2-oidc';

/** OAuth storage: tokens in memory; PKCE/nonce in sessionStorage for the redirect. */
export class PartitionedStorage implements OAuthStorage {
  private static readonly TRANSIENT_KEYS = new Set(['nonce', 'PKCE_verifier', 'session_state', 'requested_route']);

  private readonly memory = new Map<string, string>();

  constructor(private readonly session: Storage = sessionStorage) {}

  getItem(key: string): string | null {
    return this.isTransient(key) ? this.session.getItem(key) : (this.memory.get(key) ?? null);
  }

  setItem(key: string, data: string): void {
    if (this.isTransient(key)) {
      this.session.setItem(key, data);
    } else {
      this.memory.set(key, data);
    }
  }

  removeItem(key: string): void {
    if (this.isTransient(key)) {
      this.session.removeItem(key);
    } else {
      this.memory.delete(key);
    }
  }

  private isTransient(key: string): boolean {
    return PartitionedStorage.TRANSIENT_KEYS.has(key);
  }
}
