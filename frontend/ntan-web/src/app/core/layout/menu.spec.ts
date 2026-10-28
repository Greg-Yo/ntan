import { menuPourRoles } from './menu';

describe('menuPourRoles', () => {
  const libelles = (roles: Parameters<typeof menuPourRoles>[0]) => menuPourRoles(roles).map((i) => i.libelle);

  it('montre tout à un administrateur', () => {
    expect(libelles(['ADMIN'])).toEqual(['Accueil', 'Tiers', 'Catalogue', 'Ventes', 'Paramètres']);
  });

  it('masque les paramètres au magasinier', () => {
    expect(libelles(['MAGASINIER'])).toEqual(['Accueil', 'Tiers', 'Catalogue', 'Ventes']);
  });

  it('ne montre rien à un utilisateur sans rôle Ntan', () => {
    expect(libelles([])).toEqual([]);
  });
});
