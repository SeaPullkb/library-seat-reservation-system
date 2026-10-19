package com.seapullkb.library.config;

import com.seapullkb.library.service.QueryDates;
import java.sql.SQLException;
import java.time.*;
import java.util.Arrays;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

/** Opt-in demo clock; checks the connected catalog rather than trusting DB_NAME. */
@Configuration
@Profile("demo")
public class DemoConfiguration {
    public static void requireIsolated(Environment environment,String catalog) {
        var profiles=Set.copyOf(Arrays.asList(environment.getActiveProfiles()));
        if(!profiles.equals(Set.of("dev","demo")) || catalog==null || !catalog.matches("library_demo_[a-z0-9_]{1,50}"))
            throw new IllegalStateException("演示功能仅允许 dev,demo 组合及 library_demo_ 前缀的独立数据库。");
    }
    @Bean @Primary
    public Clock demoClock(DataSource dataSource,Environment environment) throws SQLException {
        try(var connection=dataSource.getConnection()) { requireIsolated(environment,connection.getCatalog()); }
        LocalDate date=LocalDate.parse(environment.getRequiredProperty("app.demo-date"));
        QueryDates.requireSupported(date);
        QueryDates.requireSupported(date.minusDays(14));
        QueryDates.requireSupported(date.plusDays(1));
        ZoneId zone=ZoneId.of("Asia/Shanghai");
        LocalTime time=LocalTime.parse(environment.getProperty("app.demo-time","09:05"));
        return Clock.fixed(date.atTime(time).atZone(zone).toInstant(),zone);
    }
}
