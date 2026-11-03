package com.stagehub.ai.service;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Reprend une trace enregistrée (en-tête W3C {@code traceparent}) pour exécuter un traitement différé :
 * l'analyse d'une candidature démarre dans une tâche planifiée, longtemps après la requête de l'étudiant. Un nouveau
 * span, enfant de celui de la candidature, porte le même traceId : les logs de l'analyse et l'événement publié
 * (l'outbox lit le contexte courant) se retrouvent avec la requête d'origine.
 */
@Component
public class RepriseDeTrace {

    private final ObjectProvider<Tracer> tracer;
    private final ObjectProvider<Propagator> propagator;

    public RepriseDeTrace(ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    public void executer(String traceparent, String nom, Runnable traitement) {
        Tracer t = tracer.getIfAvailable();
        Propagator p = propagator.getIfAvailable();
        if (t == null || p == null) {
            traitement.run();
            return;
        }
        Span.Builder constructeur = traceparent == null
                ? t.spanBuilder()
                : p.extract(Map.of("traceparent", traceparent), Map::get);
        Span span = constructeur.name(nom).start();
        try (Tracer.SpanInScope ignore = t.withSpan(span)) {
            traitement.run();
        } finally {
            span.end();
        }
    }
}
