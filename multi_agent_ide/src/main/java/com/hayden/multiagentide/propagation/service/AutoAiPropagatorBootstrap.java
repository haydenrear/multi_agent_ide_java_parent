package com.hayden.multiagentide.propagation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutoAiPropagatorBootstrap {

    private final PropagatorRegistrationService propagatorRegistrationService;

    @Bean
    public ApplicationRunner bootstrapAutoAiPropagators() {
        return args -> seedAutoAiPropagators();
    }

    @Transactional
    public void seedAutoAiPropagators() {
        int upserted = propagatorRegistrationService.ensureAutoAiPropagatorsRegistered();
        log.info("Ensured {} auto AI propagator registrations.", upserted);
    }
}
