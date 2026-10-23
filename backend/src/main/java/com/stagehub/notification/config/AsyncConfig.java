package com.stagehub.notification.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * @EnableAsync active @Async : la méthode annotée est exécutée par un pool de threads, l'appelant n'attend pas.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    public static final String MAIL_EXECUTOR = "mailExecutor";

    /**
     * Pool dédié aux emails. Volume faible (quelques changements de statut par minute) mais envoi SMTP lent :
     * <ul>
     *   <li>2 threads permanents ;</li>
     *   <li>file bornée à 100 tâches : si le serveur SMTP tombe, la mémoire ne grossit pas sans limite ;</li>
     *   <li>jusqu'à 4 threads, créés seulement quand la file est pleine (comportement de ThreadPoolExecutor) ;</li>
     *   <li>file pleine : CallerRunsPolicy, l'email est envoyé par le thread appelant. La requête ralentit
     *       (contre-pression) mais aucun email n'est perdu ;</li>
     *   <li>à l'arrêt, les emails en file sont envoyés (20 s maximum).</li>
     * </ul>
     */
    @Bean(MAIL_EXECUTOR)
    ThreadPoolTaskExecutor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("mail-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        return executor;
    }

    /** Filet de sécurité : une exception imprévue dans une méthode @Async void serait sinon perdue. */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (exception, methode, parametres) ->
                log.error("Échec de la tâche asynchrone {}", methode.getName(), exception);
    }
}
