# Affiliate Network Platform - API Integration Guide

## Table of Contents

1. [Authentication](#authentication)
2. [Click Tracking](#click-tracking)
3. [S2S Conversion Tracking](#s2s-conversion-tracking)
4. [Offer Management API](#offer-management-api)
5. [Affiliate Management API](#affiliate-management-api)
6. [Reporting API](#reporting-api)
7. [Webhook/Postback Configuration](#webhook-postback-configuration)
8. [Error Handling](#error-handling)
9. [Rate Limiting](#rate-limiting)
10. [Code Examples](#code-examples)

---

## Authentication

The platform supports multiple authentication methods depending on the endpoint type:

### Public Endpoints (No Authentication)
- `GET /affiliate/click` - Click tracking redirect
- `GET /affiliate/postback` - S2S conversion postback (IP whitelist recommended)

### API Key Authentication
For programmatic access to management APIs.

**Header Format**:
```
X-API-Key: your_api_key_here
```

**How to Get API Key**:
1. Log in to dashboard
2. Navigate to Settings → API Keys
3. Generate new key with appropriate scopes
4. Store securely (cannot be retrieved after generation)

**API Key Scopes**:
- `affiliate:read` - Read affiliate data and reports
- `affiliate:write` - Create/update affiliate settings
- `advertiser:read` - Read offer and conversion data
- `advertiser:write` - Create/update offers, approve conversions
- `admin:all` - Full platform access

### JWT Token Authentication (Optional)
For user-based dashboard access.

**Header Format**:
```
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

---

## Click Tracking

### Endpoint
```
GET /affiliate/click
```

### Description
Redirects user to advertiser landing page while recording click for attribution.

### Query Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `offer_id` | string | Yes | Offer ID to promote (e.g., `off_101`) |
| `aff_id` | string | Yes | Your affiliate ID (e.g., `aff_888`) |
| `sub1` | string | No | Custom tracking parameter 1 (traffic source) |
| `sub2` | string | No | Custom tracking parameter 2 (campaign ID) |
| `sub3` | string | No | Custom tracking parameter 3 (ad group) |
| `sub4` | string | No | Custom tracking parameter 4 (creative) |
| `sub5` | string | No | Custom tracking parameter 5 (keyword/segment) |

### Response
- **HTTP 302 Found** - Redirect to advertiser landing page
- **HTTP 404 Not Found** - Invalid offer_id or aff_id
- **HTTP 429 Too Many Requests** - Rate limit exceeded
- **HTTP 410 Gone** - Offer expired or cap reached

### Example Request
```
https://track.yournetwork.com/affiliate/click?offer_id=off_101&aff_id=aff_888&sub1=facebook&sub2=campaign_spring2026&sub3=ad_set_women_25_34&sub4=banner_300x250&sub5=interest_fashion
```

### Example Response
```
HTTP/1.1 302 Found
Location: https://advertiser.com/product?click_id=c1k_a7f3d9e8b2c4a1b5&aff=888
```

### Click ID Format
The generated `click_id` is:
- 32 characters, URL-safe (alphanumeric + underscore)
- Cryptographically secure random
- Valid for 30 days (configurable per offer)
- Used for conversion attribution

### Implementation Notes
- **Performance**: P95 < 50ms, P99 < 100ms
- **Tracking Method**: Server-side (cookieless by design)
- **Cross-Device**: Full support via click_id in URL
- **Privacy**: GDPR-compliant, no client-side cookies required

---

## S2S Conversion Tracking

### Endpoint
```
POST /affiliate/postback
GET /affiliate/postback
```

### Description
Server-to-server conversion tracking endpoint for advertisers to report conversions.

### Authentication
**Recommended**: IP Whitelist
- Configure your server IPs in dashboard
- Platform accepts postbacks only from whitelisted IPs

**Alternative**: API Key or HMAC Signature
```
X-API-Key: your_advertiser_api_key
```

### Query Parameters (GET) or JSON Body (POST)

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `click_id` | string | Yes* | Click ID from original tracking link |
| `txid` or `transaction_id` | string | Yes | Your unique order/transaction ID (idempotency key) |
| `sale_amount` | decimal | No | Order total (required for CPS percentage offers) |
| `payout_override` | decimal | No | Custom commission amount (requires permission) |
| `goal_id` | string | No | Multi-event goal ID (e.g., `REGISTRATION`, `PURCHASE`) |
| `event_type` | string | No | Event type for multi-touch (default: `CONVERSION`) |
| `status` | integer | No | Pre-approval status: `1` = approved, `0` = pending (default) |

*Can be omitted if using probabilistic fingerprinting (fallback attribution)

### POST JSON Example
```json
{
  "click_id": "c1k_a7f3d9e8b2c4a1b5",
  "transaction_id": "ORDER-2026-09-08-12345",
  "sale_amount": 99.99,
  "currency": "USD",
  "goal_id": "PURCHASE",
  "customer_email_hash": "sha256_abc123...",
  "timestamp": "2026-09-08T14:32:00Z"
}
```

### GET URL Example
```
https://track.yournetwork.com/affiliate/postback?click_id=c1k_a7f3d9e8b2c4a1b5&txid=ORDER-2026-09-08-12345&sale_amount=99.99
```

### Response

**Success (200 OK)**:
```json
{
  "status": "success",
  "conversion_id": "conv_f9d8e7c6b5a4",
  "conversion_status": "PENDING",
  "payout": 9.99,
  "revenue": 12.99,
  "message": "Conversion recorded successfully"
}
```

**Error (400 Bad Request)**:
```json
{
  "status": "error",
  "error_code": "DUPLICATE_TRANSACTION",
  "message": "Transaction ID already processed",
  "transaction_id": "ORDER-2026-09-08-12345"
}
```

**Error (404 Not Found)**:
```json
{
  "status": "error",
  "error_code": "CLICK_NOT_FOUND",
  "message": "Invalid or expired click_id"
}
```

### Conversion Status Flow

```
PENDING (initial state, awaiting review)
   ↓
APPROVED (validated, will be paid) or REJECTED (invalid) or FRAUD_SUSPECTED (flagged)
```

**Auto-Approval Rules** (configurable per advertiser):
- Conversions from Gold+ tier affiliates: auto-approve after 3 days
- Conversions <$50 from Silver+ affiliates: auto-approve after 7 days
- All others: manual review or auto-approve after 7 days

### Anti-Fraud Detection

Conversions are automatically evaluated for:
- **CTIT (Click-To-Install-Time)**: Flags if <3 seconds (click injection)
- **Duplicate Detection**: Blocks same `(offer_id, txid)` combination
- **IP Analysis**: Detects datacenter/proxy IPs
- **User-Agent Analysis**: Flags bots and headless browsers
- **Conversion Rate Anomaly**: Flags sudden spikes in affiliate CR

**High-Risk Conversions**:
- Automatically marked `FRAUD_SUSPECTED`
- Held from payment pending manual review
- Affiliate notified with reason code

### Implementation Best Practices

**1. Fire Postback Immediately After Conversion**
```php
// Example: PHP
function sendConversion($clickId, $orderId, $amount) {
    $url = "https://track.yournetwork.com/affiliate/postback";
    $params = [
        'click_id' => $clickId,
        'txid' => $orderId,
        'sale_amount' => $amount
    ];

    $ch = curl_init($url . '?' . http_build_query($params));
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_TIMEOUT, 10);
    $response = curl_exec($ch);
    curl_close($ch);

    return json_decode($response, true);
}
```

**2. Store Click ID Server-Side**
```javascript
// Example: Node.js/Express
app.get('/landing', (req, res) => {
    const clickId = req.query.click_id;

    // Store in session or database
    req.session.affiliateClickId = clickId;

    res.render('product_page');
});

app.post('/purchase', async (req, res) => {
    const clickId = req.session.affiliateClickId;
    const orderId = req.body.order_id;
    const amount = req.body.total;

    // Send conversion postback
    await sendConversionPostback(clickId, orderId, amount);

    res.json({ success: true });
});
```

**3. Implement Retry Logic**
```python
# Example: Python with retries
import requests
from tenacity import retry, stop_after_attempt, wait_exponential

@retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, min=1, max=10))
def send_conversion(click_id, transaction_id, sale_amount):
    response = requests.post(
        'https://track.yournetwork.com/affiliate/postback',
        json={
            'click_id': click_id,
            'transaction_id': transaction_id,
            'sale_amount': sale_amount
        },
        timeout=10
    )
    response.raise_for_status()
    return response.json()
```

**4. Handle Refunds and Chargebacks**
```
POST /api/v1/conversions/{conversion_id}/reverse
X-API-Key: your_api_key

{
  "reason": "REFUND",
  "refund_amount": 99.99,
  "refund_date": "2026-09-15T10:00:00Z"
}
```

---

## Offer Management API

### List Available Offers

**Endpoint**: `GET /api/v1/offers`

**Authentication**: API Key with `affiliate:read` scope

**Query Parameters**:
- `status` (string): Filter by status (`ACTIVE`, `PAUSED`, `EXPIRED`)
- `category` (string): Filter by category
- `country` (string): Filter by allowed country (ISO code)
- `device_type` (integer): Filter by device type (1=mobile, 2=desktop)
- `min_payout` (decimal): Minimum payout amount
- `page` (integer): Page number (default: 1)
- `limit` (integer): Results per page (default: 50, max: 100)

**Example Request**:
```bash
curl -X GET 'https://api.yournetwork.com/api/v1/offers?status=ACTIVE&country=US&min_payout=10' \
  -H 'X-API-Key: your_api_key'
```

**Example Response**:
```json
{
  "status": "success",
  "data": [
    {
      "id": "off_101",
      "title": "E-Commerce Fashion Store - CPA",
      "description": "Earn $15 per sale. High-quality fashion products.",
      "payout_type": "CPA",
      "default_payout": 15.00,
      "cookie_duration": 30,
      "allowed_countries": ["US", "CA", "UK", "AU"],
      "allowed_devices": [1, 2],
      "daily_conversion_cap": 100,
      "status": "ACTIVE",
      "epc_7d": 2.35,
      "conversion_rate_7d": 1.8,
      "advertiser": {
        "id": "adv_123",
        "name": "Fashion Retailer Inc"
      },
      "creatives": [
        {
          "id": "cr_001",
          "type": "banner",
          "size": "300x250",
          "url": "https://cdn.yournetwork.com/creatives/banner_300x250.jpg"
        }
      ]
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 50,
    "total": 247,
    "pages": 5
  }
}
```

### Get Offer Details

**Endpoint**: `GET /api/v1/offers/{offer_id}`

**Example Request**:
```bash
curl -X GET 'https://api.yournetwork.com/api/v1/offers/off_101' \
  -H 'X-API-Key: your_api_key'
```

**Response**: Single offer object with full details including targeting rules, caps, and available creatives.

### Create Offer (Advertiser Only)

**Endpoint**: `POST /api/v1/offers`

**Authentication**: API Key with `advertiser:write` scope

**Request Body**:
```json
{
  "title": "Mobile Game Install Campaign",
  "landing_page_url": "https://app.advertiser.com/install?click_id={click_id}&aff={affiliate_id}",
  "payout_type": "CPI",
  "default_payout": 2.50,
  "default_revenue": 3.00,
  "cookie_duration": 7,
  "allowed_countries": ["US", "UK", "CA", "AU"],
  "allowed_devices": [1],
  "daily_conversion_cap": 500,
  "daily_revenue_cap": 1500.00,
  "fallback_offer_id": "off_999",
  "status": "ACTIVE"
}
```

**Response**:
```json
{
  "status": "success",
  "data": {
    "id": "off_456",
    "title": "Mobile Game Install Campaign",
    "status": "ACTIVE",
    "created_at": "2026-09-08T10:00:00Z"
  }
}
```

### Update Offer

**Endpoint**: `PUT /api/v1/offers/{offer_id}`

**Request Body**: Same as create, but all fields optional (partial update)

### Set Tier-Based Payout

**Endpoint**: `POST /api/v1/offers/{offer_id}/tier-payouts`

**Request Body**:
```json
{
  "tier": "VIP",
  "custom_payout": 20.00,
  "custom_revenue": 25.00
}
```

**Response**:
```json
{
  "status": "success",
  "message": "Tier payout configured for VIP affiliates"
}
```

### Set Affiliate-Specific Payout

**Endpoint**: `POST /api/v1/offers/{offer_id}/affiliate-payouts`

**Request Body**:
```json
{
  "affiliate_id": "aff_888",
  "custom_payout": 25.00,
  "custom_revenue": 30.00,
  "note": "Exclusive partnership rate"
}
```

---

## Affiliate Management API

### Get Affiliate Profile

**Endpoint**: `GET /api/v1/affiliates/me`

**Authentication**: API Key with `affiliate:read` scope

**Response**:
```json
{
  "status": "success",
  "data": {
    "id": "aff_888",
    "name": "John's Marketing Agency",
    "status": "ACTIVE",
    "tier": "GOLD",
    "payment_term": "NET_15",
    "min_payout_threshold": 100.00,
    "quality_score": 85,
    "statistics": {
      "total_clicks": 150000,
      "total_conversions": 2250,
      "total_earnings": 33750.00,
      "pending_balance": 1250.00,
      "approved_balance": 850.00,
      "lifetime_earnings": 45000.00
    },
    "next_payment": {
      "amount": 850.00,
      "scheduled_date": "2026-09-20"
    },
    "created_at": "2025-01-15T08:00:00Z"
  }
}
```

### Generate Tracking Link

**Endpoint**: `POST /api/v1/links`

**Authentication**: API Key with `affiliate:read` scope

**Request Body**:
```json
{
  "offer_id": "off_101",
  "sub1": "facebook_campaign_001",
  "sub2": "ad_set_xyz",
  "sub3": "banner_300x250",
  "sub4": "audience_women_25_34",
  "sub5": "landing_variant_A"
}
```

**Response**:
```json
{
  "status": "success",
  "data": {
    "tracking_url": "https://track.yournetwork.com/affiliate/click?offer_id=off_101&aff_id=aff_888&sub1=facebook_campaign_001&sub2=ad_set_xyz&sub3=banner_300x250&sub4=audience_women_25_34&sub5=landing_variant_A",
    "short_url": "https://track.yournetwork.com/c/a8f3d9e"
  }
}
```

### Update Payment Settings

**Endpoint**: `PUT /api/v1/affiliates/me/payment`

**Request Body**:
```json
{
  "payment_method": "PAYPAL",
  "payment_email": "affiliate@example.com",
  "min_payout_threshold": 50.00
}
```

### Configure Postback URL

**Endpoint**: `PUT /api/v1/affiliates/me/postback`

**Request Body**:
```json
{
  "postback_url_template": "https://mytracker.com/postback?click_id={click_id}&payout={payout}&status={status}&txid={txid}&sub1={sub1}&sub2={sub2}"
}
```

**Available Macros**:
- `{click_id}` - Original click ID
- `{payout}` - Affiliate commission amount
- `{revenue}` - Advertiser payment amount
- `{txid}` - Transaction ID
- `{status}` - Conversion status (approved, pending, rejected)
- `{offer_id}` - Offer ID
- `{affiliate_id}` - Your affiliate ID
- `{sale_amount}` - Order total (if applicable)
- `{sub1}` through `{sub5}` - Your custom tracking parameters
- `{timestamp}` - Conversion timestamp (ISO 8601)

---

## Reporting API

### Performance Report

**Endpoint**: `GET /api/v1/reports/performance`

**Authentication**: API Key

**Query Parameters**:
- `start_date` (date): Start date (YYYY-MM-DD)
- `end_date` (date): End date (YYYY-MM-DD)
- `group_by` (string): Grouping dimension (`date`, `offer`, `sub1`, `sub2`, `sub3`, `country`, `device`)
- `offer_id` (string): Filter by specific offer
- `timezone` (string): Timezone for date grouping (default: UTC)

**Example Request**:
```bash
curl -X GET 'https://api.yournetwork.com/api/v1/reports/performance?start_date=2026-09-01&end_date=2026-09-08&group_by=offer' \
  -H 'X-API-Key: your_api_key'
```

**Example Response**:
```json
{
  "status": "success",
  "data": [
    {
      "dimension": "off_101",
      "dimension_name": "E-Commerce Fashion Store",
      "clicks": 5000,
      "conversions": 75,
      "conversion_rate": 1.5,
      "approved_conversions": 68,
      "pending_conversions": 5,
      "rejected_conversions": 2,
      "earnings": 1125.00,
      "epc": 0.225,
      "revenue": 1425.00
    },
    {
      "dimension": "off_202",
      "dimension_name": "Mobile Game Install",
      "clicks": 3000,
      "conversions": 120,
      "conversion_rate": 4.0,
      "approved_conversions": 115,
      "pending_conversions": 3,
      "rejected_conversions": 2,
      "earnings": 287.50,
      "epc": 0.096,
      "revenue": 345.00
    }
  ],
  "summary": {
    "total_clicks": 8000,
    "total_conversions": 195,
    "total_earnings": 1412.50,
    "average_epc": 0.177
  }
}
```

### Sub-ID Analytics

**Endpoint**: `GET /api/v1/reports/sub-ids`

**Query Parameters**:
- `start_date`, `end_date` (required)
- `sub_level` (string): Which sub-ID to analyze (`sub1`, `sub2`, `sub3`, `sub4`, `sub5`)
- `parent_sub1` (string): Filter by parent sub1 value (for drilling down)
- `min_clicks` (integer): Minimum clicks threshold

**Example Request**:
```bash
curl -X GET 'https://api.yournetwork.com/api/v1/reports/sub-ids?start_date=2026-09-01&end_date=2026-09-08&sub_level=sub1&min_clicks=100' \
  -H 'X-API-Key: your_api_key'
```

**Example Response**:
```json
{
  "status": "success",
  "data": [
    {
      "sub_id": "facebook",
      "clicks": 3500,
      "conversions": 52,
      "conversion_rate": 1.49,
      "earnings": 780.00,
      "epc": 0.223,
      "roi_percent": 156.3
    },
    {
      "sub_id": "google_ads",
      "clicks": 2200,
      "conversions": 44,
      "conversion_rate": 2.0,
      "earnings": 660.00,
      "epc": 0.300,
      "roi_percent": 183.7
    }
  ]
}
```

### Conversion List

**Endpoint**: `GET /api/v1/conversions`

**Query Parameters**:
- `start_date`, `end_date`
- `status` (string): Filter by status (`PENDING`, `APPROVED`, `REJECTED`, `FRAUD_SUSPECTED`)
- `offer_id` (string): Filter by offer
- `page`, `limit`: Pagination

**Example Response**:
```json
{
  "status": "success",
  "data": [
    {
      "id": "conv_f9d8e7c6b5a4",
      "click_id": "c1k_a7f3d9e8b2c4a1b5",
      "transaction_id": "ORDER-2026-09-08-12345",
      "offer_id": "off_101",
      "offer_name": "E-Commerce Fashion Store",
      "payout": 15.00,
      "sale_amount": 99.99,
      "status": "APPROVED",
      "ctit_seconds": 3600,
      "created_at": "2026-09-08T14:32:00Z",
      "approved_at": "2026-09-10T08:00:00Z"
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 50,
    "total": 1250
  }
}
```

### Payment History

**Endpoint**: `GET /api/v1/payments`

**Example Response**:
```json
{
  "status": "success",
  "data": [
    {
      "id": "inv_20260901_01",
      "amount": 2150.00,
      "conversion_count": 143,
      "payment_method": "PAYPAL",
      "status": "PAID",
      "transaction_id": "PAYPAL-TX-ABC123",
      "created_at": "2026-09-01T10:00:00Z",
      "paid_at": "2026-09-02T14:30:00Z"
    }
  ]
}
```

---

## Webhook/Postback Configuration

### Affiliate Postback Setup

Affiliates can configure a postback URL to receive real-time conversion notifications in their own tracking system.

**How It Works**:
1. Affiliate sets postback URL template in dashboard or via API
2. When conversion occurs, network fires HTTP request to affiliate's URL
3. Macros in URL template are replaced with actual values
4. Delivery guaranteed with 3 retry attempts

**Example Configuration**:
```
https://mytracker.com/postback?click_id={click_id}&payout={payout}&status={status}&txid={txid}&offer={offer_id}&sub1={sub1}&sub2={sub2}&sub3={sub3}
```

**Example Fired Postback**:
```
https://mytracker.com/postback?click_id=c1k_a7f3d9e8b2c4a1b5&payout=15.00&status=approved&txid=ORDER-2026-09-08-12345&offer=off_101&sub1=facebook&sub2=campaign_001&sub3=ad_set_xyz
```

**Retry Logic**:
- Attempt 1: Immediate
- Attempt 2: After 10 seconds
- Attempt 3: After 60 seconds
- Timeout: 10 seconds per attempt

### Advertiser Webhooks

Advertisers can subscribe to events via webhooks.

**Available Events**:
- `conversion.created` - New conversion recorded
- `conversion.approved` - Conversion approved for payment
- `conversion.rejected` - Conversion rejected
- `affiliate.applied` - New affiliate requested access to your offer

**Webhook Payload Example** (`conversion.approved`):
```json
{
  "event": "conversion.approved",
  "timestamp": "2026-09-10T08:00:00Z",
  "data": {
    "conversion_id": "conv_f9d8e7c6b5a4",
    "offer_id": "off_101",
    "affiliate_id": "aff_888",
    "transaction_id": "ORDER-2026-09-08-12345",
    "payout": 15.00,
    "revenue": 18.00,
    "sale_amount": 99.99,
    "created_at": "2026-09-08T14:32:00Z",
    "approved_at": "2026-09-10T08:00:00Z"
  }
}
```

**Webhook Security**:
- HMAC signature in `X-Webhook-Signature` header
- Verify signature to ensure authenticity

**Signature Verification** (Python):
```python
import hmac
import hashlib

def verify_webhook(payload, signature, secret):
    expected = hmac.new(
        secret.encode(),
        payload.encode(),
        hashlib.sha256
    ).hexdigest()
    return hmac.compare_digest(expected, signature)
```

---

## Error Handling

### HTTP Status Codes

| Code | Meaning | Action |
|------|---------|--------|
| 200 | Success | Request processed successfully |
| 302 | Redirect | Click tracking redirect to landing page |
| 400 | Bad Request | Check request parameters |
| 401 | Unauthorized | Check API key |
| 403 | Forbidden | Insufficient permissions |
| 404 | Not Found | Resource doesn't exist |
| 409 | Conflict | Duplicate transaction ID |
| 429 | Too Many Requests | Rate limit exceeded, retry with backoff |
| 500 | Server Error | Contact support if persistent |

### Error Response Format

```json
{
  "status": "error",
  "error_code": "DUPLICATE_TRANSACTION",
  "message": "Transaction ID already processed",
  "details": {
    "transaction_id": "ORDER-2026-09-08-12345",
    "original_conversion_id": "conv_abc123"
  },
  "request_id": "req_xyz789"
}
```

### Common Error Codes

| Code | Description | Resolution |
|------|-------------|------------|
| `CLICK_NOT_FOUND` | Invalid or expired click_id | Check attribution window (30 days) |
| `DUPLICATE_TRANSACTION` | Transaction ID already processed | Verify txid is unique per conversion |
| `OFFER_NOT_FOUND` | Invalid offer_id | Check offer still active |
| `AFFILIATE_NOT_FOUND` | Invalid aff_id | Verify affiliate ID correct |
| `OFFER_CAP_REACHED` | Daily conversion cap exceeded | Offer automatically paused |
| `ATTRIBUTION_EXPIRED` | Click older than attribution window | Conversion rejected |
| `FRAUD_SUSPECTED` | Failed anti-fraud checks | Contact support for review |
| `RATE_LIMIT_EXCEEDED` | Too many requests | Implement exponential backoff |

---

## Rate Limiting

### Limits by Endpoint Type

| Endpoint Type | Limit | Window |
|---------------|-------|--------|
| Click Tracking | 10,000 req/min per IP | 1 minute |
| S2S Postback | 1,000 req/min per advertiser | 1 minute |
| Management API | 1,000 req/hour per API key | 1 hour |
| Reporting API | 100 req/hour per API key | 1 hour |

### Rate Limit Headers

```
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 847
X-RateLimit-Reset: 1693838400
```

### Best Practices

**1. Implement Exponential Backoff**
```python
import time
import random

def request_with_backoff(func, max_retries=5):
    for attempt in range(max_retries):
        try:
            return func()
        except RateLimitError as e:
            if attempt == max_retries - 1:
                raise
            wait_time = (2 ** attempt) + random.uniform(0, 1)
            time.sleep(wait_time)
```

**2. Batch Requests When Possible**
```javascript
// Instead of 100 individual requests:
for (let conversion of conversions) {
    await sendConversion(conversion);
}

// Batch into single request:
await sendConversions(conversions);
```

**3. Cache Offer Data**
```
// Fetch offers once, cache for 15 minutes
// Reduces API calls from 100/min to 4/hour
```

---

## Code Examples

### PHP Integration

**Click Tracking**:
```php
<?php
function generateAffiliateLink($offerId, $affId, $subIds = []) {
    $baseUrl = 'https://track.yournetwork.com/affiliate/click';
    $params = [
        'offer_id' => $offerId,
        'aff_id' => $affId
    ];

    foreach ($subIds as $key => $value) {
        $params[$key] = $value;
    }

    return $baseUrl . '?' . http_build_query($params);
}

// Usage
$link = generateAffiliateLink('off_101', 'aff_888', [
    'sub1' => 'blog_post_123',
    'sub2' => 'sidebar_banner'
]);

echo '<a href="' . $link . '">Shop Now</a>';
?>
```

**Conversion Tracking**:
```php
<?php
function sendConversion($clickId, $orderId, $amount) {
    $url = 'https://track.yournetwork.com/affiliate/postback';
    $data = [
        'click_id' => $clickId,
        'txid' => $orderId,
        'sale_amount' => $amount
    ];

    $ch = curl_init();
    curl_setopt($ch, CURLOPT_URL, $url . '?' . http_build_query($data));
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_TIMEOUT, 10);

    $response = curl_exec($ch);
    $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);

    if ($httpCode == 200) {
        return json_decode($response, true);
    } else {
        error_log("Conversion postback failed: " . $response);
        return false;
    }
}

// Usage: After order completion
$clickId = $_SESSION['affiliate_click_id'];
if ($clickId) {
    $result = sendConversion($clickId, $order->id, $order->total);
}
?>
```

### Python Integration

```python
import requests
from typing import Optional, Dict

class AffiliateNetworkAPI:
    def __init__(self, api_key: str, base_url: str = 'https://api.yournetwork.com'):
        self.api_key = api_key
        self.base_url = base_url
        self.session = requests.Session()
        self.session.headers.update({'X-API-Key': api_key})

    def get_offers(self, status: str = 'ACTIVE', country: Optional[str] = None) -> list:
        params = {'status': status}
        if country:
            params['country'] = country

        response = self.session.get(f'{self.base_url}/api/v1/offers', params=params)
        response.raise_for_status()
        return response.json()['data']

    def send_conversion(self, click_id: str, transaction_id: str, sale_amount: float) -> Dict:
        data = {
            'click_id': click_id,
            'transaction_id': transaction_id,
            'sale_amount': sale_amount
        }

        response = self.session.post(
            f'{self.base_url}/affiliate/postback',
            json=data,
            timeout=10
        )
        response.raise_for_status()
        return response.json()

    def get_performance_report(self, start_date: str, end_date: str, group_by: str = 'date') -> Dict:
        params = {
            'start_date': start_date,
            'end_date': end_date,
            'group_by': group_by
        }

        response = self.session.get(
            f'{self.base_url}/api/v1/reports/performance',
            params=params
        )
        response.raise_for_status()
        return response.json()

# Usage
api = AffiliateNetworkAPI(api_key='your_api_key_here')

# Get active offers
offers = api.get_offers(status='ACTIVE', country='US')
for offer in offers:
    print(f"{offer['title']}: ${offer['default_payout']} payout")

# Send conversion
result = api.send_conversion(
    click_id='c1k_abc123',
    transaction_id='ORDER-123',
    sale_amount=99.99
)
print(f"Conversion status: {result['conversion_status']}")

# Get performance report
report = api.get_performance_report(
    start_date='2026-09-01',
    end_date='2026-09-08',
    group_by='offer'
)
print(f"Total earnings: ${report['summary']['total_earnings']}")
```

### Node.js Integration

```javascript
const axios = require('axios');

class AffiliateNetworkClient {
    constructor(apiKey, baseUrl = 'https://api.yournetwork.com') {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.client = axios.create({
            baseURL: baseUrl,
            headers: { 'X-API-Key': apiKey },
            timeout: 10000
        });
    }

    async getOffers(filters = {}) {
        const response = await this.client.get('/api/v1/offers', { params: filters });
        return response.data.data;
    }

    async sendConversion(clickId, transactionId, saleAmount) {
        const response = await this.client.post('/affiliate/postback', {
            click_id: clickId,
            transaction_id: transactionId,
            sale_amount: saleAmount
        });
        return response.data;
    }

    async getPerformanceReport(startDate, endDate, groupBy = 'date') {
        const response = await this.client.get('/api/v1/reports/performance', {
            params: {
                start_date: startDate,
                end_date: endDate,
                group_by: groupBy
            }
        });
        return response.data;
    }

    generateTrackingLink(offerId, affId, subIds = {}) {
        const params = new URLSearchParams({
            offer_id: offerId,
            aff_id: affId,
            ...subIds
        });
        return `https://track.yournetwork.com/affiliate/click?${params}`;
    }
}

// Usage
const api = new AffiliateNetworkClient('your_api_key');

// Express.js middleware to track affiliate clicks
app.get('/product/:id', async (req, res) => {
    const clickId = req.query.click_id;
    if (clickId) {
        req.session.affiliateClickId = clickId;
    }

    res.render('product', { productId: req.params.id });
});

// Send conversion after purchase
app.post('/checkout/complete', async (req, res) => {
    const clickId = req.session.affiliateClickId;
    const orderId = req.body.order_id;
    const total = req.body.total;

    if (clickId) {
        try {
            const result = await api.sendConversion(clickId, orderId, total);
            console.log('Conversion tracked:', result);
        } catch (error) {
            console.error('Failed to track conversion:', error.message);
        }
    }

    res.json({ success: true });
});
```

### Ruby Integration

```ruby
require 'httparty'

class AffiliateNetworkAPI
  include HTTParty
  base_uri 'https://api.yournetwork.com'

  def initialize(api_key)
    @api_key = api_key
    @headers = { 'X-API-Key' => api_key }
  end

  def get_offers(filters = {})
    response = self.class.get('/api/v1/offers',
      headers: @headers,
      query: filters
    )
    response['data']
  end

  def send_conversion(click_id, transaction_id, sale_amount)
    response = self.class.post('/affiliate/postback',
      headers: @headers,
      body: {
        click_id: click_id,
        transaction_id: transaction_id,
        sale_amount: sale_amount
      }.to_json,
      headers: @headers.merge('Content-Type' => 'application/json')
    )
    response.parsed_response
  end

  def generate_tracking_link(offer_id, aff_id, sub_ids = {})
    params = { offer_id: offer_id, aff_id: aff_id }.merge(sub_ids)
    query = URI.encode_www_form(params)
    "https://track.yournetwork.com/affiliate/click?#{query}"
  end
end

# Usage
api = AffiliateNetworkAPI.new('your_api_key')

# Get offers
offers = api.get_offers(status: 'ACTIVE', country: 'US')
offers.each do |offer|
  puts "#{offer['title']}: $#{offer['default_payout']} payout"
end

# Send conversion (in Rails controller)
class OrdersController < ApplicationController
  def create
    @order = Order.create(order_params)

    if @order.persisted?
      click_id = session[:affiliate_click_id]
      if click_id
        api = AffiliateNetworkAPI.new(ENV['AFFILIATE_API_KEY'])
        api.send_conversion(click_id, @order.id, @order.total)
      end

      render json: { success: true }
    end
  end
end
```

---

## Testing & Sandbox

### Test Mode

Use test API keys to simulate conversions without real money:

**Test API Key**: `test_api_key_abc123xyz`

**Test Click ID**: Any click_id starting with `test_` will be accepted

**Test Postback**:
```bash
curl -X POST 'https://api.yournetwork.com/affiliate/postback' \
  -H 'X-API-Key: test_api_key_abc123xyz' \
  -d 'click_id=test_click_123&txid=TEST_ORDER_001&sale_amount=50.00'
```

### Postback Testing Tool

Verify your postback URL receives data correctly:

**Dashboard**: Settings → Postback Testing

Enter test values and click "Send Test Postback" to fire a test notification to your configured URL.

---

## Support

- **Documentation**: https://docs.yournetwork.com
- **API Status**: https://status.yournetwork.com
- **Support Email**: api-support@yournetwork.com
- **Developer Slack**: https://slack.yournetwork.com

---

**Document Version**: 1.0
**Last Updated**: 2026-09-08
**API Version**: v1
