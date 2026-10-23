package com.stagehub.common.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Mesure la durée de toutes les méthodes publiques des services. Ici un pointcut par package convient :
 * on veut couvrir tous les services, sans exception à choisir au cas par cas.
 *
 * <p>Ordre le plus prioritaire = proxy le plus extérieur : la durée inclut l'ouverture et le commit de la
 * transaction, c'est-à-dire ce que l'appelant attend réellement.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MesureTempsAspect {

    private static final Logger log = LoggerFactory.getLogger(MesureTempsAspect.class);

    private final Duration seuilLent;

    public MesureTempsAspect(@Value("${stagehub.perf.seuil-lent:500ms}") Duration seuilLent) {
        this.seuilLent = seuilLent;
    }

    @Around("execution(public * com.stagehub..service..*(..))")
    public Object mesurer(ProceedingJoinPoint appel) throws Throwable {
        long debut = System.nanoTime();
        try {
            return appel.proceed();
        } finally {
            // finally : la durée est aussi tracée quand la méthode lève une exception
            long millis = Duration.ofNanos(System.nanoTime() - debut).toMillis();
            String methode = appel.getSignature().toShortString();
            if (millis > seuilLent.toMillis()) {
                log.warn("Service lent : {} a pris {} ms (seuil {} ms)", methode, millis, seuilLent.toMillis());
            } else {
                log.debug("{} : {} ms", methode, millis);
            }
        }
    }
}
