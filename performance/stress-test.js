import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter, Gauge } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const duration = new Trend('request_duration');
const successCount = new Counter('successful_requests');
const httpErrorCount = new Counter('http_errors');
const rps = new Gauge('requests_per_second');

// Test configuration
export const options = {
    vus: 10,                    // Virtual Users (start)
    duration: '5m',             // Total duration

    // Stress test: gradually ramp up to find breaking point
    // Note: AI chat uses 1 worker, keep concurrent chat requests low
    stages: [
        { duration: '1m', target: 5 },       // Ramp up to 5 users
        { duration: '2m', target: 20 },      // Ramp up to 20 users
        { duration: '2m', target: 50 },      // Ramp up to 50 users
        { duration: '1m', target: 100 },     // Push to 100 users (stress)
        { duration: '1m', target: 0 },       // Ramp down
    ],

    thresholds: {
        // Non-AI endpoints: 95% < 1s, 99% < 2s
        'http_req_duration{endpoint:/auth/login}': ['p(95)<1000', 'p(99)<2000'],
        'http_req_duration{endpoint:/community/posts}': ['p(95)<1000', 'p(99)<2000'],
        'http_req_duration{endpoint:/user/profile}': ['p(95)<1000', 'p(99)<2000'],
        // AI Chat: p(95) < 90s (Gemini RAG pipeline)
        'http_req_duration{endpoint:/ai-chat/chat}': ['p(95)<90000'],
        'http_req_failed': ['rate<0.1'],
        'errors': ['rate<0.1'],
    },

    // Report configuration
    ext: {
        loadimpact: {
            projectID: 3433385,
            name: "Plant App Backend Stress Test"
        }
    }
};

// Test data
const baseURL = __ENV.BASE_URL || 'http://localhost:8080';
const authBaseURL = __ENV.AUTH_BASE_URL || 'http://localhost:8080';

// Mock signed_request for testing
const mockSignedRequest = 'mock_signature.eyJ1c2VyX2lkIjoiMTIzNDU2Nzg5In0=';

export default function () {
    // Test 1: Auth - Login
    group('Auth Service - Login', () => {
        const loginPayload = JSON.stringify({
            phone: '0912345678',
            password: 'TestPassword123!',
        });

        const res = http.post(
            `${authBaseURL}/api/v1/auth/login`,
            loginPayload,
            { headers: { 'Content-Type': 'application/json' } }
        );

        duration.add(res.timings.duration, { endpoint: '/auth/login' });
        rps.add(1);

        check(res, {
            'login status 2xx or 401': (r) => (r.status >= 200 && r.status < 300) || r.status === 401,
            'login response time < 500ms': (r) => r.timings.duration < 500,
        }) || errorRate.add(1);

        if (res.status >= 200 && res.status < 300) {
            successCount.add(1);
        } else if (res.status !== 401) {
            httpErrorCount.add(1);
        }
    });

    sleep(0.5);

    // Test 2: Facebook delete-data endpoint
    group('Auth Service - Facebook Delete', () => {
        const payload = JSON.stringify({
            signed_request: mockSignedRequest,
            user_id: '123456789'
        });

        const res = http.post(
            `${authBaseURL}/api/v1/auth/facebook/delete-data`,
            payload,
            { headers: { 'Content-Type': 'application/json' } }
        );

        duration.add(res.timings.duration, { endpoint: '/facebook/delete-data' });
        rps.add(1);

        check(res, {
            'delete-data status 200 or 401': (r) => r.status === 200 || r.status === 401,
            'delete-data response time < 500ms': (r) => r.timings.duration < 500,
        }) || errorRate.add(1);

        if (res.status >= 400 && res.status !== 401) {
            httpErrorCount.add(1);
        } else {
            successCount.add(1);
        }
    });

    sleep(0.5);

    // Test 3: Community Service - Get Posts
    group('Community Service - Get Posts', () => {
        const communityURL = __ENV.COMMUNITY_BASE_URL || 'http://localhost:8080';
        const res = http.get(`${communityURL}/api/v1/posts?page=0&size=20`);

        duration.add(res.timings.duration, { endpoint: '/community/posts' });
        rps.add(1);

        check(res, {
            'posts status 2xx or 401': (r) => (r.status >= 200 && r.status < 300) || r.status === 401,
            'posts response time < 1000ms': (r) => r.timings.duration < 1000,
        }) || errorRate.add(1);

        if (res.status >= 200 && res.status < 300) {
            successCount.add(1);
        } else if (res.status !== 401) {
            httpErrorCount.add(1);
        }
    });

    sleep(0.5);

    // Test 4: Community Service - Create Post
    group('Community Service - Create Post', () => {
        const communityURL = __ENV.COMMUNITY_BASE_URL || 'http://localhost:8080';
        const postPayload = JSON.stringify({
            title: `Test Post ${__VU}`,
            description: 'This is a performance test post',
            content: 'Test content for performance testing',
        });

        const res = http.post(
            `${communityURL}/api/v1/posts`,
            postPayload,
            {
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': 'Bearer test-token'
                }
            }
        );

        duration.add(res.timings.duration, { endpoint: '/community/posts/create' });
        rps.add(1);

        check(res, {
            'create post status 2xx or 401': (r) => (r.status >= 200 && r.status < 300) || r.status === 401,
            'create post response time < 1000ms': (r) => r.timings.duration < 1000,
        }) || errorRate.add(1);

        if (res.status >= 400 && res.status !== 401) {
            httpErrorCount.add(1);
        }
    });

    sleep(0.5);

    // Test 5: User Service - Get Profile
    group('User Service - Get Profile', () => {
        const userURL = __ENV.USER_BASE_URL || 'http://localhost:8080';
        const res = http.get(
            `${userURL}/api/v1/users/profile`,
            {
                headers: {
                    'Authorization': 'Bearer test-token'
                }
            }
        );

        duration.add(res.timings.duration, { endpoint: '/user/profile' });
        rps.add(1);

        check(res, {
            'profile status 2xx or 401': (r) => (r.status >= 200 && r.status < 300) || r.status === 401,
            'profile response time < 500ms': (r) => r.timings.duration < 500,
        }) || errorRate.add(1);

        if (res.status >= 200 && res.status < 300) {
            successCount.add(1);
        } else if (res.status !== 401) {
            httpErrorCount.add(1);
        }
    });

    sleep(0.5);

    // Test 6: AI Chat Service (through gateway)
    group('AI Chat - Chat Completion', () => {
        const aiURL = __ENV.BASE_URL || 'http://localhost:8080';
        const userId = `test-user-${__VU}`;

        const formData = {
            message: 'Bệnh xì mủ trên sầu riêng điều trị thế nào?',
            conv_id: '',
        };

        const res = http.post(
            `${aiURL}/api/v1/chat`,
            formData,
            {
                headers: {
                    'X-User-Id': userId,
                },
                timeout: '200s',  // Match gateway response-timeout
            }
        );

        duration.add(res.timings.duration, { endpoint: '/ai-chat/chat' });
        rps.add(1);

        check(res, {
            'chat status 2xx or 503': (r) => (r.status >= 200 && r.status < 300) || r.status === 503,
            'chat not 500': (r) => r.status !== 500,
        }) || errorRate.add(1);

        if (res.status >= 200 && res.status < 300) {
            successCount.add(1);
        } else if (res.status >= 400) {
            httpErrorCount.add(1);
        }
    });

    sleep(0.5);

    // Test 7: Health Checks
    group('Health Checks', () => {
        const res = http.get(`${authBaseURL}/api/v1/auth/health`);

        duration.add(res.timings.duration, { endpoint: '/health' });
        rps.add(1);

        check(res, {
            'health status is 200': (r) => r.status === 200,
            'health response time < 100ms': (r) => r.timings.duration < 100,
        }) || errorRate.add(1);

        if (res.status === 200) {
            successCount.add(1);
        } else {
            httpErrorCount.add(1);
        }
    });

    sleep(1);
}

export function handleSummary(data) {
    return {
        'summary.json': JSON.stringify(data),
        stdout: textSummary(data, { indent: ' ', enableColors: true }),
    };
}

// Simple text summary
function textSummary(data, options = {}) {
    const indent = options.indent || '';
    let output = '\n✅ K6 Performance Test Summary\n';
    output += `${indent}${'='.repeat(50)}\n`;

    if (data.metrics) {
        output += `${indent}📊 Request Duration:\n`;
        output += `${indent}  Min: ${Math.round(data.metrics.http_req_duration?.values?.min || 0)}ms\n`;
        output += `${indent}  Max: ${Math.round(data.metrics.http_req_duration?.values?.max || 0)}ms\n`;
        output += `${indent}  Avg: ${Math.round(data.metrics.http_req_duration?.values?.value || 0)}ms\n`;

        output += `${indent}📈 Success Rate:\n`;
        output += `${indent}  Success: ${data.metrics.successful_requests?.value || 0}\n`;
        output += `${indent}  Errors: ${data.metrics.http_errors?.value || 0}\n`;
        output += `${indent}  Error Rate: ${((data.metrics.errors?.value || 0) * 100).toFixed(2)}%\n`;
    }

    output += `${indent}${'='.repeat(50)}\n`;
    return output;
}
