package vn.anpay.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;

class RedisConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class))
            .withInitializer(context -> {
                try {
                    for (var source : new YamlPropertySourceLoader().load(
                            "application", new ClassPathResource("application.yml"))) {
                        context.getEnvironment().getPropertySources().addLast(source);
                    }
                } catch (java.io.IOException ex) {
                    throw new IllegalStateException(ex);
                }
            });

    @Test
    void startsWithHostAndPortWhenNoUrlIsConfigured() {
        runner.withPropertyValues("REDIS_HOST=redis.internal", "REDIS_PORT=6380")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var factory = context.getBean(LettuceConnectionFactory.class);
                    assertThat(factory.getHostName()).isEqualTo("redis.internal");
                    assertThat(factory.getPort()).isEqualTo(6380);
                });
    }

    @Test
    void acceptsExplicitCloudUrl() {
        runner.withPropertyValues("spring.data.redis.url=redis://redis.internal:6381")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var factory = context.getBean(LettuceConnectionFactory.class);
                    assertThat(factory.getHostName()).isEqualTo("redis.internal");
                    assertThat(factory.getPort()).isEqualTo(6381);
                });
    }

    @Test
    void enablesTlsForSecureCloudUrl() {
        runner.withPropertyValues("spring.data.redis.url=rediss://redis.internal:6382")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var factory = context.getBean(LettuceConnectionFactory.class);
                    assertThat(factory.getHostName()).isEqualTo("redis.internal");
                    assertThat(factory.getPort()).isEqualTo(6382);
                    assertThat(factory.getClientConfiguration().isUseSsl()).isTrue();
                });
    }
}
