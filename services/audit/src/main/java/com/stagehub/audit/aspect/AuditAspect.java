package com.stagehub.audit.aspect;

import com.stagehub.audit.service.AuditService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Audit des méthodes annotées {@link Audite}.
 *
 * <p>Ordre des proxys : la transaction (AuditTransactionConfig) enveloppe cet aspect, qui s'exécute donc
 * <em>dans</em> la transaction métier. Conséquences :
 * <ul>
 *   <li>l'état "avant" est lu dans la même transaction, juste avant l'action ;</li>
 *   <li>si l'action lève une exception, on ne passe jamais à l'enregistrement ;</li>
 *   <li>si la transaction est annulée plus tard (au commit), la ligne d'audit est annulée avec elle.</li>
 * </ul>
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AuditAspect {

    private final AuditService auditService;
    private final Map<String, LecteurEtatAudit> lecteurs;

    public AuditAspect(AuditService auditService, List<LecteurEtatAudit> lecteurs) {
        this.auditService = auditService;
        this.lecteurs = lecteurs.stream().collect(Collectors.toMap(LecteurEtatAudit::entite, Function.identity()));
    }

    @Around("@annotation(audite)")
    public Object auditer(ProceedingJoinPoint appel, Audite audite) throws Throwable {
        Long id = identifiant(appel, audite.idParam());
        LecteurEtatAudit lecteur = lecteurs.get(audite.entite());
        if (lecteur == null) {
            throw new IllegalStateException("Aucun LecteurEtatAudit pour " + audite.entite());
        }
        String avant = lecteur.lireEtat(id).orElse(null);
        // Si l'action échoue (exception), elle remonte telle quelle et rien n'est audité
        Object resultat = appel.proceed();
        // Relu après l'action : l'état réellement obtenu, pas seulement l'argument reçu
        String apres = lecteur.lireEtat(id).orElse(null);
        auditService.enregistrer(audite.action(), audite.entite(), id, avant, apres);
        return resultat;
    }

    /** Récupère l'argument dont le nom est donné par idParam (noms conservés grâce à l'option -parameters). */
    private static Long identifiant(ProceedingJoinPoint appel, String nomParametre) {
        MethodSignature signature = (MethodSignature) appel.getSignature();
        int index = Arrays.asList(signature.getParameterNames()).indexOf(nomParametre);
        if (index < 0 || !(appel.getArgs()[index] instanceof Long id)) {
            throw new IllegalStateException("@Audite : paramètre Long '%s' introuvable sur %s"
                    .formatted(nomParametre, signature.toShortString()));
        }
        return id;
    }
}
