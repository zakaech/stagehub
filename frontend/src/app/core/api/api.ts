/**
 * URL relative : en dev, `ng serve` la relaie au backend via proxy.conf.json ;
 * en production (phase 5), Nginx fera de même. Le code ne connaît jamais l'adresse du backend.
 */
export const API_URL = '/api/v1';
