import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { CurrentUser, Role } from './current-user';
import { CurrentUserService } from './current-user.service';

/** Autorise la route si l'utilisateur possède au moins un des rôles ; sinon renvoie vers l'accueil. */
export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => {
    const service = inject(CurrentUserService);
    const router = inject(Router);
    const verifier = (u: CurrentUser) => roles.some((r) => u.roles.includes(r)) || router.createUrlTree(['/accueil']);

    const utilisateur = service.currentUser();
    return utilisateur ? verifier(utilisateur) : service.load().pipe(map(verifier));
  };
}
