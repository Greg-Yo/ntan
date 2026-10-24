import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// Port hôte du gateway : variable d'environnement, sinon infra/.env, sinon 8080.
function lirePortDepuisEnvFile() {
  try {
    const fichier = resolve(dirname(fileURLToPath(import.meta.url)), '../../infra/.env');
    for (const ligne of readFileSync(fichier, 'utf8').split(/\r?\n/)) {
      const m = ligne.match(/^\s*(?:export\s+)?NTAN_GATEWAY_PORT\s*=\s*["']?(\d+)["']?\s*(?:#.*)?$/);
      if (m) return m[1];
    }
  } catch {
    // fichier absent ou illisible : on retombe sur la valeur par défaut
  }
  return undefined;
}

const port = process.env.NTAN_GATEWAY_PORT || lirePortDepuisEnvFile() || '8080';

export default {
  '/api': {
    target: `http://localhost:${port}`,
    secure: false,
  },
};
