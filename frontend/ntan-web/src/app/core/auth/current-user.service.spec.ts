import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { CurrentUserService } from './current-user.service';
import { CurrentUser } from './current-user';

describe('CurrentUserService', () => {
  const admin: CurrentUser = { username: 'admin.ntan', nom: 'Awa Admin', roles: ['ADMIN'] };
  let service: CurrentUserService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(CurrentUserService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('est vide avant chargement', () => {
    expect(service.currentUser()).toBeNull();
  });

  it('charge /api/me et mémorise l’utilisateur', async () => {
    const resultat = firstValueFrom(service.load());
    http.expectOne('/api/me').flush(admin);

    expect(await resultat).toEqual(admin);
    expect(service.currentUser()).toEqual(admin);
  });

  it('partage une requête en cours entre appels simultanés', async () => {
    const premier = firstValueFrom(service.load());
    const second = firstValueFrom(service.load());
    http.expectOne('/api/me').flush(admin);

    expect(await premier).toEqual(admin);
    expect(await second).toEqual(admin);
  });

  it('émet une nouvelle requête après une erreur', async () => {
    const echec = firstValueFrom(service.load());
    http.expectOne('/api/me').flush('erreur', { status: 500, statusText: 'Server Error' });
    await expect(echec).rejects.toBeDefined();

    const reprise = firstValueFrom(service.load());
    http.expectOne('/api/me').flush(admin);
    expect(await reprise).toEqual(admin);
  });

  it('émet une nouvelle requête après un chargement réussi', async () => {
    const un = firstValueFrom(service.load());
    http.expectOne('/api/me').flush(admin);
    await un;

    const deux = firstValueFrom(service.load());
    http.expectOne('/api/me').flush(admin);
    await deux;
  });
});
