package com.stagehub.outbox.service;

import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Le contexte réel (OpenTelemetry) est vérifié par les tests d'intégration des services ; ici, les cas limites. */
class ContexteDeTraceTest {

    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    private final Tracer tracer = mock(Tracer.class);
    private final CurrentTraceContext courant = mock(CurrentTraceContext.class);
    private final Propagator propagator = mock(Propagator.class);

    @Test
    void sansTracageDansLeService_renvoieNull() {
        ContexteDeTrace contexte = new ContexteDeTrace(fournisseur(Tracer.class, null), fournisseur(Propagator.class, null));

        assertThat(contexte.traceparentCourant()).isNull();
    }

    @Test
    void horsDeTouteTrace_renvoieNull() {
        when(tracer.currentTraceContext()).thenReturn(courant);
        when(courant.context()).thenReturn(null);
        ContexteDeTrace contexte = new ContexteDeTrace(fournisseur(Tracer.class, tracer), fournisseur(Propagator.class, propagator));

        assertThat(contexte.traceparentCourant()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void dansUneTrace_renvoieLEnTeteEcritParLePropagateur() {
        TraceContext traceContext = mock(TraceContext.class);
        when(tracer.currentTraceContext()).thenReturn(courant);
        when(courant.context()).thenReturn(traceContext);
        doAnswer(invocation -> {
            Propagator.Setter<Map<String, String>> setter = invocation.getArgument(2);
            setter.set(invocation.getArgument(1), "traceparent", TRACEPARENT);
            return null;
        }).when(propagator).inject(eq(traceContext), any(), any());
        ContexteDeTrace contexte = new ContexteDeTrace(fournisseur(Tracer.class, tracer), fournisseur(Propagator.class, propagator));

        assertThat(contexte.traceparentCourant()).isEqualTo(TRACEPARENT);
    }

    private static <T> ObjectProvider<T> fournisseur(Class<T> type, T bean) {
        StaticListableBeanFactory fabrique = new StaticListableBeanFactory();
        if (bean != null) {
            fabrique.addBean(type.getSimpleName(), bean);
        }
        return fabrique.getBeanProvider(type);
    }
}
