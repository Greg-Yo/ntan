import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { CurrentUser, Role } from './current-user';
import { CurrentUserService } from './current-user.service';

/** Autorise la route si l'utilisateur possède au moins un des rôles ; sinon (ou si le profil est introuvable) renvoie vers l'accueil, qui n'a pas de garde. */
export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => {
    const service = inject(CurrentUserService);
    const router = inject(Router);
    const accueil = () => router.createUrlTree(['/accueil']);
    const verifier = (u: CurrentUser) => roles.some((r) => u.roles.includes(r)) || accueil();

    const utilisateur = service.currentUser();
    return utilisateur ? verifier(utilisateur) : service.load().pipe(map(verifier), catchError(() => of(accueil())));
  };
}
