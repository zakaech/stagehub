package com.stagehub.common.aspect;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.stagehub.common.aspect.service.ServiceDeTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MesureTempsAspectTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(MesureTempsAspect.class);
    private final ListAppender<ILoggingEvent> journal = new ListAppender<>();
    private Level niveauInitial;

    @BeforeEach
    void capturerLesLogs() {
        niveauInitial = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        journal.start();
        logger.addAppender(journal);
    }

    @AfterEach
    void restaurer() {
        logger.detachAppender(journal);
        logger.setLevel(niveauInitial);
    }

    @Test
    void methodeLente_logueUnWarnAvecSaDuree() throws Exception {
        proxy(Duration.ofMillis(10)).lent();

        assertThat(journal.list).singleElement().satisfies(evenement -> {
            assertThat(evenement.getLevel()).isEqualTo(Level.WARN);
            assertThat(evenement.getFormattedMessage()).contains("Service lent", "ServiceDeTest.lent()");
        });
    }

    @Test
    void methodeRapide_logueEnDebug() {
        assertThat(proxy(Duration.ofSeconds(1)).rapide()).isEqualTo("ok");

        assertThat(journal.list).singleElement()
                .satisfies(evenement -> assertThat(evenement.getLevel()).isEqualTo(Level.DEBUG));
    }

    @Test
    void exception_estMesureePuisPropagee() {
        assertThatThrownBy(() -> proxy(Duration.ofSeconds(1)).enEchec()).hasMessage("boum");

        assertThat(journal.list).hasSize(1);
    }

    private ServiceDeTest proxy(Duration seuil) {
        AspectJProxyFactory fabrique = new AspectJProxyFactory(new ServiceDeTest());
        fabrique.setProxyTargetClass(true);
        fabrique.addAspect(new MesureTempsAspect(seuil));
        return fabrique.getProxy();
    }
}
