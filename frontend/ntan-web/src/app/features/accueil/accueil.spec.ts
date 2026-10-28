import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Accueil } from './accueil';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { CurrentUser } from '../../core/auth/current-user';

describe('Accueil', () => {
  async function afficher(utilisateur: CurrentUser | null): Promise<string> {
    TestBed.configureTestingModule({
      imports: [Accueil],
      providers: [{ provide: CurrentUserService, useValue: { currentUser: signal(utilisateur) } }],
    });
    const fixture = TestBed.createComponent(Accueil);
    fixture.detectChanges();
    await fixture.whenStable();
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('salue l’utilisateur et affiche ses rôles', async () => {
    const texte = await afficher({ username: 'admin.ntan', nom: 'Awa Admin', roles: ['ADMIN'] });
    expect(texte).toContain('Bonjour Awa Admin');
    expect(texte).toContain('ADMIN');
  });

  it('prévient l’utilisateur qui n’a aucun rôle', async () => {
    const texte = await afficher({ username: 'sans.role', nom: 'sans.role', roles: [] });
    expect(texte).toContain('Aucun rôle Ntan ne vous est attribué');
  });

  it('indique le chargement tant que l’utilisateur est inconnu', async () => {
    expect(await afficher(null)).toContain('Chargement');
  });
});
