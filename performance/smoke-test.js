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
    // Test 1: Gateway Health Check (public endpoint via gateway)
    const healthRes = http.get(`${BASE_URL}/actuator/health`);
    check(healthRes, {
        'gateway health is 200': (r) => r.status === 200,
    });

    sleep(1);
}
