import http from 'k6/http';
import { check, sleep } from 'k6';

// Quick smoke test - verify endpoints are working
export const options = {
    vus: 1,
    duration: '10s',
    thresholds: {
        http_req_duration: ['p(99)<1500'], // 99% of requests must complete below 1.5s
        http_req_failed: ['rate<0.1'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
    // Test 1: Health check
    const healthRes = http.get(`${BASE_URL}/api/v1/auth/health`);
    check(healthRes, {
        'auth health is 200': (r) => r.status === 200,
    });

    sleep(1);

    // Test 2: Facebook delete endpoint (should respond with 400/401 for invalid signature)
    const deleteRes = http.post(
        `${BASE_URL}/api/v1/auth/facebook/delete-data`,
        JSON.stringify({ signed_request: 'invalid' }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    check(deleteRes, {
        'facebook endpoint is accessible': (r) => r.status >= 200 && r.status < 500,
    });

    sleep(1);
}
