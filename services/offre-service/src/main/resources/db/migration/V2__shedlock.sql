-- ShedLock : verrous des tâches planifiées, partagés par toutes les instances d'offre-service.
-- Une ligne par tâche : avant de s'exécuter, une instance prend le verrou (lock_until dans le futur) ; les autres
-- voient la ligne verrouillée et sautent ce créneau. Table au format attendu par ShedLock (JdbcTemplateLockProvider).
CREATE TABLE shedlock (
    name        VARCHAR(64)  PRIMARY KEY,
    lock_until  TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_by   VARCHAR(255) NOT NULL
);
