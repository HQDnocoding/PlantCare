import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';
import { Rate, Trend, Counter } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('search_errors');
const duration = new Trend('search_request_duration');
const successCount = new Counter('search_successful_requests');

export const options = {
    stages: [
        { duration: '30s', target: 10 },    // Ramp up
        { duration: '1m30s', target: 20 },  // Sustain
        { duration: '30s', target: 0 },     // Ramp down
    ],
    thresholds: {
        'http_req_duration{endpoint:/search/plant}': ['p(95)<2000', 'p(99)<5000'],
        'http_req_duration{endpoint:/search/disease}': ['p(95)<2000', 'p(99)<5000'],
        'http_req_failed': ['rate<0.10'],
        'search_errors': ['rate<0.10'],
    },
};

const baseURL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {

    // 1. Basic search
    group('Search - Basic Query', () => {
        const queries = ['rau', 'cay', 'sau rieng', 'xoai'];
        const query = queries[Math.floor(Math.random() * queries.length)];

        const res = http.get(`${baseURL}/api/v1/search?q=${query}&page=0`);

        duration.add(res.timings.duration);

        const ok = check(res, {
            'status 200': (r) => r.status === 200,
            'response < 2s': (r) => r.timings.duration < 2000,
        });

        if (!ok) errorRate.add(1);
        if (res.status === 200) successCount.add(1);
    });

    sleep(0.5);

    // 2. Pagination test
    group('Search - Pagination', () => {
        const page = Math.floor(Math.random() * 5);

        const res = http.get(`${baseURL}/api/v1/search?q=cay&page=${page}`);

        duration.add(res.timings.duration);

        check(res, {
            'pagination ok': (r) => r.status === 200,
        });
    });

    sleep(0.5);

    // 3. Debug search (optional nhưng rất hữu ích)
    group('Search - Debug Endpoint', () => {
        const res = http.get(
            `${baseURL}/api/v1/search/internal/debug/search-test?q=rau`
        );

        check(res, {
            'debug search ok': (r) => r.status === 200,
        });
    });

    sleep(1);
}

export function handleSummary(data) {
    return {
        'search-results.json': JSON.stringify(data, null, 2),
        stdout: textSummary(data, { indent: ' ', enableColors: true }),
    };
}