import { PassedInitialConfig } from 'angular-auth-oidc-client';

/** Keycloak est toujours atteint via son URL publique, en développement comme en conteneur. */
export const authConfig: PassedInitialConfig = {
  config: {
    authority: 'http://localhost:8180/realms/ntan',
    redirectUrl: window.location.origin,
    postLogoutRedirectUri: window.location.origin,
    clientId: 'ntan-web',
    scope: 'openid profile email',
    responseType: 'code',
    silentRenew: true,
    useRefreshToken: true,
    secureRoutes: ['/api/'],
  },
};
