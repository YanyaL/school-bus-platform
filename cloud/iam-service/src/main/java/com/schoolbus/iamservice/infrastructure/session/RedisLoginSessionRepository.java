package com.schoolbus.iamservice.infrastructure.session;

import com.schoolbus.iamservice.application.authentication.LoginSession;
import com.schoolbus.iamservice.application.authentication.LoginSessionRepository;
import com.schoolbus.iamservice.domain.identity.UserId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
public class RedisLoginSessionRepository
        implements LoginSessionRepository {

    static final String SESSION_KEY_PREFIX =
            "school-bus:login-session:id:";
    static final String REFRESH_KEY_PREFIX =
            "school-bus:login-session:refresh:";
    static final String USER_SESSIONS_KEY_PREFIX =
            "school-bus:login-session:user:";

    private static final String SESSION_ID_FIELD = "sessionId";
    private static final String USER_ID_FIELD = "userId";
    private static final String REFRESH_TOKEN_HASH_FIELD =
            "refreshTokenHash";
    private static final String CREATED_AT_FIELD = "createdAt";
    private static final String EXPIRES_AT_FIELD = "expiresAt";

    private static final DefaultRedisScript<Long> SAVE_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local previousHash = redis.call(
                        'HGET', KEYS[1], 'refreshTokenHash'
                    )
                    if previousHash and previousHash ~= ARGV[3] then
                        redis.call('DEL', ARGV[7] .. previousHash)
                    end
                    redis.call(
                        'HSET', KEYS[1],
                        'sessionId', ARGV[1],
                        'userId', ARGV[2],
                        'refreshTokenHash', ARGV[3],
                        'createdAt', ARGV[4],
                        'expiresAt', ARGV[5]
                    )
                    redis.call('PEXPIRE', KEYS[1], ARGV[6])
                    redis.call(
                        'SET', KEYS[2], ARGV[1],
                        'PX', ARGV[6]
                    )
                    redis.call('SADD', KEYS[3], ARGV[1])
                    redis.call('PEXPIRE', KEYS[3], ARGV[6])
                    return 1
                    """,
                    Long.class
            );

    private static final DefaultRedisScript<Long> DELETE_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local refreshHash = redis.call(
                        'HGET', KEYS[1], 'refreshTokenHash'
                    )
                    local userId = redis.call(
                        'HGET', KEYS[1], 'userId'
                    )
                    local deleted = redis.call('DEL', KEYS[1])
                    if refreshHash then
                        deleted = deleted + redis.call(
                            'DEL', ARGV[1] .. refreshHash
                        )
                    end
                    if userId then
                        local userKey = ARGV[2] .. userId
                        redis.call('SREM', userKey, ARGV[3])
                        if redis.call('SCARD', userKey) == 0 then
                            redis.call('DEL', userKey)
                        end
                    end
                    return deleted
                    """,
                    Long.class
            );

    private static final DefaultRedisScript<Long> ROTATE_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local currentHash = redis.call(
                        'HGET', KEYS[1], 'refreshTokenHash'
                    )
                    if not currentHash or currentHash ~= ARGV[1] then
                        return 0
                    end
                    redis.call('DEL', ARGV[8] .. currentHash)
                    redis.call(
                        'HSET', KEYS[1],
                        'sessionId', ARGV[2],
                        'userId', ARGV[3],
                        'refreshTokenHash', ARGV[4],
                        'createdAt', ARGV[5],
                        'expiresAt', ARGV[6]
                    )
                    redis.call('PEXPIRE', KEYS[1], ARGV[7])
                    redis.call(
                        'SET', KEYS[2], ARGV[2],
                        'PX', ARGV[7]
                    )
                    redis.call('SADD', KEYS[3], ARGV[2])
                    redis.call('PEXPIRE', KEYS[3], ARGV[7])
                    return 1
                    """,
                    Long.class
            );

    private static final DefaultRedisScript<Long> DELETE_USER_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local sessionIds = redis.call('SMEMBERS', KEYS[1])
                    local deleted = 0
                    for _, sessionId in ipairs(sessionIds) do
                        local sessionKey = ARGV[1] .. sessionId
                        local refreshHash = redis.call(
                            'HGET', sessionKey, 'refreshTokenHash'
                        )
                        deleted = deleted + redis.call('DEL', sessionKey)
                        if refreshHash then
                            deleted = deleted + redis.call(
                                'DEL', ARGV[2] .. refreshHash
                            )
                        end
                    end
                    redis.call('DEL', KEYS[1])
                    return deleted
                    """,
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    public RedisLoginSessionRepository(
            StringRedisTemplate redisTemplate,
            Clock clock
    ) {
        this.redisTemplate = Objects.requireNonNull(
                redisTemplate,
                "redisTemplate must not be null"
        );
        this.clock = Objects.requireNonNull(
                clock,
                "clock must not be null"
        );
    }

    @Override
    public void save(LoginSession session) {
        LoginSession validatedSession = Objects.requireNonNull(
                session,
                "session must not be null"
        );
        Duration timeToLive = Duration.between(
                clock.instant(),
                validatedSession.expiresAt()
        );
        long timeToLiveMillis = timeToLive.toMillis();
        if (timeToLiveMillis <= 0) {
            throw new IllegalArgumentException(
                    "cannot save an expired login session"
            );
        }

        redisTemplate.execute(
                SAVE_SCRIPT,
                List.of(
                        sessionKey(validatedSession.sessionId()),
                        refreshKey(
                                validatedSession.refreshTokenHash()
                        ),
                        userSessionsKey(validatedSession.userId())
                ),
                validatedSession.sessionId(),
                Long.toString(
                        validatedSession.userId().value()
                ),
                validatedSession.refreshTokenHash(),
                validatedSession.createdAt().toString(),
                validatedSession.expiresAt().toString(),
                Long.toString(timeToLiveMillis),
                REFRESH_KEY_PREFIX
        );
    }

    @Override
    public Optional<LoginSession> findBySessionId(
            String sessionId
    ) {
        String validatedSessionId = requireText(
                sessionId,
                "sessionId"
        );
        Map<Object, Object> storedFields = redisTemplate
                .opsForHash()
                .entries(sessionKey(validatedSessionId));
        if (storedFields.isEmpty()) {
            return Optional.empty();
        }

        LoginSession session = restore(storedFields);
        if (session.isExpiredAt(clock.instant())) {
            deleteBySessionId(validatedSessionId);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    @Override
    public Optional<LoginSession> findByRefreshTokenHash(
            String refreshTokenHash
    ) {
        String validatedHash = requireText(
                refreshTokenHash,
                "refreshTokenHash"
        );
        String indexKey = refreshKey(validatedHash);
        String sessionId = redisTemplate
                .opsForValue()
                .get(indexKey);
        if (sessionId == null) {
            return Optional.empty();
        }

        Optional<LoginSession> result = findBySessionId(sessionId);
        if (result.isEmpty()
                || !result.orElseThrow()
                        .refreshTokenHash()
                        .equals(validatedHash)) {
            redisTemplate.delete(indexKey);
            return Optional.empty();
        }
        return result;
    }

    @Override
    public boolean replaceRefreshToken(
            LoginSession replacement,
            String expectedRefreshTokenHash
    ) {
        LoginSession validatedReplacement = Objects.requireNonNull(
                replacement,
                "replacement must not be null"
        );
        String validatedExpectedHash = requireText(
                expectedRefreshTokenHash,
                "expectedRefreshTokenHash"
        );
        Duration timeToLive = Duration.between(
                clock.instant(),
                validatedReplacement.expiresAt()
        );
        long timeToLiveMillis = timeToLive.toMillis();
        if (timeToLiveMillis <= 0) {
            throw new IllegalArgumentException(
                    "cannot save an expired login session"
            );
        }

        Long result = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(
                        sessionKey(
                                validatedReplacement.sessionId()
                        ),
                        refreshKey(
                                validatedReplacement
                                        .refreshTokenHash()
                        ),
                        userSessionsKey(validatedReplacement.userId())
                ),
                validatedExpectedHash,
                validatedReplacement.sessionId(),
                Long.toString(
                        validatedReplacement.userId().value()
                ),
                validatedReplacement.refreshTokenHash(),
                validatedReplacement.createdAt().toString(),
                validatedReplacement.expiresAt().toString(),
                Long.toString(timeToLiveMillis),
                REFRESH_KEY_PREFIX
        );
        return Long.valueOf(1L).equals(result);
    }

    @Override
    public void deleteBySessionId(String sessionId) {
        String validatedSessionId = requireText(
                sessionId,
                "sessionId"
        );
        redisTemplate.execute(
                DELETE_SCRIPT,
                List.of(sessionKey(validatedSessionId)),
                REFRESH_KEY_PREFIX,
                USER_SESSIONS_KEY_PREFIX,
                validatedSessionId
        );
    }

    @Override
    public void deleteByUserId(UserId userId) {
        UserId validatedUserId = Objects.requireNonNull(
                userId,
                "userId must not be null"
        );
        redisTemplate.execute(
                DELETE_USER_SCRIPT,
                List.of(userSessionsKey(validatedUserId)),
                SESSION_KEY_PREFIX,
                REFRESH_KEY_PREFIX
        );
    }

    static String sessionKey(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }

    static String refreshKey(String refreshTokenHash) {
        return REFRESH_KEY_PREFIX + refreshTokenHash;
    }

    static String userSessionsKey(UserId userId) {
        return USER_SESSIONS_KEY_PREFIX + userId.value();
    }

    private LoginSession restore(Map<Object, Object> fields) {
        try {
            return new LoginSession(
                    requiredField(fields, SESSION_ID_FIELD),
                    UserId.of(
                            Long.parseLong(
                                    requiredField(
                                            fields,
                                            USER_ID_FIELD
                                    )
                            )
                    ),
                    requiredField(
                            fields,
                            REFRESH_TOKEN_HASH_FIELD
                    ),
                    Instant.parse(
                            requiredField(fields, CREATED_AT_FIELD)
                    ),
                    Instant.parse(
                            requiredField(fields, EXPIRES_AT_FIELD)
                    )
            );
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "corrupt login session data in Redis",
                    exception
            );
        }
    }

    private String requiredField(
            Map<Object, Object> fields,
            String fieldName
    ) {
        Object value = fields.get(fieldName);
        if (!(value instanceof String stringValue)
                || stringValue.isBlank()) {
            throw new IllegalStateException(
                    "missing Redis session field: " + fieldName
            );
        }
        return stringValue;
    }

    private static String requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }
        return value;
    }
}
