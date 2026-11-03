/**
 * URL relative : en dev, `ng serve` la relaie au backend via proxy.conf.json ;
 * en production, Nginx fait de même. Le code ne connaît jamais l'adresse du backend.
 */
export const API_URL = '/api/v1';
