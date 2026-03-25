package com.moneytransfer.fraud.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

@Configuration
public class RedisScriptConfig {
    @Bean
    public DefaultRedisScript<Long> checkAndRecordVelocityScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(
              new ResourceScriptSource(
                    new ClassPathResource("scripts/check_and_record_velocity.lua")
              )
        );
        script.setResultType(Long.class);

        return script;
    }

    @Bean
    public DefaultRedisScript<Long> setIdempotencyKeyScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(
              new ResourceScriptSource(
                    new ClassPathResource("scripts/set_idempotency_key.lua")
              )
        );
        script.setResultType(Long.class);

        return script;
    }
}