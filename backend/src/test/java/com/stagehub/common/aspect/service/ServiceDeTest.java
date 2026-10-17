package com.stagehub.common.aspect.service;

/** Faux service placé dans un package "service", donc visé par le pointcut de MesureTempsAspect. */
public class ServiceDeTest {

    public String rapide() {
        return "ok";
    }

    public String lent() throws InterruptedException {
        Thread.sleep(40);
        return "ok";
    }

    public void enEchec() {
        throw new IllegalArgumentException("boum");
    }
}
