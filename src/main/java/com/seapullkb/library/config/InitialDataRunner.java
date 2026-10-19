package com.seapullkb.library.config;

import com.seapullkb.library.service.InitialDataService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class InitialDataRunner implements ApplicationRunner {
    private final InitialDataService initialData;
    private final Environment environment;
    public InitialDataRunner(InitialDataService initialData, Environment environment) {
        this.initialData = initialData; this.environment = environment;
    }
    @Override
    public void run(ApplicationArguments args) {
        boolean demo = environment.acceptsProfiles(Profiles.of("dev"));
        initialData.initialize(demo, environment.getProperty(demo ? "app.demo-password" : "app.bootstrap-admin-password"));
    }
}
