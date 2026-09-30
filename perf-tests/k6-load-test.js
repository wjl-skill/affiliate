import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// =====================================================================
// 自定义高精分位延时与业务度量指标
// =====================================================================
const clickDurationTrend = new Trend('affiliate_click_latency', true);
const postbackDurationTrend = new Trend('affiliate_postback_latency', true);
const rtbBidDurationTrend = new Trend('rtb_bid_latency', true);

const successfulRequests = new Counter('successful_requests');
const errorRate = new Rate('request_error_rate');

// =====================================================================
// 压测场景配置 (针对万级 QPS 负载与分位 SLA 断言)
// =====================================================================
export const options = {
  scenarios: {
    // 场景 1: 点击流热路径万级 QPS 压力阶梯测试
    click_traffic: {
      executor: 'ramping-arrival-rate',
      startRate: 500,
      timeUnit: '1s',
      preAllocatedVUs: 200,
      maxVUs: 2000,
      stages: [
        { duration: '30s', target: 2000 },  // 预热升温至 2,000 QPS
        { duration: '1m',  target: 10000 }, // 阶梯拉升至 10,000 QPS
        { duration: '2m',  target: 10000 }, // 维持 10,000 QPS 高峰峰值
        { duration: '30s', target: 0 },     // 降温冷却
      ],
      exec: 'testClickFlow',
    },

    // 场景 2: 广告主 S2S 转化回传与分布式防重锁压测
    postback_traffic: {
      executor: 'ramping-vus',
      startVUs: 10,
      stages: [
        { duration: '30s', target: 100 },
        { duration: '1m',  target: 500 },
        { duration: '30s', target: 0 },
      ],
      exec: 'testPostbackFlow',
    },

    // 场景 3: RTB 实时竞价出价 (OpenRTB 2.5) 极速响应压测
    rtb_bid_traffic: {
      executor: 'constant-arrival-rate',
      rate: 3000,
      timeUnit: '1s',
      duration: '2m',
      preAllocatedVUs: 100,
      maxVUs: 1000,
      exec: 'testRtbBidding',
    },
  },

  thresholds: {
    'http_req_duration': ['p(95)<15', 'p(99)<25'], // 全局 HTTP 响应 P95 < 15ms, P99 < 25ms
    'affiliate_click_latency': ['p(95)<10', 'p(99)<20'], // 点击网关 P99 < 20ms
    'rtb_bid_latency': ['p(95)<5', 'p(99)<15'], // RTB 竞价出价 P99 < 15ms
    'request_error_rate': ['rate<0.001'], // 失败率严格控制在 0.1% 以内
  },
};

const BASE_URL = __ENV.TARGET_URL || 'http://localhost:8080';

/**
 * 场景 1: 点击流热路径测试 (/affiliate/click)
 */
export function testClickFlow() {
  const randomAff = Math.floor(Math.random() * 50) + 1;
  const randomOffer = Math.floor(Math.random() * 10) + 100;
  const ip = `192.168.${Math.floor(Math.random() * 254) + 1}.${Math.floor(Math.random() * 254) + 1}`;

  const params = {
    headers: {
      'User-Agent': 'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15',
      'X-Forwarded-For': ip,
    },
    redirects: 0, // 不自动跟踪 302，测量真实网关跳转延时
  };

  const url = `${BASE_URL}/affiliate/click?offer_id=${randomOffer}&aff_id=${randomAff}&sub1=k6_stress&sub2=perf`;
  const res = http.get(url, params);

  clickDurationTrend.add(res.timings.duration);
  const success = res.status === 302 || res.status === 200;
  check(res, {
    'click status is 302 or 200': () => success,
  });

  if (success) {
    successfulRequests.add(1);
    errorRate.add(0);
  } else {
    errorRate.add(1);
  }
}

/**
 * 场景 2: S2S 服务端转化回传测试 (/affiliate/postback)
 */
export function testPostbackFlow() {
  const txId = `tx_k6_${__VU}_${__ITER}_${Date.now()}`;
  const amount = (Math.random() * 100 + 10).toFixed(2);
  const clickId = `c_test_${Math.floor(Math.random() * 1000)}`;

  const url = `${BASE_URL}/affiliate/postback?click_id=${clickId}&txid=${txId}&sale_amount=${amount}`;
  const res = http.get(url);

  postbackDurationTrend.add(res.timings.duration);
  const success = res.status === 200;
  check(res, {
    'postback status is 200': () => success,
  });

  if (success) {
    successfulRequests.add(1);
    errorRate.add(0);
  } else {
    errorRate.add(1);
  }
}

/**
 * 场景 3: RTB 极速竞价测试 (/rtb/openrtb/2.5/bid)
 */
export function testRtbBidding() {
  const payload = JSON.stringify({
    id: `req_${__VU}_${__ITER}`,
    imp: [
      {
        id: "imp_1",
        banner: {
          w: 300,
          h: 250,
          pos: 1
        },
        bidfloor: 0.50
      }
    ],
    device: {
      ip: "203.0.113.195",
      ua: "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
      geo: {
        country: "USA"
      }
    },
    tmax: 50
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  const res = http.post(`${BASE_URL}/rtb/openrtb/2.5/bid`, payload, params);
  rtbBidDurationTrend.add(res.timings.duration);

  const success = res.status === 200 || res.status === 204;
  check(res, {
    'rtb bid status is 200 or 204': () => success,
  });

  if (success) {
    successfulRequests.add(1);
    errorRate.add(0);
  } else {
    errorRate.add(1);
  }
}
