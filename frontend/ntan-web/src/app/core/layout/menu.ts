import { Role } from '../auth/current-user';

export interface MenuItem {
  libelle: string;
  lien: string;
  icone: string;
  roles: readonly Role[];
}

const TOUS: readonly Role[] = ['ADMIN', 'COMMERCIAL', 'MAGASINIER'];

/** Droits de lecture par module (spec §5.7). */
export const MENU: readonly MenuItem[] = [
  { libelle: 'Accueil', lien: '/accueil', icone: 'home', roles: TOUS },
  { libelle: 'Tiers', lien: '/tiers', icone: 'groups', roles: TOUS },
  { libelle: 'Catalogue', lien: '/catalogue', icone: 'inventory_2', roles: TOUS },
  { libelle: 'Ventes', lien: '/ventes', icone: 'receipt_long', roles: TOUS },
  { libelle: 'Paramètres', lien: '/parametres', icone: 'settings', roles: ['ADMIN', 'COMMERCIAL'] },
];

export function menuPourRoles(roles: readonly Role[]): MenuItem[] {
  return MENU.filter((item) => item.roles.some((r) => roles.includes(r)));
}
