package com.stagehub.audit.aspect;

import com.stagehub.audit.entity.ActionAudit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Demande l'audit de la méthode annotée : {@link AuditAspect} lit l'état de l'entité avant et après
 * l'appel, puis enregistre une ligne d'{@code AuditLog} dans la même transaction.
 *
 * <p><b>Piège de l'auto-invocation</b> : l'aspect est appliqué par un proxy Spring qui enveloppe le bean.
 * Seuls les appels venant d'un autre bean passent par ce proxy. Un appel interne {@code this.methode()}
 * va directement à l'objet réel : il n'est ni audité, ni transactionnel. À poser donc uniquement sur des
 * méthodes publiques appelées depuis l'extérieur de la classe (ici, par les controllers).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audite {

    ActionAudit value();

    /** Nom du paramètre de la méthode qui contient l'identifiant de l'entité auditée. */
    String idParam();
}
