import { PartitionedStorage } from './partitioned-storage';

describe('PartitionedStorage', () => {
  let session: Storage;
  let storage: PartitionedStorage;

  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    session = sessionStorage;
    storage = new PartitionedStorage(session);
  });

  const tokenKeys = ['access_token', 'id_token', 'refresh_token', 'expires_at', 'id_token_claims_obj'];

  tokenKeys.forEach((key) => {
    it(`keeps ${key} in memory only: never in session or local storage`, () => {
      storage.setItem(key, 'secret-value');

      expect(storage.getItem(key)).toBe('secret-value');
      expect(session.getItem(key)).toBeNull();
      expect(localStorage.getItem(key)).toBeNull();
      expect(JSON.stringify({ ...sessionStorage })).not.toContain('secret-value');
      expect(JSON.stringify({ ...localStorage })).not.toContain('secret-value');
    });
  });

  it('puts only the one-time login values in session storage, so a redirect does not lose them', () => {
    storage.setItem('PKCE_verifier', 'verifier-1');
    storage.setItem('nonce', 'nonce-1');

    expect(session.getItem('PKCE_verifier')).toBe('verifier-1');
    expect(session.getItem('nonce')).toBe('nonce-1');
    expect(localStorage.length).toBe(0);
  });

  it('removes values from the place they live', () => {
    storage.setItem('access_token', 'a');
    storage.setItem('PKCE_verifier', 'v');

    storage.removeItem('access_token');
    storage.removeItem('PKCE_verifier');

    expect(storage.getItem('access_token')).toBeNull();
    expect(session.getItem('PKCE_verifier')).toBeNull();
  });

  it('returns null for something never stored', () => {
    expect(storage.getItem('nothing')).toBeNull();
  });

  it('does not share tokens between two storage instances (a new page load starts signed out)', () => {
    storage.setItem('access_token', 'a');

    expect(new PartitionedStorage(session).getItem('access_token')).toBeNull();
  });
});
