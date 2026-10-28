import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { finalize, Observable, share, tap } from 'rxjs';
import { CurrentUser } from './current-user';

/** Identité et rôles de l'utilisateur connecté, fournis par la gateway (GET /api/me). */
@Injectable({ providedIn: 'root' })
export class CurrentUserService {
  private readonly http = inject(HttpClient);
  private readonly utilisateur = signal<CurrentUser | null>(null);

  private chargement: Observable<CurrentUser> | null = null;

  readonly currentUser = this.utilisateur.asReadonly();

  /** Les appels simultanés partagent une seule requête ; la suivante repart à neuf, succès ou erreur. */
  load(): Observable<CurrentUser> {
    this.chargement ??= this.http.get<CurrentUser>('/api/me').pipe(
      tap((u) => this.utilisateur.set(u)),
      finalize(() => (this.chargement = null)),
      share(),
    );
    return this.chargement;
  }
}
