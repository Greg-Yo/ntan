import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { CurrentUser } from './current-user';

/** Identité et rôles de l'utilisateur connecté, fournis par la gateway (GET /api/me). */
@Injectable({ providedIn: 'root' })
export class CurrentUserService {
  private readonly http = inject(HttpClient);
  private readonly utilisateur = signal<CurrentUser | null>(null);

  readonly currentUser = this.utilisateur.asReadonly();

  load(): Observable<CurrentUser> {
    return this.http.get<CurrentUser>('/api/me').pipe(tap((u) => this.utilisateur.set(u)));
  }
}
