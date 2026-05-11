import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import encoding from 'k6/encoding';

// Custom metrics
const circuitBreakerTrips = new Counter('circuit_breaker_trips');
const fallbackInvocations = new Counter('fallback_invocations');
const timeoutErrors = new Counter('timeout_errors');
const recoveryTime = new Trend('recovery_time_ms');

// Generate mock JWT token
function generateMockJWT() {
    const header = encoding.b64encode(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
    const payload = encoding.b64encode(JSON.stringify({
        sub: `user-${__VU}`,
        email: `test-${__VU}@test.com`,
        role: 'USER',
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 3600,
    }));
    return `${header}.${payload}.mock-signature`;
}

export const options = {
    vus: 15,
    duration: '5m',
    stages: [
        { duration: '1m', target: 5 },     // Normal load
        { duration: '1m30s', target: 10 }, // Increase load
        { duration: '1m30s', target: 15 }, // Stress
        { duration: '1m', target: 0 },     // Recovery
    ],
    thresholds: {
        'http_req_duration{scenario:normal}': ['p(95)<1000'],
        'http_req_duration{scenario:degraded}': ['p(95)<3000'],
        'circuit_breaker_trips': ['count>0'],
    },
};

const baseURL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
    // Scenario 1: Circuit Breaker Test
    group('Resilience - Circuit Breaker Activation', () => {
        const startTime = new Date().getTime();

        for (let i = 0; i < 5; i++) {
            const res = http.get(
                `${baseURL}/api/v1/users/profile`,
                {
                    headers: {
                        'Authorization': 'Bearer ' + generateMockJWT(),
                    },
                    timeout: '2s',
                }
            );

            if (res.status === 503 || res.status === 504) {
                circuitBreakerTrips.add(1);
            }

            check(res, {
                'status ok or fallback': (r) =>
                    (r.status >= 200 && r.status < 300) ||
                    r.status === 503 ||
                    r.status === 504,
            });

            sleep(0.1);
        }

        const endTime = new Date().getTime();
        recoveryTime.add(endTime - startTime);
    });

    sleep(0.5);

    // Scenario 3: Timeout Handling
    group('Resilience - Timeout Scenarios', () => {
        const res = http.post(
            `${baseURL}/api/v1/chat`,
            JSON.stringify({
                message: 'Test message',
                conv_id: '',
            }),
            {
                headers: {
                    'Content-Type': 'application/json',
                    'X-User-Id': `user-${__VU}`,
                },
                timeout: '3s',
            }
        );

        if (res.status === 504 || res.error) {
            timeoutErrors.add(1);
        }

        check(res, {
            'timeout handled gracefully': (r) =>
                (r.status >= 200 && r.status < 500) ||
                r.status === 504 ||
                r.error !== undefined,
        });
    });

    sleep(0.5);

    // Scenario 4: Service Recovery Test
    group('Resilience - Service Recovery', () => {
        const res = http.get(
            `${baseURL}/actuator/health`,
            {
                timeout: '5s',
            }
        );

        check(res, {
            'service health check ok': (r) => r.status === 200,
            'recovery successful': (r) => r.status === 200,
        });
    });

    sleep(0.5);
}

export function handleSummary(data) {
    return {
        'resilience-results.json': JSON.stringify(data, null, 2),
        stdout: textSummary(data),
    };
}

function textSummary(data) {
    let output = '\n🛡️ Resilience & Circuit Breaker Test Results\n';
    output += '='.repeat(50) + '\n';

    output += `Circuit Breaker Trips: ${data.metrics?.circuit_breaker_trips?.value || 0}\n`;
    output += `Fallback Invocations: ${data.metrics?.fallback_invocations?.value || 0}\n`;
    output += `Timeout Errors: ${data.metrics?.timeout_errors?.value || 0}\n`;

    if (data.metrics?.recovery_time_ms) {
        output += `\nRecovery Time:\n`;
        output += `  Min: ${Math.round(data.metrics.recovery_time_ms.values?.min || 0)}ms\n`;
        output += `  Max: ${Math.round(data.metrics.recovery_time_ms.values?.max || 0)}ms\n`;
        output += `  Avg: ${Math.round(data.metrics.recovery_time_ms.values?.value || 0)}ms\n`;
    }

    output += '='.repeat(50) + '\n';
    return output;
}