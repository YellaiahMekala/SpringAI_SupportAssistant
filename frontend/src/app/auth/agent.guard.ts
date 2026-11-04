import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const agentGuard: CanMatchFn = () => (inject(AuthService).isAgent() ? true : inject(Router).createUrlTree(['/chat']));
