import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideOAuthClient } from 'angular-oauth2-oidc';

import { routes } from './app.routes';
import { ApiService } from './api/api.service';
import { AuthService } from './auth/auth.service';
import { authInterceptor } from './auth/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideOAuthClient(),
    // Before the first screen: sign in (redirecting to Keycloak if there is no session), then ask the API who
    // this is. A person without a recognised role gets an explanation instead of screens that would all fail.
    provideAppInitializer(async () => {
      const auth = inject(AuthService);
      const api = inject(ApiService);
      await auth.init();
      try {
        auth.setMe(await api.me());
      } catch {
        auth.setMe(null);
      }
    }),
  ],
};
