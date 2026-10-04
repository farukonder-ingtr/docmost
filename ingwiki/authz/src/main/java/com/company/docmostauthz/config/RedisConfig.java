package com.company.docmostauthz.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Caches LDAP group membership lookups in Redis so that we don't hit the
 * directory on every single authorization check. See docmost-ldap-page-authorization.md
 * section 33 for the TTL trade-off (fresher revocations vs. LDAP load).
 */
@Configuration
@EnableCaching
public class RedisConfig {

    public static final String USER_GROUPS_CACHE = "ldapUserGroups";

    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            LdapProperties ldapProperties) {

        // Values serialize with the default JdkSerializationRedisSerializer,
        // which is why LdapUser implements Serializable (classic com.fasterxml
        // jackson-databind isn't on the classpath under Spring Boot 4's Jackson 3 baseline).
        RedisCacheConfiguration config = RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(ldapProperties.getGroupsCacheTtlSeconds()))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
