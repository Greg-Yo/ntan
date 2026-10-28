import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRouteSnapshot, provideRouter, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { firstValueFrom, isObservable, Observable, of, throwError } from 'rxjs';
import { roleGuard } from './role.guard';
import { CurrentUserService } from './current-user.service';
import { CurrentUser } from './current-user';

describe('roleGuard', () => {
  const magasinier: CurrentUser = { username: 'magasinier.ntan', nom: 'Brice Magasinier', roles: ['MAGASINIER'] };

  function configurer(utilisateurCharge: CurrentUser | null, chargement: Observable<CurrentUser> = of(magasinier)) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: CurrentUserService, useValue: { currentUser: signal(utilisateurCharge), load: () => chargement } },
      ],
    });
  }

  async function executer(...roles: Parameters<typeof roleGuard>) {
    const resultat = TestBed.runInInjectionContext(() =>
      roleGuard(...roles)({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );
    return isObservable(resultat) ? firstValueFrom(resultat) : resultat;
  }

  it('autorise un utilisateur qui a l’un des rôles', async () => {
    configurer(magasinier);
    expect(await executer('ADMIN', 'MAGASINIER')).toBe(true);
  });

  it('redirige vers /accueil un utilisateur sans le rôle requis', async () => {
    configurer(magasinier);
    const resultat = await executer('ADMIN', 'COMMERCIAL');
    expect(resultat).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(resultat as UrlTree)).toBe('/accueil');
  });

  it('charge l’utilisateur s’il ne l’est pas encore', async () => {
    configurer(null, of(magasinier));
    expect(await executer('MAGASINIER')).toBe(true);
  });

  it('redirige vers /accueil un utilisateur chargé sans le rôle requis', async () => {
    configurer(null, of(magasinier));
    const resultat = await executer('ADMIN');
    expect(resultat).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(resultat as UrlTree)).toBe('/accueil');
  });

  it('redirige vers /accueil quand le chargement échoue', async () => {
    configurer(null, throwError(() => new Error('401')));
    const resultat = await executer('MAGASINIER');
    expect(resultat).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(resultat as UrlTree)).toBe('/accueil');
  });
});
