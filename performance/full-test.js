import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import encoding from 'k6/encoding';

// Custom metrics
const errorRate = new Rate('full_test_errors');
const duration = new Trend('full_test_duration');
const successCount = new Counter('full_test_success');
const totalRequests = new Counter('full_test_requests');

export const options = {
    vus: 30,
    duration: '10m',
    stages: [
        { duration: '2m', target: 15 },
        { duration: '4m', target: 30 },
        { duration: '2m', target: 50 }, // Stress peak
        { duration: '1m', target: 0 },
    ],
    thresholds: {
        'http_req_duration': ['p(95)<2000', 'p(99)<5000'],
        'full_test_errors': ['rate<0.1'],
        'http_req_failed': ['rate<0.1'],
    },
};

const baseURL = __ENV.BASE_URL || 'http://localhost:8080';
const searchServiceURL = __ENV.SEARCH_SERVICE_URL || 'http://localhost:8085';

function generateJWT() {
    const header = encoding.b64encode(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
    const payload = encoding.b64encode(JSON.stringify({
        sub: `user-${__VU}`,
        email: `test-${__VU}@test.com`,
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 3600,
    }));
    return `${header}.${payload}.mock-sig`;
}

export default function () {
    // Phase 1: Community Operations
    if (__ITER % 4 === 0) {
        group('Full Test - Community Posts', () => {
            const res = http.get(
                `${baseURL}/api/v1/posts?page=0&size=10`,
                { headers: { 'Authorization': `Bearer ${generateJWT()}` } }
            );
            duration.add(res.timings.duration);
            totalRequests.add(1);
            check(res, {
                'posts ok': (r) => r.status >= 200 && r.status < 500,
            }) || errorRate.add(1);
        });
        sleep(0.3);
    }

    // Phase 2: Search Operations
    if (__ITER % 4 === 1) {
        group('Full Test - Search', () => {
            const queries = ['rau cải', 'cây lúa', 'bệnh xì mủ'];
            const query = queries[__ITER % queries.length];
            const res = http.get(
                `${searchServiceURL}/api/v1/search/plants?q=${encodeURIComponent(query)}&page=0&size=10`
            );
            duration.add(res.timings.duration);
            totalRequests.add(1);
            check(res, {
                'search ok': (r) => r.status >= 200 && r.status < 500,
            }) || errorRate.add(1);
        });
        sleep(0.3);
    }

    // Phase 3: AI Chat (Expensive Operation)
    if (__ITER % 4 === 3) {
        group('Full Test - AI Chat', () => {
            const res = http.post(
                `${baseURL}/api/v1/chat`,
                JSON.stringify({
                    message: 'Bệnh xì mủ trên sầu riêng?',
                    conv_id: '',
                }),
                {
                    headers: {
                        'Content-Type': 'application/json',
                        'X-User-Id': `user-${__VU}`,
                    },
                    timeout: '120s',
                }
            );
            duration.add(res.timings.duration);
            totalRequests.add(1);
            check(res, {
                'chat ok': (r) => r.status >= 200 && r.status < 500,
            }) || errorRate.add(1);
        });
        sleep(1);
    }

    // Periodically: Event Publishing (Kafka)
    if (__ITER % 10 === 0) {
        group('Full Test - Event Publishing', () => {
            const res = http.post(
                `${baseURL}/api/v1/events/users`,
                JSON.stringify({
                    eventType: 'user.action',
                    userId: `user-${__VU}`,
                    action: 'viewed_plant',
                    timestamp: new Date().toISOString(),
                }),
                { headers: { 'Content-Type': 'application/json' } }
            );
            duration.add(res.timings.duration);
            totalRequests.add(1);
            if (res.status === 200 || res.status === 202) {
                successCount.add(1);
            }
        });
        sleep(0.3);
    }

    // Periodically: User Profile Access (DB Heavy)
    if (__ITER % 15 === 0) {
        group('Full Test - User Profile', () => {
            const res = http.get(
                `${baseURL}/api/v1/users/profile`,
                { headers: { 'Authorization': `Bearer ${generateJWT()}` } }
            );
            duration.add(res.timings.duration);
            totalRequests.add(1);
            check(res, {
                'profile ok': (r) => r.status >= 200 && r.status < 500,
            }) || errorRate.add(1);
        });
        sleep(0.5);
    }

    sleep(0.5);
}

export function handleSummary(data) {
    return {
        'full-test-results.json': JSON.stringify(data, null, 2),
        stdout: textSummary(data),
    };
}

function textSummary(data) {
    let output = '\n🎯 Full System Test Results\n';
    output += '='.repeat(60) + '\n';

    output += `Total Requests: ${data.metrics?.full_test_requests?.value || 0}\n`;
    output += `Successful: ${data.metrics?.full_test_success?.value || 0}\n`;
    output += `Error Rate: ${((data.metrics?.full_test_errors?.value || 0) * 100).toFixed(2)}%\n`;

    if (data.metrics?.full_test_duration) {
        const dur = data.metrics.full_test_duration.values;
        output += `\nLatency Breakdown:\n`;
        output += `  Min: ${Math.round(dur?.min || 0)}ms\n`;
        output += `  P50: ${Math.round(dur?.['p(50)'] || 0)}ms\n`;
        output += `  P95: ${Math.round(dur?.['p(95)'] || 0)}ms\n`;
        output += `  P99: ${Math.round(dur?.['p(99)'] || 0)}ms\n`;
        output += `  Max: ${Math.round(dur?.max || 0)}ms\n`;
    }

    output += '='.repeat(60) + '\n';
    output += '✅ Full system test completed\n';
    return output;
}
