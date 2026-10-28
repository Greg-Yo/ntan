import { Component, computed, inject, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { CurrentUserService } from '../auth/current-user.service';
import { menuPourRoles } from './menu';

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive,
    MatToolbarModule, MatSidenavModule, MatListModule, MatIconModule, MatButtonModule,
  ],
  template: `
    <mat-toolbar class="barre">
      <span class="titre">Ntan</span>
      <span class="espace"></span>
      @if (utilisateur(); as u) {
        <span class="nom">{{ u.nom }}</span>
      }
      <button mat-icon-button (click)="deconnecter()" aria-label="Se déconnecter">
        <mat-icon>logout</mat-icon>
      </button>
    </mat-toolbar>
    <mat-sidenav-container class="conteneur">
      <mat-sidenav mode="side" opened class="menu">
        <mat-nav-list>
          @for (item of menu(); track item.lien) {
            <a mat-list-item [routerLink]="item.lien" routerLinkActive #actif="routerLinkActive" [activated]="actif.isActive">
              <mat-icon matListItemIcon>{{ item.icone }}</mat-icon>
              <span matListItemTitle>{{ item.libelle }}</span>
            </a>
          }
        </mat-nav-list>
      </mat-sidenav>
      <mat-sidenav-content class="contenu">
        <router-outlet />
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: `
    :host { display: flex; flex-direction: column; height: 100%; }
    .barre { background: var(--mat-sys-primary); color: var(--mat-sys-on-primary); }
    .titre { font-weight: 500; }
    .espace { flex: 1; }
    .nom { margin-right: 8px; font-size: 14px; }
    .conteneur { flex: 1; }
    .menu { width: 220px; }
    .contenu { padding: 24px; }
  `,
})
export class Shell implements OnInit {
  private readonly currentUserService = inject(CurrentUserService);
  private readonly oidc = inject(OidcSecurityService);

  protected readonly utilisateur = this.currentUserService.currentUser;
  protected readonly menu = computed(() => menuPourRoles(this.utilisateur()?.roles ?? []));

  ngOnInit(): void {
    this.currentUserService.load().subscribe();
  }

  protected deconnecter(): void {
    this.oidc.logoff().subscribe();
  }
}
