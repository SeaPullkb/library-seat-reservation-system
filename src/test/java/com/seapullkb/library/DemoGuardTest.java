package com.seapullkb.library;

import com.seapullkb.library.config.DemoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class DemoGuardTest {
    @Test void onlyExactDevDemoCombinationAndActualIsolatedCatalogAreAllowed() {
        var e=new MockEnvironment();e.setActiveProfiles("dev","demo");
        DemoConfiguration.requireIsolated(e,"library_demo_test");
        for(String catalog:new String[]{"library_seat","library_demo_","other_demo_test","library_demo_unsafe-name"})
            assertThatThrownBy(()->DemoConfiguration.requireIsolated(e,catalog)).isInstanceOf(IllegalStateException.class);
    }
    @Test void ordinaryAndProductionProfilesCannotActivateFixtureBehavior() {
        for(String[] profiles:new String[][]{{},{"dev"},{"demo"},{"prod","dev","demo"},{"test","demo"}}) {
            var e=new MockEnvironment();e.setActiveProfiles(profiles);
            assertThatThrownBy(()->DemoConfiguration.requireIsolated(e,"library_demo_test")).isInstanceOf(IllegalStateException.class);
        }
    }
}
