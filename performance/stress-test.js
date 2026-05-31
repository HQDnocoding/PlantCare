import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';
import { Rate, Trend, Counter } from 'k6/metrics';
import encoding from 'k6/encoding';

// Custom metrics
const errorRate = new Rate('errors');
const duration = new Trend('request_duration');
const successCount = new Counter('successful_requests');
const httpErrorCount = new Counter('http_errors');
const requestCount = new Counter('requests_per_second');

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

// Test configuration
export const options = {
    // Stress test: gradually ramp up to find breaking point
    // Note: AI chat now uses CPU version of torch (lighter weight)
    stages: [
        { duration: '1m', target: 5 },       // Ramp up to 5 users
        { duration: '2m', target: 20 },      // Ramp up to 20 users
        { duration: '2m', target: 50 },      // Ramp up to 50 users
        { duration: '1m', target: 100 },     // Push to 100 users (stress)
        { duration: '1m', target: 0 },       // Ramp down
    ],

    thresholds: {
        // Non-AI endpoints: 95% < 1s, 99% < 2s
        'http_req_duration{endpoint:/community/posts}': ['p(95)<1000', 'p(99)<2000'],
        'http_req_duration{endpoint:/user/profile}': ['p(95)<1000', 'p(99)<2000'],
        // AI Chat: p(95) < 90s (Gemini RAG pipeline)
        'http_req_duration{endpoint:/ai-chat/chat}': ['p(95)<90000'],
        'http_req_failed': ['rate<0.1'],
        'errors': ['rate<0.1'],
    },

    ext: {
        loadimpact: {
            projectID: 3433385,
            name: 'Plant App Backend Stress Test',
        },
    },
};

// Test data
const baseURL = __ENV.BASE_URL || 'http://localhost:8080';
const authBaseURL = __ENV.AUTH_BASE_URL || 'http://localhost:8080';
const mockSignedRequest = 'mock_signature.eyJ1c2VyX2lkIjoiMTIzNDU2Nzg5In0=';

export default function () {
    // Test 1: Community Service - Get Posts
    group('Community Service - Get Posts', () => {
        const communityURL = __ENV.COMMUNITY_BASE_URL || 'http://localhost:8080';
        const res = http.get(`${communityURL}/api/v1/posts?page=0&size=20`);

        duration.add(res.timings.duration, { endpoint: '/community/posts' });
        requestCount.add(1);

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

    // Test 3: Community Service - Create Post
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
                    'Authorization': 'Bearer ' + generateMockJWT(),
                },
            }
        );

        duration.add(res.timings.duration, { endpoint: '/community/posts/create' });
        requestCount.add(1);

        check(res, {
            'create post status 2xx or 401': (r) => (r.status >= 200 && r.status < 300) || r.status === 401,
            'create post response time < 1000ms': (r) => r.timings.duration < 1000,
        }) || errorRate.add(1);

        if (res.status >= 400 && res.status !== 401) {
            httpErrorCount.add(1);
        } else {
            successCount.add(1);
        }
    });

    sleep(0.5);

    // Test 4: User Service - Get Profile
    group('User Service - Get Profile', () => {
        const userURL = __ENV.USER_BASE_URL || 'http://localhost:8080';
        const res = http.get(
            `${userURL}/api/v1/users/profile`,
            {
                headers: {
                    'Authorization': 'Bearer ' + generateMockJWT(),
                },
            }
        );

        duration.add(res.timings.duration, { endpoint: '/user/profile' });
        requestCount.add(1);

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

    // // Test 5: AI Chat Service (through gateway) - COMMENTED: needs valid auth token
    // group('AI Chat - Chat Completion', () => {
    //     const aiURL = __ENV.BASE_URL || 'http://localhost:8080';
    //     const userId = `test-user-${__VU}`;

    //     const chatPayload = JSON.stringify({
    //         message: 'Benh xi mu tren sau rieng dieu tri the nao?',
    //         conv_id: '',
    //     });

    //     const res = http.post(
    //         `${aiURL}/api/v1/chat`,
    //         chatPayload,
    //         {
    //             headers: {
    //                 'Content-Type': 'application/json',
    //                 'X-User-Id': userId,
    //             },
    //             timeout: '200s',
    //         }
    //     );

    //     duration.add(res.timings.duration, { endpoint: '/ai-chat/chat' });
    //     requestCount.add(1);

    //     check(res, {
    //         'chat status 2xx or 503': (r) => (r.status >= 200 && r.status < 300) || r.status === 503,
    //         'chat not 500': (r) => r.status !== 500,
    //     }) || errorRate.add(1);

    //     if (res.status >= 200 && res.status < 300) {
    //         successCount.add(1);
    //     } else if (res.status >= 400) {
    //         httpErrorCount.add(1);
    //     }
    // });

    sleep(0.5);

    // Test 6: Gateway Health Check
    group('Gateway Health Check', () => {
        const res = http.get(`${baseURL}/actuator/health`);

        duration.add(res.timings.duration, { endpoint: '/gateway/health' });
        requestCount.add(1);

        check(res, {
            'health status is 200': (r) => r.status === 200,
            'health response time < 200ms': (r) => r.timings.duration < 200,
        }) || errorRate.add(1);

        if (res.status === 200) {
            successCount.add(1);
        } else {
            httpErrorCount.add(1);
        }
    });

    sleep(0.5);

    // // Test 7: AI Chat - Spike Test - COMMENTED: needs valid auth token
    // // Note: not a true simultaneous spike since VUs ramp up at different times.
    // // For a true spike, use a separate scenario with executor: 'shared-iterations'.
    // group('AI Chat - High Concurrency Spike', () => {
    //     if (__ITER === 0) {
    //         const startTime = new Date().getTime();

    //         const aiURL = __ENV.BASE_URL || 'http://localhost:8080';
    //         const userId = `spike-user-${__VU}-${__ITER}`;

    //         const chatPayload = JSON.stringify({
    //             message: 'Cach xu ly benh heo xanh tren cay ca chua?',
    //             conv_id: `spike-${__ITER}`,
    //         });

    //         const res = http.post(
    //             `${aiURL}/api/v1/chat`,
    //             chatPayload,
    //             {
    //                 headers: {
    //                     'Content-Type': 'application/json',
    //                     'X-User-Id': userId,
    //                 },
    //                 timeout: '120s',
    //             }
    //         );

    //         duration.add(res.timings.duration, { endpoint: '/ai-chat/spike' });
    //         requestCount.add(1);

    //         check(res, {
    //             'spike chat responded': (r) => r.status >= 200 && r.status < 600,
    //             'spike not 500': (r) => r.status !== 500,
    //             'spike timeout ok': (r) => r.error === undefined || r.status === 503,
    //         }) || errorRate.add(1);

    //         if (res.status >= 200 && res.status < 300) {
    //             successCount.add(1);
    //         } else if (res.status >= 400) {
    //             httpErrorCount.add(1);
    //         }

    //         const endTime = new Date().getTime();
    //         console.log(`Spike test VU ${__VU} iteration ${__ITER}: ${endTime - startTime}ms`);
    //     }
    // });

    sleep(1);
}

export function handleSummary(data) {
    return {
        'summary.json': JSON.stringify(data),
        stdout: textSummary(data, { indent: ' ', enableColors: true }),
    };
}