package com.schoolbus.bookingservice.shared.infrastructure.messaging;

import com.schoolbus.bookingservice.shared.application.messaging.ConsumedEventCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class RedisConsumedEventCache implements ConsumedEventCache {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(RedisConsumedEventCache.class);

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;

    public RedisConsumedEventCache(
            StringRedisTemplate redisTemplate,
            @Value("${school-bus.messaging.consumed-event-cache.key-prefix:school-bus:mq:consumed}")
            String keyPrefix
    ) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = requireText(keyPrefix, "keyPrefix");
    }

    @Override
    public boolean contains(String consumerName, String eventId) {
        String key = key(
                requireText(consumerName, "consumerName"),
                requireText(eventId, "eventId")
        );
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Redis consumed-event cache unavailable; falling back to MySQL"
            );
            return false;
        }
    }

    private String key(String consumerName, String eventId) {
        return keyPrefix + ":" + consumerName + ":" + eventId;
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
