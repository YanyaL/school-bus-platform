/**
 * Sentinel HTTP rate-limit load test for school-bus-platform.
 *
 * Required environment variables (set by run-sentinel-rate-limit.ps1):
 *   BASE_URL, SCENARIO, BASELINE_RATE, OVERLOAD_RATE,
 *   BASELINE_DURATION, OVERLOAD_DURATION
 *
 * Scenario-specific credentials — see docs/07-sentinel-load-test.md
 */
import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import crypto from 'k6/crypto';
import { Counter } from 'k6/metrics';

const totalRequests = new Counter('total_requests');
const http2xx = new Counter('http_2xx');
const http4xxBusiness = new Counter('http_4xx_business');
const http401 = new Counter('http_401');
const http409 = new Counter('http_409');
const http429RateLimited = new Counter('http_429_rate_limited');
const unexpectedResponses = new Counter('unexpected_responses');

const scenario = (__ENV.SCENARIO || 'login').toLowerCase();
const baselineRate = parsePositiveInt(__ENV.BASELINE_RATE, 2);
const overloadRate = parsePositiveInt(__ENV.OVERLOAD_RATE, 20);
const baselineDuration = __ENV.BASELINE_DURATION || '15s';
const overloadDuration = __ENV.OVERLOAD_DURATION || '20s';
const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');

const execFn = scenarioExecFunction(scenario);

export const options = {
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'],
    scenarios: {
        baseline: {
            executor: 'constant-arrival-rate',
            rate: baselineRate,
            timeUnit: '1s',
            duration: baselineDuration,
            preAllocatedVUs: 5,
            maxVUs: 50,
            exec: execFn,
        },
        overload: {
            executor: 'constant-arrival-rate',
            rate: overloadRate,
            timeUnit: '1s',
            duration: overloadDuration,
            preAllocatedVUs: 10,
            maxVUs: 100,
            exec: execFn,
            startTime: baselineDuration,
        },
    },
    thresholds: {
        total_requests: ['count>0'],
        'http_429_rate_limited{scenario:baseline}': ['count==0'],
        'http_429_rate_limited{scenario:overload}': ['count>0'],
        unexpected_responses: ['count==0'],
    },
};

export function loginRequest() {
    const payload = JSON.stringify({
        studentNumber: __ENV.TEST_STUDENT_NUMBER,
        password: __ENV.TEST_PASSWORD,
    });
    const response = http.post(`${baseUrl}/api/v1/auth/login`, payload, {
        headers: { 'Content-Type': 'application/json' },
        tags: { endpoint: 'login' },
    });
    classifyResponse(response, ['OK']);
}

export function bookingRequest() {
    const payload = JSON.stringify({
        tripNumber: __ENV.TEST_TRIP_NUMBER,
        seatNumber: __ENV.TEST_SEAT_NUMBER,
    });
    const response = http.post(`${baseUrl}/api/v1/bookings`, payload, {
        headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${__ENV.TEST_ACCESS_TOKEN}`,
            'Idempotency-Key': randomUuid(),
        },
        tags: { endpoint: 'booking' },
    });
    classifyResponse(response, ['OK']);
}

export function paymentRequest() {
    const requestNumber = `load-${exec.vu.idInTest}-${exec.scenario.iterationInTest}-${Date.now()}`;
    const amount = parseFloat(__ENV.TEST_PAYMENT_AMOUNT || '5.50');
    const payload = JSON.stringify({
        requestNumber: requestNumber,
        paymentNumber: __ENV.TEST_PAYMENT_NUMBER,
        bookingNumber: __ENV.TEST_BOOKING_NUMBER,
        amount: amount,
        paidAt: new Date().toISOString(),
    });
    const signature = signPaymentPayload(payload, __ENV.TEST_PAYMENT_CALLBACK_SECRET);
    const response = http.post(`${baseUrl}/api/v1/payments/callback`, payload, {
        headers: {
            'Content-Type': 'application/json',
            'X-Payment-Signature': signature,
        },
        tags: { endpoint: 'payment' },
    });
    classifyResponse(response, ['OK']);
}

function classifyResponse(response, successCodes) {
    totalRequests.add(1, { scenario: exec.scenario.name });

    if (response.status === 429) {
        let limited = false;
        try {
            const body = response.json();
            limited = body && body.code === 'RATE_LIMITED';
        } catch (_) {
            limited = false;
        }
        check(response, {
            '429 body code is RATE_LIMITED': () => limited,
        });
        if (limited) {
            http429RateLimited.add(1, { scenario: exec.scenario.name });
            return;
        }
        unexpectedResponses.add(1, { scenario: exec.scenario.name });
        return;
    }

    if (response.status >= 200 && response.status < 300) {
        let ok = false;
        try {
            const body = response.json();
            ok = body && successCodes.includes(body.code);
        } catch (_) {
            ok = false;
        }
        if (ok) {
            http2xx.add(1, { scenario: exec.scenario.name });
            return;
        }
        unexpectedResponses.add(1, { scenario: exec.scenario.name });
        return;
    }

    if (response.status === 401) {
        http401.add(1, { scenario: exec.scenario.name });
        return;
    }

    if (response.status === 409) {
        http409.add(1, { scenario: exec.scenario.name });
        return;
    }

    if (response.status >= 400 && response.status < 500) {
        http4xxBusiness.add(1, { scenario: exec.scenario.name });
        return;
    }

    unexpectedResponses.add(1, { scenario: exec.scenario.name });
}

function signPaymentPayload(rawBody, secret) {
    const digest = crypto.hmac('sha256', secret, rawBody, 'hex');
    return `sha256=${digest}`;
}

function randomUuid() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
        const random = (Math.random() * 16) | 0;
        const value = char === 'x' ? random : (random & 0x3) | 0x8;
        return value.toString(16);
    });
}

function scenarioExecFunction(name) {
    switch (name) {
        case 'login':
            return 'loginRequest';
        case 'booking':
            return 'bookingRequest';
        case 'payment':
            return 'paymentRequest';
        default:
            throw new Error(`unsupported SCENARIO: ${name}`);
    }
}

function parsePositiveInt(value, fallback) {
    const parsed = parseInt(value, 10);
    if (!Number.isFinite(parsed) || parsed <= 0) {
        return fallback;
    }
    return parsed;
}

export function handleSummary(data) {
    const total = metricCount(data, 'total_requests');
    const baseline429 = metricCount(
        data,
        'http_429_rate_limited{scenario:baseline}'
    );
    const overload429 = metricCount(
        data,
        'http_429_rate_limited{scenario:overload}'
    );
    const total429 = baseline429 + overload429;
    const ratio429 = total > 0
        ? `${((total429 / total) * 100).toFixed(2)}%`
        : 'n/a';

    const duration = data.metrics.http_req_duration;
    const p95 = duration?.values?.['p(95)'];
    const p99 = duration?.values?.['p(99)'];

    const lines = [
        '',
        '--- Sentinel rate-limit summary ---',
        `scenario: ${scenario}`,
        `baseline: ${baselineRate} req/s for ${baselineDuration}`,
        `overload: ${overloadRate} req/s for ${overloadDuration}`,
        '',
        `total requests: ${total}`,
        `429 baseline phase: ${baseline429}`,
        `429 overload phase: ${overload429}`,
        `429 ratio: ${ratio429}`,
        `P95 (http_req_duration): ${formatMs(p95)}`,
        `P99 (http_req_duration): ${formatMs(p99)}`,
        '-----------------------------------',
        '',
    ];

    return {
        stdout: lines.join('\n'),
    };
}

function metricCount(data, name) {
    return data.metrics[name]?.values?.count ?? 0;
}

function formatMs(value) {
    if (value === undefined || value === null) {
        return 'n/a';
    }
    return `${value.toFixed(2)} ms`;
}
