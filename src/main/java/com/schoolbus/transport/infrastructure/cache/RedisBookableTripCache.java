package com.schoolbus.transport.infrastructure.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.transport.application.trip.BookableTripCache;
import com.schoolbus.transport.application.trip.BookableTripView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
@Profile("!test")
public class RedisBookableTripCache implements BookableTripCache {

    static final String CACHE_KEY =
            "school-bus:transport:bookable-trips";
    static final String EMPTY_MARKER = "__EMPTY__";

    private static final DefaultRedisScript<Long> REPLACE_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    redis.call('DEL', KEYS[1])
                    for index = 2, #ARGV do
                        redis.call('RPUSH', KEYS[1], ARGV[index])
                    end
                    redis.call('PEXPIRE', KEYS[1], ARGV[1])
                    return #ARGV - 1
                    """,
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration timeToLive;

    public RedisBookableTripCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${school-bus.trip-list-cache.ttl:PT1M}")
            Duration timeToLive
    ) {
        this.redisTemplate = Objects.requireNonNull(
                redisTemplate,
                "redisTemplate must not be null"
        );
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper must not be null"
        );
        this.timeToLive = Objects.requireNonNull(
                timeToLive,
                "timeToLive must not be null"
        );
        if (timeToLive.isZero() || timeToLive.isNegative()) {
            throw new IllegalArgumentException(
                    "timeToLive must be positive"
            );
        }
    }

    @Override
    public Optional<List<BookableTripView>> findAll() {
        List<String> cachedValues = redisTemplate
                .opsForList()
                .range(CACHE_KEY, 0, -1);
        if (cachedValues == null || cachedValues.isEmpty()) {
            return Optional.empty();
        }
        if (cachedValues.size() == 1
                && EMPTY_MARKER.equals(cachedValues.getFirst())) {
            return Optional.of(List.of());
        }
        return Optional.of(
                cachedValues.stream()
                        .map(this::deserialize)
                        .toList()
        );
    }

    @Override
    public void replaceAll(List<BookableTripView> trips) {
        List<BookableTripView> validatedTrips = List.copyOf(
                Objects.requireNonNull(
                        trips,
                        "trips must not be null"
                )
        );
        List<String> arguments = new ArrayList<>();
        arguments.add(Long.toString(timeToLive.toMillis()));
        if (validatedTrips.isEmpty()) {
            arguments.add(EMPTY_MARKER);
        } else {
            validatedTrips.stream()
                    .map(this::serialize)
                    .forEach(arguments::add);
        }
        redisTemplate.execute(
                REPLACE_SCRIPT,
                List.of(CACHE_KEY),
                arguments.toArray()
        );
    }

    @Override
    public void evict() {
        redisTemplate.delete(CACHE_KEY);
    }

    private String serialize(BookableTripView trip) {
        try {
            return objectMapper.writeValueAsString(trip);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "failed to serialize bookable trip cache entry",
                    exception
            );
        }
    }

    private BookableTripView deserialize(String value) {
        try {
            return objectMapper.readValue(
                    value,
                    BookableTripView.class
            );
        } catch (JsonProcessingException exception) {
            evict();
            throw new IllegalStateException(
                    "corrupt bookable trip cache entry",
                    exception
            );
        }
    }
}
