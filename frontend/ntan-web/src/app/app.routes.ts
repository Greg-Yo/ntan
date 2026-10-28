import { Routes } from '@angular/router';
import { autoLoginPartialRoutesGuard } from 'angular-auth-oidc-client';
import { Shell } from './core/layout/shell';
import { roleGuard } from './core/auth/role.guard';

const aVenir = () => import('./features/a-venir/a-venir').then((m) => m.AVenir);

export const routes: Routes = [
  {
    path: '',
    component: Shell,
    canActivate: [autoLoginPartialRoutesGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'accueil' },
      { path: 'accueil', loadComponent: () => import('./features/accueil/accueil').then((m) => m.Accueil) },
      { path: 'tiers', loadComponent: aVenir, canActivate: [roleGuard('ADMIN', 'COMMERCIAL', 'MAGASINIER')] },
      { path: 'catalogue', loadComponent: aVenir, canActivate: [roleGuard('ADMIN', 'COMMERCIAL', 'MAGASINIER')] },
      { path: 'ventes', loadComponent: aVenir, canActivate: [roleGuard('ADMIN', 'COMMERCIAL', 'MAGASINIER')] },
      { path: 'parametres', loadComponent: aVenir, canActivate: [roleGuard('ADMIN', 'COMMERCIAL')] },
    ],
  },
  { path: '**', redirectTo: '' },
];
