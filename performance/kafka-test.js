import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('kafka_errors');
const duration = new Trend('kafka_message_latency');
const publishCount = new Counter('kafka_published_messages');
const consumeCount = new Counter('kafka_consumed_messages');

export const options = {
    vus: 30,
    duration: '4m',
    stages: [
        { duration: '1m', target: 10 },    // Warm up
        { duration: '2m', target: 30 },    // Sustained load
        { duration: '30s', target: 50 },   // Spike
        { duration: '30s', target: 0 },    // Ramp down
    ],
    thresholds: {
        'http_req_duration{type:publish}': ['p(95)<1000', 'p(99)<2000'],
        'http_req_duration{type:consume}': ['p(95)<3000', 'p(99)<5000'],
        'kafka_errors': ['rate<0.05'],
        'http_req_failed': ['rate<0.05'],
    },
};

const baseURL = __ENV.BASE_URL || 'http://localhost:8080';
const kafkaProducerURL = __ENV.KAFKA_PRODUCER_URL || 'http://localhost:8080/api/v1/events';
const kafkaConsumerURL = __ENV.KAFKA_CONSUMER_URL || 'http://localhost:8080/api/v1/messages';

export default function () {
    // Test 1: Publish User Events
    group('Kafka - Publish User Events', () => {
        const userId = `user-${__VU}-${__ITER}`;
        const payload = JSON.stringify({
            eventType: 'user.created',
            userId: userId,
            email: `test-${__VU}@test.com`,
            timestamp: new Date().toISOString(),
        });

        const res = http.post(
            `${kafkaProducerURL}/users`,
            payload,
            {
                headers: {
                    'Content-Type': 'application/json',
                    'X-Service': 'performance-test',
                }
            }
        );

        duration.add(res.timings.duration, { type: 'publish' });

        check(res, {
            'user event published': (r) => r.status === 200 || r.status === 202,
            'publish latency < 1s': (r) => r.timings.duration < 1000,
        }) || errorRate.add(1);

        if (res.status === 200 || r.status === 202) {
            publishCount.add(1);
        }
    });

    sleep(0.2);

    // Test 2: Publish Post Events
    group('Kafka - Publish Post Events', () => {
        const postId = `post-${__VU}-${__ITER}`;
        const payload = JSON.stringify({
            eventType: 'post.created',
            postId: postId,
            userId: `user-${__VU}`,
            title: `Test Post ${__VU}-${__ITER}`,
            content: 'Performance test post content',
            timestamp: new Date().toISOString(),
        });

        const res = http.post(
            `${kafkaProducerURL}/posts`,
            payload,
            {
                headers: {
                    'Content-Type': 'application/json',
                    'X-Service': 'performance-test',
                }
            }
        );

        duration.add(res.timings.duration, { type: 'publish' });

        check(res, {
            'post event published': (r) => r.status === 200 || r.status === 202,
        }) || errorRate.add(1);

        if (res.status === 200 || res.status === 202) {
            publishCount.add(1);
        }
    });

    sleep(0.2);

    // Test 3: Publish Notification Events
    group('Kafka - Publish Notification Events', () => {
        const payload = JSON.stringify({
            eventType: 'notification.send',
            userId: `user-${__VU}`,
            notificationType: 'post_comment',
            title: 'New comment on your post',
            message: 'Someone commented on your post',
            timestamp: new Date().toISOString(),
        });

        const res = http.post(
            `${kafkaProducerURL}/notifications`,
            payload,
            {
                headers: {
                    'Content-Type': 'application/json',
                    'X-Service': 'performance-test',
                }
            }
        );

        duration.add(res.timings.duration, { type: 'publish' });

        check(res, {
            'notification event published': (r) => r.status === 200 || r.status === 202,
        }) || errorRate.add(1);

        publishCount.add(1);
    });

    sleep(0.3);

    // Test 4: Consumer Lag Check (read messages)
    group('Kafka - Consumer Lag Monitoring', () => {
        const res = http.get(
            `${kafkaConsumerURL}/lag?topic=user-events&consumerGroup=user-service`,
            {
                headers: {
                    'Content-Type': 'application/json',
                }
            }
        );

        duration.add(res.timings.duration, { type: 'consume' });

        check(res, {
            'lag check status ok': (r) => r.status === 200 || r.status === 404,
            'lag check latency < 3s': (r) => r.timings.duration < 3000,
        });

        if (res.status === 200) {
            try {
                const data = JSON.parse(res.body);
                console.log(`Consumer lag: ${data.lag || 'N/A'}`);
                consumeCount.add(1);
            } catch (e) {
                errorRate.add(1);
            }
        }
    });

    sleep(0.5);

    // Test 5: Bulk Event Publishing (High Throughput)
    group('Kafka - Bulk Publishing', () => {
        const events = [];
        for (let i = 0; i < 10; i++) {
            events.push({
                eventType: 'bulk.event',
                id: `${__VU}-${__ITER}-${i}`,
                data: `Performance test event ${i}`,
                timestamp: new Date().toISOString(),
            });
        }

        const payload = JSON.stringify({
            events: events
        });

        const res = http.post(
            `${kafkaProducerURL}/batch`,
            payload,
            {
                headers: {
                    'Content-Type': 'application/json',
                    'X-Service': 'performance-test',
                }
            }
        );

        duration.add(res.timings.duration, { type: 'publish' });

        check(res, {
            'bulk publish successful': (r) => r.status === 200 || r.status === 202,
            'bulk publish < 2s': (r) => r.timings.duration < 2000,
        }) || errorRate.add(1);

        publishCount.add(10);
    });

    sleep(1);
}

export function handleSummary(data) {
    return {
        'kafka-results.json': JSON.stringify(data, null, 2),
        stdout: textSummary(data),
    };
}

function textSummary(data) {
    let output = '\n📨 Kafka Message Queue Performance Results\n';
    output += '='.repeat(50) + '\n';

    if (data.metrics) {
        const latency = data.metrics.kafka_message_latency;
        output += `Message Latency:\n`;
        output += `  Min: ${Math.round(latency?.values?.min || 0)}ms\n`;
        output += `  Max: ${Math.round(latency?.values?.max || 0)}ms\n`;
        output += `  Avg: ${Math.round(latency?.values?.value || 0)}ms\n`;
        output += `\nThroughput:\n`;
        output += `  Published: ${data.metrics.kafka_published_messages?.value || 0} messages\n`;
        output += `  Consumed: ${data.metrics.kafka_consumed_messages?.value || 0} messages\n`;
        output += `  Error Rate: ${((data.metrics.kafka_errors?.value || 0) * 100).toFixed(2)}%\n`;
    }

    output += '='.repeat(50) + '\n';
    return output;
}
