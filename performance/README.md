# K6 Performance Testing Guide

Complete performance testing setup for Plant App Backend using k6.

## 📋 Prerequisites

- k6 installed (if not: `choco install k6`)
- Services running locally or accessible via network
- PowerShell 5.1+ (for Windows)

## 🚀 Quick Start

### 1. Smoke Test (Quick Verification)
```powershell
./run-k6-tests.ps1 -TestType smoke
```
**What it does:** Verifies endpoints are responding (10 seconds, 1 user)

### 2. Stress Test (Find Breaking Point)
```powershell
./run-k6-tests.ps1 -TestType stress -VUs 100
```
**Stages:**
- 1m at 10 users
- 2m at 50 users  
- 2m at 100 users
- 1m at 200 users (stress)
- 1m ramp down

### 3. Load Test (Sustained Traffic)
```powershell
./run-k6-tests.ps1 -TestType load -VUs 50
```
**Stages:**
- 2m ramp up to 50 users
- 5m steady at 50 users
- 2m ramp down

### 4. Spike Test (Traffic Burst)
```powershell
./run-k6-tests.ps1 -TestType spike
```
**Stages:**
- 10s ramp to 10 users
- 1m sudden jump to 500 users
- 10s drop to 0

### 5. Endurance Test (Long-Running)
```powershell
./run-k6-tests.ps1 -TestType endurance
```
**Stages:**
- 1m ramp up to 50 users
- 10m sustained
- 1m ramp down

## 🎯 Test Different Endpoints

### Default (All Services)
```powershell
./run-k6-tests.ps1 -TestType stress -BaseURL "http://localhost:8080"
```

### Specific Service
```powershell
# Auth Service
./run-k6-tests.ps1 -TestType stress -BaseURL "http://localhost:8080"

# Community Service
$env:COMMUNITY_BASE_URL="http://localhost:8082"
k6 run stress-test.js

# User Service
$env:USER_BASE_URL="http://localhost:8083"
k6 run stress-test.js
```

## 📊 Understanding Results

### Key Metrics

| Metric | Description | Target |
|--------|-------------|--------|
| Duration (ms) | Response time | < 500ms p95 |
| Throughput (req/s) | Requests per second | > 100 |
| Error Rate | Failed requests | < 1% |
| Memory | Peak memory usage | Monitor |
| CPU | CPU utilization | Monitor |

### Result Files

- `k6-results/k6-stress-YYYY-MM-DD_HH-mm-ss.json` - Raw test data
- `k6-results/summary-stress-YYYY-MM-DD_HH-mm-ss.txt` - Summary output

### Analyzing Results

```powershell
# View summary
Get-Content k6-results/summary-*.txt | Select-Object -Last 50

# Parse JSON results
$results = Get-Content k6-results/k6-*.json | ConvertFrom-Json
$results.metrics | Select-Object -ExpandProperty "*" | Format-Table
```

## 🔧 Customization

### Modify Test Parameters

Edit `stress-test.js` and change:

```javascript
export const options = {
  vus: 50,              // Number of virtual users
  duration: '10m',      // Total test duration
  
  stages: [
    { duration: '2m', target: 50 },    // Ramp up
    { duration: '5m', target: 50 },    // Sustain
    { duration: '2m', target: 0 },     // Ramp down
  ],
  
  thresholds: {
    'http_req_duration': ['p(95)<500'],  // 95th percentile < 500ms
    'http_req_failed': ['rate<0.1'],     // Error rate < 10%
  },
};
```

### Add Custom Endpoints

In `stress-test.js`:

```javascript
group('Custom Endpoint', () => {
  const res = http.post(
    `${baseURL}/api/v1/your-endpoint`,
    JSON.stringify({ /* payload */ }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 500ms': (r) => r.timings.duration < 500,
  });
});
```

## 🐛 Troubleshooting

### "Cannot find k6 command"
```powershell
choco install k6 -y
# Restart PowerShell
```

### Connection refused
- Check if services are running on expected ports
- Verify BASE_URL environment variable
- Check firewall rules

### High error rate
- Verify endpoint validity
- Check authentication headers
- Review application logs from services

### Memory/CPU spikes
- Reduce VUs for next test
- Check for memory leaks in application
- Monitor database connections

## 📈 Performance Analysis

### Facebook Delete Endpoint Test
```bash
k6 run -e BASE_URL=http://localhost:8080 \
       -e AUTH_BASE_URL=http://localhost:8080 \
       stress-test.js
```

**Expected Results:**
- Initial: 10 users, < 100ms latency
- Peak: 200 users, < 500ms latency, < 1% errors

### All Services Test
```bash
$env:BASE_URL="http://localhost:8080"
$env:COMMUNITY_BASE_URL="http://localhost:8082"
$env:USER_BASE_URL="http://localhost:8083"

k6 run stress-test.js
```

## 📝 K6 CLI Commands

### Run test directly
```bash
k6 run script.js

# With parameters
k6 run -u 50 -d 5m script.js

# With environment variables
k6 run -e BASE_URL=http://api.local script.js

# Output to JSON file
k6 run -o json=results.json script.js
```

### Advanced Options
```bash
# Run with specific duration
k6 run -d 10m -u 100 script.js

# Set VUs and iterations
k6 run --vus 50 --iterations 1000 script.js

# Quiet mode (less output)
k6 run -q script.js

# Verbose mode (more details)
k6 run -v script.js
```

## 🔄 CI/CD Integration

### GitHub Actions Example
```yaml
- name: Run k6 Stress Test
  run: |
    k6 run \
      -u 100 \
      -d 5m \
      --out json=results.json \
      stress-test.js
  env:
    BASE_URL: ${{ env.API_URL }}

- name: Upload Results
  uses: actions/upload-artifact@v2
  with:
    name: k6-results
    path: results.json
```

## 📚 Resources

- [K6 Documentation](https://k6.io/docs)
- [K6 Best Practices](https://k6.io/blog/k6-best-practices)
- [Load Testing Guide](https://k6.io/docs/test-types/)

## 💡 Tips

1. **Start small**: Begin with smoke tests, then scale up
2. **Monitor services**: Watch logs/metrics during tests
3. **Baseline**: Run tests regularly to track performance trends
4. **Document**: Save results and analyze patterns
5. **Isolate**: Test individual services before full stack

---

**Last Updated:** April 17, 2026
