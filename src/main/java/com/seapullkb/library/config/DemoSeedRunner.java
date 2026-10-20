package com.seapullkb.library.config;

import com.seapullkb.library.service.DemoDataset;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component @Profile("demo")
@ConditionalOnProperty(name="app.demo-seed.enabled",havingValue="true")
public class DemoSeedRunner implements ApplicationRunner {
    private final DemoDataset dataset;
    private final Environment environment;
    public DemoSeedRunner(DemoDataset dataset,Environment environment) { this.dataset=dataset;this.environment=environment; }
    public void run(ApplicationArguments arguments) { dataset.seed(environment.getRequiredProperty("app.demo-password")); }
}
