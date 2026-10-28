import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { throwError } from 'rxjs';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { Shell } from './shell';
import { CurrentUserService } from '../auth/current-user.service';

describe('Shell', () => {
  it('affiche un message d’erreur et garde la déconnexion si le profil est introuvable', async () => {
    TestBed.configureTestingModule({
      imports: [Shell],
      providers: [
        provideRouter([]),
        { provide: CurrentUserService, useValue: { currentUser: signal(null), load: () => throwError(() => new Error('500')) } },
        { provide: OidcSecurityService, useValue: { logoff: () => throwError(() => new Error('non utilisé')) } },
      ],
    });
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const racine = fixture.nativeElement as HTMLElement;
    expect(racine.textContent).toContain('Impossible de récupérer votre profil');
    expect(racine.querySelector('button[aria-label="Se déconnecter"]')).not.toBeNull();
    expect(racine.querySelectorAll('a[mat-list-item]').length).toBe(0);
  });
});
