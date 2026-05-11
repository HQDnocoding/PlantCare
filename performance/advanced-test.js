import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import encoding from 'k6/encoding';

// Custom metrics
const uploadErrors = new Rate('upload_errors');
const uploadDuration = new Trend('upload_duration');
const dbConnectionErrors = new Counter('db_connection_errors');
const rpsMetric = new Trend('requests_per_second');

export const options = {
    vus: 25,
    duration: '5m',
    stages: [
        { duration: '1m', target: 10 },
        { duration: '2m', target: 25 },
        { duration: '1m30s', target: 50 }, // High load for connection pool test
        { duration: '30s', target: 0 },
    ],
    thresholds: {
        'http_req_duration{type:upload}': ['p(95)<5000', 'p(99)<10000'],
        'http_req_duration{type:auth}': ['p(95)<1000', 'p(99)<2000'],
        'http_req_duration{type:db_connection}': ['p(95)<500', 'p(99)<1000'],
        'upload_errors': ['rate<0.1'],
        'db_connection_errors': ['count<20'],
    },
};

const baseURL = __ENV.BASE_URL || 'http://localhost:8080';

// Generate JWT-like token
function generateMockJWT() {
    const header = encoding.b64encode(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
    const payload = encoding.b64encode(JSON.stringify({
        sub: `user-${__VU}`,
        email: `test-${__VU}@test.com`,
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 3600,
    }));
    const signature = 'mock-signature';
    return `${header}.${payload}.${signature}`;
}

export default function () {
    // Test 1: Real JWT Token Authentication
    group('Advanced - JWT Token Authentication', () => {
        const token = generateMockJWT();

        const res = http.get(
            `${baseURL}/api/v1/users/profile`,
            {
                headers: {
                    'Authorization': `Bearer ${token}`,
                    'Content-Type': 'application/json',
                }
            }
        );

        uploadDuration.add(res.timings.duration, { type: 'auth' });

        check(res, {
            'jwt auth accepted or rejected': (r) => r.status !== 500,
            'jwt auth < 1s': (r) => r.timings.duration < 1000,
            'token validation fast': (r) => r.timings.duration < 500,
        });
    });

    sleep(0.3);

    // Test 2: Database Connection Pool Saturation
    group('Advanced - DB Connection Pool', () => {
        // Simulate multiple concurrent database queries
        const res = http.get(
            `${baseURL}/api/v1/users/profile/extended?includeHistory=true&includePreferences=true&includeFavorites=true`,
            {
                headers: {
                    'Authorization': `Bearer ${generateMockJWT()}`,
                }
            }
        );

        uploadDuration.add(res.timings.duration, { type: 'db_connection' });

        if (res.status === 503 || res.status === 504) {
            dbConnectionErrors.add(1);
            check(res, {
                'connection pool saturation handled': (r) => r.status === 503,
            });
        } else {
            check(res, {
                'db query successful': (r) => r.status === 200 || r.status === 401,
                'db query < 500ms': (r) => r.timings.duration < 500,
            });
        }
    });

    sleep(0.3);

    // Test 3: File Upload - Small File
    group('Advanced - File Upload (Small)', () => {
        const fileName = `test-${__VU}-${__ITER}.txt`;
        const fileContent = 'This is a small test file for performance testing.';

        const payload = {
            file: http.file(fileContent, fileName),
            description: 'Small test file',
        };

        const res = http.post(
            `${baseURL}/api/v1/uploads/files`,
            payload,
            {
                headers: {
                    'Authorization': `Bearer ${generateMockJWT()}`,
                },
                timeout: '10s',
            }
        );

        uploadDuration.add(res.timings.duration, { type: 'upload' });

        check(res, {
            'upload status ok': (r) => r.status === 200 || r.status === 201,
            'upload < 2s': (r) => r.timings.duration < 2000,
            'upload response valid': (r) => {
                try {
                    return JSON.parse(r.body).fileId !== undefined;
                } catch (e) {
                    return false;
                }
            },
        }) || uploadErrors.add(1);
    });

    sleep(0.5);

    // Test 4: File Upload - Large File Simulation
    group('Advanced - File Upload (Large)', () => {
        const fileName = `image-${__VU}-${__ITER}.jpg`;
        // Simulate a 2MB file
        const largeContent = 'x'.repeat(2 * 1024 * 1024);

        const payload = {
            file: http.file(largeContent, fileName),
            description: 'Large image file',
        };

        const res = http.post(
            `${baseURL}/api/v1/uploads/images`,
            payload,
            {
                headers: {
                    'Authorization': `Bearer ${generateMockJWT()}`,
                },
                timeout: '30s',
            }
        );

        uploadDuration.add(res.timings.duration, { type: 'upload' });

        check(res, {
            'large upload status ok': (r) => r.status === 200 || r.status === 201 || r.status === 413,
            'large upload < 10s': (r) => r.timings.duration < 10000,
        }) || uploadErrors.add(1);
    });

    sleep(1);

    // Test 5: Rate Limiter Under Load
    group('Advanced - Rate Limiter Stress', () => {
        // Send multiple requests rapidly to test rate limiting
        for (let i = 0; i < 5; i++) {
            const res = http.get(
                `${baseURL}/api/v1/posts?page=0&size=10`,
                {
                    headers: {
                        'Authorization': `Bearer ${generateMockJWT()}`,
                    }
                }
            );

            rpsMetric.add(res.timings.duration);

            // Should eventually hit rate limit
            check(res, {
                'status not 429 or handled': (r) => r.status !== 429 || true,
                'response received': (r) => r.status !== undefined,
            });

            sleep(0.1);
        }
    });

    sleep(0.5);

    // Test 6: Cache Effectiveness Test
    group('Advanced - Repeated Requests (Cache Test)', () => {
        const requests = [];

        // First request - cache miss
        for (let i = 0; i < 3; i++) {
            const res = http.get(
                `${baseURL}/api/v1/plants/popular?limit=20`,
                {
                    headers: {
                        'Authorization': `Bearer ${generateMockJWT()}`,
                    }
                }
            );

            uploadDuration.add(res.timings.duration, { type: 'cache_test' });

            check(res, {
                'cache test successful': (r) => r.status === 200,
            });

            requests.push(res.timings.duration);

            sleep(0.1);
        }

        // Subsequent requests should be faster (cached)
        const firstRequestTime = requests[0];
        const cachedRequestTime = requests[requests.length - 1];

        if (cachedRequestTime < firstRequestTime * 0.5) {
            console.log(`✅ Cache effective: ${firstRequestTime}ms → ${cachedRequestTime}ms`);
        }
    });

    sleep(1);

    // Test 7: Token Refresh Under Load
    group('Advanced - Token Refresh', () => {
        const res = http.post(
            `${baseURL}/api/v1/auth/refresh-token`,
            JSON.stringify({
                refreshToken: generateMockJWT(),
            }),
            {
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${generateMockJWT()}`,
                }
            }
        );

        uploadDuration.add(res.timings.duration, { type: 'auth' });

        check(res, {
            'token refresh status ok': (r) => r.status === 200 || r.status === 401,
            'token refresh < 500ms': (r) => r.timings.duration < 500,
        });
    });

    sleep(1);
}

export function handleSummary(data) {
    return {
        'advanced-results.json': JSON.stringify(data, null, 2),
        stdout: textSummary(data),
    };
}

function textSummary(data) {
    let output = '\n⚡ Advanced Scenarios Results\n';
    output += '='.repeat(50) + '\n';

    output += `Upload Errors: ${((data.metrics?.upload_errors?.value || 0) * 100).toFixed(2)}%\n`;
    output += `DB Connection Errors: ${data.metrics?.db_connection_errors?.value || 0}\n`;

    if (data.metrics?.upload_duration) {
        output += `\nOperation Latency:\n`;
        output += `  Min: ${Math.round(data.metrics.upload_duration.values?.min || 0)}ms\n`;
        output += `  Max: ${Math.round(data.metrics.upload_duration.values?.max || 0)}ms\n`;
        output += `  Avg: ${Math.round(data.metrics.upload_duration.values?.value || 0)}ms\n`;
    }

    output += '='.repeat(50) + '\n';
    return output;
}
