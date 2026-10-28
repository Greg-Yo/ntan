import { Component, inject } from '@angular/core';
import { MatChipsModule } from '@angular/material/chips';
import { CurrentUserService } from '../../core/auth/current-user.service';

@Component({
  selector: 'app-accueil',
  imports: [MatChipsModule],
  template: `
    @if (utilisateur(); as u) {
      <h1>Bonjour {{ u.nom }}</h1>
      @if (u.roles.length > 0) {
        <p>Vos rôles :</p>
        <mat-chip-set aria-label="Rôles">
          @for (role of u.roles; track role) {
            <mat-chip>{{ role }}</mat-chip>
          }
        </mat-chip-set>
      } @else {
        <p class="alerte">Aucun rôle Ntan ne vous est attribué. Contactez un administrateur.</p>
      }
    } @else {
      <p>Chargement…</p>
    }
  `,
  styles: `
    .alerte { color: var(--mat-sys-error); }
  `,
})
export class Accueil {
  protected readonly utilisateur = inject(CurrentUserService).currentUser;
}
