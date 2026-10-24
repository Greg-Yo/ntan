export type Role = 'ADMIN' | 'COMMERCIAL' | 'MAGASINIER';

export interface CurrentUser {
  username: string;
  nom: string;
  roles: Role[];
}
