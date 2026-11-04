import { Injectable, computed, inject, signal } from '@angular/core';
import { AuthConfig, OAuthService } from 'angular-oauth2-oidc';
import { APP_CONFIG } from '../config';
import { Me } from '../api/api.models';
import { PartitionedStorage } from './partitioned-storage';

/** Keycloak authorization-code + PKCE; tokens via PartitionedStorage. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly oauth = inject(OAuthService);

  /** Who the API says the caller is. Set after sign-in. */
  readonly me = signal<Me | null>(null);
  readonly isAgent = computed(() => this.me()?.roles.includes('ROLE_SUPPORT_AGENT') ?? false);
  readonly displayName = computed(() => this.me()?.displayName ?? this.me()?.subject ?? '');

  /**
   * Called once before the app starts. If there is no valid session this redirects to Keycloak, and the
   * promise never settles in the meantime; on return it completes the code exchange.
   */
  async init(): Promise<void> {
    const config: AuthConfig = {
      issuer: APP_CONFIG.issuer,
      clientId: APP_CONFIG.clientId,
      redirectUri: window.location.origin + '/',
      postLogoutRedirectUri: window.location.origin + '/',
      responseType: 'code',
      scope: APP_CONFIG.scope,
      // The dev realm runs over plain http on localhost. A deployed realm must use https.
      requireHttps: false,
      showDebugInformation: false,
      clearHashAfterLogin: true,
    };
    this.oauth.configure(config);
    this.oauth.setStorage(new PartitionedStorage());

    const signedIn = await this.oauth.loadDiscoveryDocumentAndLogin();
    if (signedIn) {
      this.oauth.setupAutomaticSilentRefresh();
    }
  }

  /** The current access token, or null when there is no valid one. */
  accessToken(): string | null {
    return this.oauth.hasValidAccessToken() ? this.oauth.getAccessToken() : null;
  }

  setMe(me: Me | null): void {
    this.me.set(me);
  }

  /** Starts a fresh sign-in (used when a session has ended). */
  signIn(): void {
    this.oauth.initCodeFlow();
  }

  /** Ends the session at Keycloak as well as in this tab. */
  signOut(): void {
    this.oauth.logOut();
  }
}
