package com.stagehub.outbox.service;

import com.stagehub.outbox.OutboxEntetes;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Lit le contexte de trace courant (phase 8 a) au format W3C {@code traceparent} : version, traceId, id du span et
 * indicateur d'échantillonnage, ex. {@code 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01}. On passe par
 * le {@link Propagator} de Micrometer, celui qui écrit cet en-tête dans les requêtes HTTP : même format partout.
 * Sans traçage dans le service (ou hors de toute trace), renvoie null.
 */
@Component
public class ContexteDeTrace {

    private final ObjectProvider<Tracer> tracer;
    private final ObjectProvider<Propagator> propagator;

    public ContexteDeTrace(ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    public String traceparentCourant() {
        Tracer t = tracer.getIfAvailable();
        Propagator p = propagator.getIfAvailable();
        if (t == null || p == null) {
            return null;
        }
        TraceContext contexte = t.currentTraceContext().context();
        if (contexte == null) {
            return null;
        }
        Map<String, String> entetes = new HashMap<>();
        p.inject(contexte, entetes, Map::put);
        return entetes.get(OutboxEntetes.TRACEPARENT);
    }
}
