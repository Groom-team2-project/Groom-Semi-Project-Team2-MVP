import http from 'k6/http';
import exec from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const eventId = __ENV.EVENT_ID;
const strategy = __ENV.LOCK_STRATEGY || 'unknown';
const iterations = Number(__ENV.ITERATIONS || 500);
const vus = Number(__ENV.VUS || 100);
const memberIdBase = Number(__ENV.MEMBER_ID_BASE || 1000000);

const successfulRequests = new Counter('successful_requests');
const lockTimeouts = new Counter('lock_timeouts');
const unexpectedFailures = new Counter('unexpected_failures');
const participateDuration = new Trend('participate_duration', true);

export const options = {
  discardResponseBodies: true,
  summaryTrendStats: ['avg', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    event_participation: {
      executor: 'shared-iterations',
      vus,
      iterations,
      maxDuration: '2m',
    },
  },
  tags: {
    lock_strategy: strategy,
  },
};

export default function () {
  const memberId = memberIdBase + exec.scenario.iterationInTest;
  const response = http.post(
    `${baseUrl}/api/performance/events/${eventId}/participate?memberId=${memberId}`,
    null,
    {
      tags: {
        name: 'event-participate',
        lock_strategy: strategy,
      },
      timeout: '15s',
    },
  );

  participateDuration.add(response.timings.duration);

  if (response.status === 200) {
    successfulRequests.add(1);
    return;
  }

  if (response.status === 409) {
    lockTimeouts.add(1);
    return;
  }

  unexpectedFailures.add(1);
}

export function handleSummary(data) {
  const output = __ENV.RESULTS_FILE || `/results/${strategy}-summary.json`;
  const duration = data.metrics.http_req_duration?.values || {};
  const requests = data.metrics.http_reqs?.values || {};
  const success = data.metrics.successful_requests?.values?.count || 0;
  const timeouts = data.metrics.lock_timeouts?.values?.count || 0;
  const unexpected = data.metrics.unexpected_failures?.values?.count || 0;

  const summary = [
    '',
    `strategy=${strategy}`,
    `requests=${requests.count || 0}`,
    `success=${success}`,
    `lock_timeouts=${timeouts}`,
    `unexpected_failures=${unexpected}`,
    `throughput=${(requests.rate || 0).toFixed(2)} req/s`,
    `avg=${(duration.avg || 0).toFixed(2)} ms`,
    `p95=${(duration['p(95)'] || 0).toFixed(2)} ms`,
    `p99=${(duration['p(99)'] || 0).toFixed(2)} ms`,
    '',
  ].join('\n');

  return {
    stdout: summary,
    [output]: JSON.stringify(data, null, 2),
  };
}
