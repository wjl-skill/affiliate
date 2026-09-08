# Affiliate Network Platform - Business Requirements Document

## Executive Summary

This document defines the business requirements for a commercial-grade affiliate marketing network platform, modeled after industry leaders including CJ Affiliate, ShareASale, Rakuten Advertising, Impact, and Awin. The platform enables advertisers to create performance-based offers and publishers/affiliates to promote them, with automated tracking, attribution, fraud prevention, and payment settlement.

## 1. Core User Personas

### 1.1 Network Operator (Platform Owner)
- **Needs**: Oversee entire ecosystem, ensure compliance, facilitate relationships, manage payments
- **Goals**: Maximize transaction volume, maintain quality standards, minimize fraud
- **Key Metrics**: GMV (Gross Merchandise Value), network take rate, fraud rate, partner satisfaction

### 1.2 Advertiser (Merchant)
- **Needs**: Access quality affiliates, track performance, control costs, prevent fraud
- **Goals**: Acquire customers at target CPA/ROAS, scale profitable channels
- **Key Metrics**: CPA, ROAS, customer LTV by source, fraud/refund rate

### 1.3 Publisher/Affiliate (Traffic Source)
- **Needs**: Access profitable offers, track conversions, optimize campaigns, get paid reliably
- **Goals**: Maximize EPC and total earnings, diversify offer portfolio
- **Key Metrics**: EPC (Earnings Per Click), conversion rate, approval rate, payment consistency

## 2. Functional Requirements

### 2.1 Affiliate Partner Management

#### FR-2.1.1: Affiliate Registration and Onboarding
- **Description**: Publishers can apply to join the network with identity verification
- **Business Rules**:
  - Require business/individual information: name, tax ID, payment details, traffic sources
  - Manual approval workflow with compliance review
  - Email verification and phone validation
  - Website/social media property verification
  - Background check against fraud blacklists
- **Success Criteria**: 95% of legitimate applications processed within 24 hours

#### FR-2.1.2: Tiered Commission Structure
- **Description**: Support performance-based affiliate tiers with automatic upgrades
- **Tier Definitions**:
  - **STANDARD**: 0-10 conversions/month (baseline commission)
  - **SILVER**: 11-25 conversions/month (+20% commission boost)
  - **GOLD**: 26-50 conversions/month (+40% commission boost)
  - **VIP**: 51+ conversions/month (+60% commission boost + dedicated manager)
- **Business Rules**:
  - Tier evaluation runs monthly on trailing 30-day performance
  - Tier benefits apply prospectively to new conversions
  - Once achieved, tier status maintained for 90 days even if performance declines
  - VIP tier receives priority support and exclusive private offers
- **Success Criteria**: 30% of active affiliates reach Silver+ tier within 6 months

#### FR-2.1.3: Affiliate Status Management
- **Description**: Lifecycle management with suspension and reactivation capabilities
- **States**:
  - **PENDING**: Application submitted, awaiting approval
  - **ACTIVE**: Approved and can promote offers
  - **SUSPENDED**: Temporarily blocked due to policy violation or payment issue
  - **BANNED**: Permanently terminated for fraud or severe violation
- **Business Rules**:
  - Suspended affiliates cannot generate new clicks but existing conversions still process
  - Banned affiliates forfeit unpaid commissions under $100
  - Affiliates can appeal suspensions within 30 days
- **Success Criteria**: <2% false positive suspension rate

### 2.2 Offer Management

#### FR-2.2.1: Offer Creation and Configuration
- **Description**: Advertisers create performance offers with detailed targeting and payout rules
- **Required Fields**:
  - Offer title and description
  - Landing page URL template (with macro support: `{click_id}`, `{sub1}`, `{affiliate_id}`)
  - Payout model: CPA, CPL, CPS, CPI, CPC, Hybrid
  - Default payout amount (USD) and default revenue (what advertiser pays)
  - Cookie duration (1-90 days, default 30)
- **Optional Fields**:
  - Geographic targeting (allow/block countries)
  - Device targeting (mobile, desktop, tablet)
  - Traffic source restrictions (no incentive, no toolbar, no coupon sites)
  - Daily conversion cap (stop accepting conversions after N/day)
  - Daily revenue cap (budget limit)
  - Fallback offer ID (redirect when cap reached)
  - Start/end dates (campaign scheduling)
- **Success Criteria**: Advertisers can launch new offer in <5 minutes

#### FR-2.2.2: Tiered Payout Rules (VIP Pricing)
- **Description**: Support custom payout rates for specific affiliates or tier levels
- **Use Cases**:
  - Offer baseline payout: $15 CPA
  - VIP tier override: $20 CPA (33% higher to incentivize top performers)
  - Specific affiliate override: Affiliate #888 gets $25 CPA (exclusive partnership)
- **Business Rules**:
  - System checks for affiliate-specific rate first
  - Falls back to tier-based rate if defined
  - Falls back to default offer payout if no overrides
  - Payout changes apply only to conversions after effective date
- **Success Criteria**: 15% of conversions use tier/custom pricing

#### FR-2.2.3: Conversion Caps and Traffic Shaping
- **Description**: Automatically manage offer capacity with intelligent fallback
- **Cap Types**:
  - **Daily conversion cap**: Max conversions accepted per calendar day (advertiser timezone)
  - **Hourly cap**: Smooth distribution (e.g., 240/day = 10/hour max)
  - **Per-affiliate cap**: Limit single affiliate volume (prevent over-concentration)
  - **Global budget cap**: Stop when total spend reaches threshold
- **Business Rules**:
  - Check caps BEFORE recording conversion (atomic operation)
  - When cap reached: redirect clicks to `fallbackOfferId` if specified
  - If no fallback: show generic landing page or return HTTP 410 (Gone)
  - Caps reset at midnight in advertiser's configured timezone
  - Buffer at 95% of actual cap to account for concurrent requests
- **Success Criteria**: Zero over-cap conversions, <1% legitimate traffic rejected

#### FR-2.2.4: Offer Approval Workflow
- **Description**: Publishers request access to private/restricted offers
- **Business Rules**:
  - Public offers: auto-approved for all ACTIVE affiliates
  - Private offers: require explicit advertiser approval
  - Application can include promotional plan description
  - Advertisers can pre-approve by affiliate tier or traffic quality score
  - Auto-rejection for affiliates with <70% quality score
- **Success Criteria**: 80% of applications processed within 12 hours

### 2.3 Click Tracking and Attribution

#### FR-2.3.1: Click Tracking Endpoint
- **Description**: Generate unique click IDs and redirect to advertiser landing page
- **Endpoint**: `GET /affiliate/click?offer_id={id}&aff_id={id}&sub1={val}&sub2={val}...`
- **Processing Flow**:
  1. Validate offer is active and affiliate is approved
  2. Check geographic/device targeting (reject if mismatch)
  3. Apply IP-based flood control (max 60 clicks/IP/minute)
  4. Check daily conversion cap status (redirect to fallback if reached)
  5. Generate cryptographically secure `click_id` (32 chars, URL-safe)
  6. Extract visitor attributes: IP, User-Agent, Country, Device Type
  7. Store click session with 30-day expiration
  8. Replace landing page macros: `{click_id}`, `{sub1}`-`{sub5}`, `{affiliate_id}`
  9. Return HTTP 302 redirect to final landing page
- **Performance Target**: P95 < 50ms, P99 < 100ms
- **Success Criteria**: >99.9% uptime, zero data loss

#### FR-2.3.2: Sub-ID Multi-Dimensional Tracking
- **Description**: Support 5 custom tracking parameters for granular campaign optimization
- **Parameters**: `sub1`, `sub2`, `sub3`, `sub4`, `sub5` (each up to 128 chars)
- **Common Usage Patterns**:
  - `sub1`: Traffic source (Facebook, Google, Email, Blog)
  - `sub2`: Campaign ID or name
  - `sub3`: Ad group or placement
  - `sub4`: Creative variant
  - `sub5`: Keyword or audience segment
- **Business Rules**:
  - Sub-IDs pass through from click → conversion → postback
  - URL-encoded values preserved exactly (no truncation)
  - Empty/missing sub-IDs stored as NULL
  - Reporting supports filtering and grouping by any sub-ID
- **Success Criteria**: 70% of affiliates use at least sub1, 40% use sub1-sub3

#### FR-2.3.3: Attribution Window Management
- **Description**: Configure how long after click a conversion can be attributed
- **Business Rules**:
  - Default 30-day attribution window from click timestamp
  - Configurable per offer (1-90 days)
  - Expired click sessions rejected during conversion postback
  - Clicks older than 90 days automatically purged from database
- **Success Criteria**: <0.1% of valid conversions rejected due to attribution window

#### FR-2.3.4: Cross-Device Attribution Support
- **Description**: Server-side tracking enables attribution even when device changes
- **Implementation**:
  - Click ID passed in URL (not cookie-dependent)
  - Advertiser stores click ID server-side (in session or database)
  - Conversion postback includes original click ID regardless of device
  - Example: Click on mobile → Convert on desktop (both attributed correctly)
- **Success Criteria**: Track 100% of S2S conversions regardless of device

### 2.4 Conversion Tracking and S2S Postback

#### FR-2.4.1: Server-to-Server Conversion Postback
- **Description**: Advertisers send conversion events via server-side HTTP request
- **Endpoint**: `GET/POST /affiliate/postback?click_id={id}&txid={order_id}&sale_amount={amount}`
- **Required Parameters**:
  - `click_id`: Original tracking ID from click session
  - `txid` or `transaction_id`: Advertiser's unique order ID (idempotency key)
- **Optional Parameters**:
  - `sale_amount`: Order total (required for CPS percentage calculation)
  - `payout_override`: Custom commission amount (advertiser override)
  - `event_type`: For multi-event tracking (LEAD, PURCHASE, SUBSCRIPTION)
  - `status`: Pre-approved status (1=approved, 0=pending review)
- **Processing Flow**:
  1. Authenticate request (IP whitelist, API key, or HMAC signature)
  2. Validate click_id exists and within attribution window
  3. Check duplicate: `(offer_id, txid)` must be unique
  4. Calculate CTIT (Click-To-Install-Time): `t_conversion - t_click`
  5. Apply anti-fraud rules (see FR-2.5)
  6. Determine payout: check tier/affiliate overrides, calculate CPS if applicable
  7. Store conversion with status (PENDING, APPROVED, REJECTED, FRAUD_SUSPECTED)
  8. Fire publisher postback (see FR-2.4.3)
  9. Return HTTP 200 with conversion ID
- **Performance Target**: Handle 1,000 req/sec, P99 < 200ms
- **Success Criteria**: Zero duplicate conversions, 99.99% processing reliability

#### FR-2.4.2: Conversion Approval Workflow
- **Description**: Conversions enter pending state with manual/automatic approval
- **Status Lifecycle**:
  - **PENDING**: Initial state, awaiting validation (default 7 days)
  - **APPROVED**: Validated, will be paid on next cycle
  - **REJECTED**: Invalid (refund, duplicate, failed validation)
  - **FRAUD_SUSPECTED**: Flagged by anti-fraud engine, under review
- **Auto-Approval Rules**:
  - Conversions from GOLD+ tier affiliates auto-approve after 3 days
  - Conversions <$50 from SILVER+ auto-approve after 7 days
  - Conversions >$500 always require manual review
  - Advertiser can configure per-offer auto-approval thresholds
- **Business Rules**:
  - Affiliates see pending balance vs. approved balance separately
  - Only APPROVED conversions count toward payment minimum
  - Rejections after approval (chargebacks) deducted from next payment
- **Success Criteria**: 85% of conversions auto-approve, <5% approval reversals

#### FR-2.4.3: Publisher Postback Dispatcher
- **Description**: Fire real-time conversion notifications to affiliate's tracking system
- **Configuration**: Affiliate provides postback URL template during registration
- **URL Template Example**:
```
https://affiliate-tracker.com/postback?
  click_id={click_id}&
  payout={payout}&
  txid={txid}&
  status={status}&
  sub1={sub1}&
  sub2={sub2}
```
- **Macro Replacement**:
  - `{click_id}`: Original tracking ID
  - `{payout}`: Commission amount (USD)
  - `{revenue}`: Advertiser payment amount
  - `{txid}`: Transaction ID
  - `{status}`: approved, pending, rejected
  - `{sub1}` through `{sub5}`: Original sub-ID values
  - `{offer_id}`, `{affiliate_id}`, `{sale_amount}`, `{timestamp}`
- **Delivery Guarantees**:
  - Fire within 5 seconds of conversion receipt
  - Retry failed requests: 3 attempts with exponential backoff (1s, 10s, 60s)
  - Timeout after 10 seconds per attempt
  - Log failed postbacks for manual review
- **Success Criteria**: 99.5% delivery success rate, P95 latency <2 seconds

### 2.5 Anti-Fraud and Quality Control

#### FR-2.5.1: Real-Time Fraud Detection Engine
- **Description**: Multi-layered fraud prevention at click and conversion stages
- **Click-Stage Validation**:
  - **IP Flood Control**: Max 60 clicks per IP per minute (Sliding window)
  - **Proxy/VPN Detection**: Flag datacenter IPs, block known proxy ranges
  - **Bot Detection**: Challenge suspicious User-Agents (headless browsers)
  - **Geographic Validation**: Reject clicks from blocked countries
  - **Click Velocity**: Alert if single affiliate generates >10x normal traffic suddenly
- **Conversion-Stage Validation**:
  - **CTIT Analysis** (Click-To-Install-Time):
    - If CTIT < 3 seconds: Flag as FRAUD_SUSPECTED (click injection attack)
    - If CTIT > 30 days: Reject as expired attribution window
    - Typical legitimate range: 30 seconds to 7 days
  - **Duplicate Transaction Detection**:
    - Enforce unique constraint on `(offer_id, txid)`
    - Bloom filter for fast duplicate checking before database insert
  - **Conversion Rate Anomaly**:
    - If affiliate CR suddenly >3x their average: Flag for review
    - If affiliate CR >5x offer average: Auto-suspend pending investigation
  - **Device Fingerprint Consistency**:
    - Compare click device attributes with conversion device (if available)
    - Flag if IP country changed (VPN switching indicator)
- **Automated Actions**:
  - Low risk: Allow with flag for post-review
  - Medium risk: Mark FRAUD_SUSPECTED, hold payment pending manual review
  - High risk: Auto-reject conversion, suspend affiliate temporarily
- **Success Criteria**: Detect >90% of fraud, <1% false positive rate

#### FR-2.5.2: Traffic Quality Scoring
- **Description**: Assign quality scores to affiliates based on historical performance
- **Scoring Factors** (0-100 scale):
  - Conversion rate vs. offer average (+/-)
  - Customer LTV (if advertiser shares data) (+)
  - Refund/chargeback rate (-)
  - Fraud flag frequency (-)
  - Traffic source transparency (+)
  - Compliance with advertiser terms (+)
- **Score Ranges**:
  - 90-100: Platinum quality (auto-approve for private offers)
  - 70-89: Good quality (standard access)
  - 50-69: Acceptable (restricted access, more scrutiny)
  - 0-49: Poor quality (suspend or terminate)
- **Business Rules**:
  - Scores recalculated weekly based on trailing 90-day data
  - New affiliates start at 70 (neutral) until 25+ conversions
  - Advertiser can set minimum quality score for offer access
- **Success Criteria**: Quality score correlates >0.8 with actual fraud rate

#### FR-2.5.3: Advertiser Quality Validation
- **Description**: Post-conversion validation signals from advertiser
- **Validation Signals**:
  - Email validation: Did user verify email?
  - Phone validation: Did user verify phone number?
  - Purchase fulfillment: Did order ship?
  - Refund tracking: Was order refunded?
  - Chargeback tracking: Was payment disputed?
  - Customer LTV: Did customer make repeat purchases?
- **Integration**:
  - Advertiser sends status updates via API: `PUT /api/v1/conversions/{id}/validate`
  - Payload: `{validation_status: 'EMAIL_VERIFIED', clv: 150.00}`
  - System adjusts affiliate quality score based on signals
  - High refund rate from specific affiliate triggers review
- **Success Criteria**: 60% of advertisers provide validation signals

### 2.6 SmartLink and Traffic Distribution System (TDS)

#### FR-2.6.1: SmartLink Creation
- **Description**: Single tracking link that intelligently routes to best offer
- **Configuration**:
  - SmartLink name and category (E-Commerce, Gaming, Finance, Dating, etc.)
  - Candidate offer pool (array of offer IDs)
  - Routing strategy: HIGHEST_EPC, ROUND_ROBIN, WEIGHTED, GEO_OPTIMIZED
  - Global fallback offer (used when no candidates match)
- **Example**: `https://track.network/smartlink/sl_12345?aff_id=888&sub1=facebook`
- **Business Value**:
  - Affiliate manages one link instead of dozens
  - Platform automatically optimizes for revenue
  - Advertiser receives better-matched traffic
- **Success Criteria**: SmartLinks achieve 20-30% higher EPC vs. direct links

#### FR-2.6.2: Intelligent Routing Algorithms
- **Description**: Real-time offer selection logic executed on each click

**Algorithm 1: HIGHEST_EPC (Performance-Based)**
```
FOR each offer in candidate_pool:
  IF offer matches visitor (geo, device, active, under cap):
    epc = historical_epc[offer][visitor_country][visitor_device]
    candidates.add(offer, epc)

RETURN offer with highest epc
```
- Calculate EPC from last 7 days of data
- Segment by country + device for accurate prediction
- Update EPC cache every 15 minutes

**Algorithm 2: WEIGHTED (Advertiser Bidding)**
```
Offer A: 50% weight (highest payout/priority)
Offer B: 30% weight
Offer C: 20% weight

Random selection proportional to weights
```
- Allows advertiser to buy more traffic by increasing weight
- Balances between performance and advertiser demand

**Algorithm 3: GEO_OPTIMIZED (Geographic Preference)**
```
IF visitor_country IN ['US', 'CA', 'UK', 'AU']:
  prioritize tier1_offers
ELSE IF visitor_country IN ['DE', 'FR', 'ES']:
  prioritize tier2_offers
ELSE:
  prioritize tier3_offers (lower payout international offers)
```
- Maximizes revenue by matching geo-specific offers
- Prevents wasted clicks on geo-restricted offers

**Algorithm 4: Multi-Armed Bandit (Exploration + Exploitation)**
```
80% of traffic → current best performer (exploit)
20% of traffic → random exploration (explore)
```
- Automatically discovers new winning offers
- Adapts to changing conversion rates without manual intervention

- **Business Rules**:
  - Check offer caps before selection (skip offers at capacity)
  - Respect offer targeting rules (geo, device restrictions)
  - If no candidates match: redirect to fallback offer or generic page
  - Log routing decision for analytics
- **Performance Target**: Routing decision <20ms
- **Success Criteria**: EPC-optimized routing increases revenue 25%+ vs. round-robin

#### FR-2.6.3: SmartLink Analytics
- **Description**: Report performance breakdown by routing decisions
- **Metrics**:
  - Click distribution by selected offer
  - EPC by offer within SmartLink
  - Conversion rate by offer
  - Revenue attribution by routing strategy
  - Fallback rate (% of clicks with no match)
- **Optimization Recommendations**:
  - Suggest removing underperforming offers from pool
  - Recommend adding high-converting offers
  - Alert when fallback rate exceeds 10%
- **Success Criteria**: Affiliates achieve 2x ROI vs. managing direct links

### 2.7 Reporting and Analytics

#### FR-2.7.1: Real-Time Performance Dashboard
- **Description**: Live metrics updated every 60 seconds
- **Affiliate Dashboard Metrics**:
  - Today's clicks, conversions, earnings
  - Top performing offers (by EPC)
  - Sub-ID breakdown (best traffic sources)
  - Pending vs. approved balance
  - Current tier status and path to next tier
- **Advertiser Dashboard Metrics**:
  - Today's spend and conversions
  - CPA and ROAS by offer
  - Top performing affiliates
  - Fraud rate and rejection rate
  - Conversion approval queue count
- **Performance Target**: Dashboard loads in <2 seconds
- **Success Criteria**: 70% of users check dashboard daily

#### FR-2.7.2: Multi-Dimensional Reporting
- **Description**: Flexible reporting with drill-down capabilities
- **Dimensions**:
  - Time: Hour, day, week, month, custom date range
  - Affiliate: Individual or grouped by tier
  - Offer: Single or multi-offer comparison
  - Geography: Country, state, city
  - Device: Mobile, desktop, tablet, OS, browser
  - Sub-IDs: sub1 through sub5 breakdown
  - Creative: Landing page variant performance
- **Metrics**:
  - Clicks (total, unique)
  - Conversions (total, by status)
  - Conversion Rate (%)
  - EPC (Earnings Per Click)
  - Revenue (total sales)
  - Payout (total commissions)
  - Profit Margin (revenue - payout)
  - AOV (Average Order Value)
  - ROAS (Return on Ad Spend) for advertiser
- **Visualizations**:
  - Line charts for trends
  - Bar charts for comparisons
  - Pie charts for distribution
  - Geo heatmaps for regional performance
- **Export**: CSV, Excel, PDF, scheduled email reports
- **Success Criteria**: 90% of user questions answerable without support

#### FR-2.7.3: Sub-ID Analytics (Traffic Source Optimization)
- **Description**: Detailed breakdown by sub-ID parameters for campaign optimization
- **Report Structure**:
```
Sub1 (Source) | Clicks | Conv | CR%  | EPC   | Revenue | Payout
--------------|--------|------|------|-------|---------|--------
Facebook      | 5,000  | 50   | 1.0% | $2.50 | $12,500 | $1,250
  > sub2: campaign_A | 3,000 | 35 | 1.17% | $2.92 | $8,750 | $875
  > sub2: campaign_B | 2,000 | 15 | 0.75% | $1.88 | $3,750 | $375
Google Ads    | 3,000  | 45   | 1.5% | $3.00 | $9,000  | $900
Blog Post A   | 2,000  | 15   | 0.75%| $1.50 | $3,000  | $300
```
- **Drill-Down**: Click any sub1 to see sub2 breakdown, then sub3, etc.
- **Optimization Actions**:
  - Pause underperforming sub-IDs
  - Increase budget on high-EPC sub-IDs
  - A/B test results comparison (sub3 as creative variant)
- **Aggregation**: Real-time incremental updates using upsert logic
- **Success Criteria**: 50% of advanced affiliates use sub-ID optimization

#### FR-2.7.4: Conversion Journey Reporting (Multi-Touch Attribution)
- **Description**: Show all affiliate touchpoints in customer journey
- **Use Case**: Customer clicked 3 different affiliates before converting
  1. Affiliate A: First click (blog review, 7 days ago)
  2. Affiliate B: Mid-touch (comparison site, 3 days ago)
  3. Affiliate C: Last click (coupon site, today) ← Gets 100% credit
- **Report Output**:
  - Show all touchpoints with timestamps
  - Display attribution model: Last-Click, First-Click, Linear, Position-Based
  - Calculate "assisted conversions" for upper-funnel partners
- **Business Value**:
  - Advertisers understand full customer journey
  - Can optionally reward assist partners (10% bonus payment)
  - Justify value of awareness-stage affiliates
- **Implementation Note**: Store all clicks per user (cookie/device fingerprint), link at conversion time
- **Success Criteria**: 30% of conversions have 2+ touchpoints tracked

### 2.8 Payment and Settlement

#### FR-2.8.1: Payment Term Management
- **Description**: Flexible payment cycles based on affiliate tier and agreement
- **Standard Terms**:
  - **NET-30**: Payment 30 days after end of month (industry standard)
  - **NET-15**: Payment 15 days after end of month (Silver+ tier)
  - **NET-7**: Payment 7 days after end of month (Gold+ tier)
  - **WEEKLY**: Every Monday for previous week (VIP tier only)
- **Calculation Example (NET-30)**:
  - Conversions in March 1-31 → Approved by April 5 → Paid May 1
  - Actual wait time: 30-60 days from conversion date
- **Business Rules**:
  - Only APPROVED conversions eligible for payment
  - Must meet minimum payout threshold (see FR-2.8.2)
  - Chargebacks deducted from current payment cycle
  - Affiliate can see "next payment date" and "next payment amount" in dashboard
- **Success Criteria**: 99% of payments processed on schedule

#### FR-2.8.2: Minimum Payout Thresholds
- **Description**: Minimum balance required before payment issued
- **Thresholds by Method**:
  - PayPal: $50 minimum (low transaction cost)
  - Direct Deposit/ACH: $100 minimum (US/Canada)
  - Wire Transfer: $500 minimum ($25 fee deducted from payment)
  - Check: $100 minimum (processing + postage)
  - Cryptocurrency: $100 minimum (BTC, USDC supported)
- **Business Rules**:
  - If balance below threshold: rolls to next payment cycle
  - Affiliate can request early payout with 10% fee if balance >$500
  - VIP tier: threshold waived (pay any amount)
  - Dormant accounts (no conversions 180 days): minimum raised to $500
- **Success Criteria**: 75% of affiliates reach threshold within 30 days

#### FR-2.8.3: Automated Payment Processing
- **Description**: Batch payment execution via integrated payment rails
- **Payment Methods**:
  - **PayPal Mass Pay**: Batch send to multiple affiliates (0-2% fee)
  - **Stripe Connect**: Direct transfer to affiliate Stripe account (instant)
  - **Wise (TransferWise)**: International transfers with competitive forex (1-2% fee)
  - **ACH/Direct Deposit**: US bank transfers via Plaid or Stripe Treasury (free)
  - **Wire Transfer**: Manual processing for high-value payments ($25 fee)
  - **Tipalti**: Enterprise payment automation with tax compliance (3% fee)
- **Processing Flow**:
  1. System generates payment batch on scheduled date
  2. Affiliate receives email: "Payment of $X is processing"
  3. Payment sent via configured method
  4. Status updated: PROCESSING → COMPLETED
  5. Affiliate receives email: "Payment of $X sent via PayPal"
  6. Transaction ID stored for reconciliation
- **Error Handling**:
  - Invalid payment details: Hold payment, email affiliate to update
  - Insufficient platform balance: Alert finance team, retry next day
  - Payment failed (closed account): Mark as ON_HOLD, require manual resolution
- **Success Criteria**: 98% of payments process successfully on first attempt

#### FR-2.8.4: Invoice and Tax Reporting
- **Description**: Generate payment statements and tax documentation
- **Invoice Contents**:
  - Invoice ID and date
  - Affiliate name and ID
  - Payment period (e.g., "March 1-31, 2026")
  - Breakdown by offer: conversions, payout, subtotal
  - Deductions (chargebacks, fees)
  - Total payment amount
  - Payment method and transaction ID
- **Tax Forms** (US Compliance):
  - **1099-NEC**: For US affiliates earning >$600/year
  - **W-9**: Collect TIN during registration
  - Generated annually in January for previous tax year
- **Access**: Affiliates download invoices from dashboard, receive email copy
- **Success Criteria**: 100% of payments have corresponding invoice

#### FR-2.8.5: Chargeback and Reversal Management
- **Description**: Handle refunds and fraudulent conversions post-payment
- **Scenarios**:
  - Customer refunds order within 30 days
  - Credit card chargeback discovered 60 days later
  - Conversion marked fraud after affiliate already paid
- **Business Rules**:
  - Deduct chargeback amount from affiliate's next payment
  - If balance goes negative: mark account as DEBT, suspend until resolved
  - Allow dispute process: affiliate can challenge chargeback with evidence
  - Excessive chargeback rate (>10%): trigger quality review
- **Notification**: Email affiliate immediately when chargeback occurs with reason
- **Success Criteria**: <3% of conversions result in chargebacks

### 2.9 API and Integration

#### FR-2.9.1: RESTful API for Advertisers
- **Description**: Programmatic access for advertisers to manage offers and conversions
- **Authentication**: API Key (Bearer token) in Authorization header
- **Key Endpoints**:
  - `POST /api/v1/offers` - Create new offer
  - `PUT /api/v1/offers/{id}` - Update offer details, caps, status
  - `GET /api/v1/offers/{id}/performance` - Retrieve offer stats
  - `POST /api/v1/conversions` - Send S2S conversion (primary tracking method)
  - `GET /api/v1/conversions` - List conversions with filtering
  - `PUT /api/v1/conversions/{id}/approve` - Approve/reject conversion
  - `GET /api/v1/affiliates` - List affiliates promoting your offers
  - `PUT /api/v1/affiliates/{id}/block` - Block specific affiliate
- **Rate Limiting**: 1,000 requests/hour per API key (adjustable for enterprise)
- **Success Criteria**: 40% of advertisers use API for conversion tracking

#### FR-2.9.2: RESTful API for Affiliates
- **Description**: Programmatic access for affiliates to retrieve performance data
- **Key Endpoints**:
  - `GET /api/v1/offers` - List available offers with filtering
  - `GET /api/v1/offers/{id}` - Get offer details and creatives
  - `POST /api/v1/links` - Generate tracking link with custom sub-IDs
  - `GET /api/v1/reports/performance` - Retrieve click/conversion stats
  - `GET /api/v1/reports/sub-ids` - Get sub-ID breakdown
  - `GET /api/v1/conversions` - List individual conversions
  - `GET /api/v1/payments` - Payment history and upcoming payments
  - `GET /api/v1/account/balance` - Current pending/approved balance
- **Use Case**: Large affiliates integrate with their own tracking platform
- **Success Criteria**: 20% of VIP affiliates use API integration

#### FR-2.9.3: Webhook/Postback System
- **Description**: Real-time event notifications to external systems
- **Advertiser Webhooks** (push to advertiser server):
  - `affiliate.applied`: New affiliate requested access to your offer
  - `conversion.approved`: Conversion moved from pending to approved
  - `conversion.rejected`: Conversion rejected due to refund/fraud
- **Affiliate Postbacks** (push to affiliate server):
  - Configured per affiliate: `https://affiliate-tracker.com/postback?click_id={click_id}&payout={payout}`
  - Fired on conversion events (immediate, not batched)
  - See FR-2.4.3 for full specification
- **Reliability**: 3 retry attempts with exponential backoff, 99.5% delivery rate
- **Success Criteria**: 60% of API users configure webhooks

#### FR-2.9.4: White-Label and Embedded Solutions
- **Description**: Allow advertisers to embed affiliate portal in their own domain
- **Features**:
  - Custom domain (track.advertiser.com instead of network.com)
  - Custom branding (logo, colors, CSS)
  - Embed affiliate dashboard via iframe or React component
  - SSO integration (SAML, OAuth) for seamless login
- **Use Case**: Large advertisers want branded affiliate program experience
- **Pricing**: Enterprise tier feature ($2,000/month)
- **Success Criteria**: 5% of enterprise advertisers adopt white-label

### 2.10 Compliance and Security

#### FR-2.10.1: GDPR and Privacy Compliance
- **Requirements**:
  - Cookie consent banner on tracking pages
  - Privacy policy disclosure of data collection
  - Affiliate/advertiser data processing agreement (DPA)
  - Right to access: Affiliates can export all their data
  - Right to deletion: Delete affiliate account and all PII
  - Data retention: Click data purged after 90 days, conversion data 7 years
- **Technical Implementation**:
  - Respect Do Not Track (DNT) headers (optional)
  - Hash/anonymize IP addresses in analytics (last octet removed)
  - Server-side tracking minimizes client-side cookies
- **Success Criteria**: Zero GDPR violations, pass annual audit

#### FR-2.10.2: Payment Security (PCI Compliance)
- **Requirements**:
  - Never store affiliate bank account details directly (use tokenization)
  - Use PCI-compliant payment processors (Stripe, Wise, Tipalti)
  - Encrypt sensitive data at rest (AES-256)
  - Encrypt data in transit (TLS 1.3)
  - Restrict payment data access to finance team only
- **Success Criteria**: Pass PCI-DSS audit if processing credit cards

#### FR-2.10.3: Fraud Liability Model
- **Business Rules**:
  - Network liable for fraudulent conversions if fraud detection fails
  - Affiliate liable if caught sending fraudulent traffic (forfeit earnings)
  - Advertiser liable for false rejections (must pay affiliate + penalty)
- **Dispute Resolution**:
  - 3-party mediation for disputed conversions
  - Evidence review: traffic logs, conversion screenshots, customer data
  - Final decision by network operator with appeal option
- **Success Criteria**: <2% of conversions enter dispute, 90% resolved within 14 days

## 3. Non-Functional Requirements

### 3.1 Performance
- **Click tracking endpoint**: P95 <50ms, P99 <100ms
- **Conversion postback endpoint**: P99 <200ms, handle 1,000 req/sec
- **Dashboard page load**: <2 seconds for 30-day report
- **API response time**: P95 <500ms

### 3.2 Availability
- **Tracking system**: 99.95% uptime (SLA: <4 hours downtime/year)
- **Dashboard/API**: 99.5% uptime
- **Scheduled maintenance**: Max 2 hours/month, announced 7 days prior

### 3.3 Scalability
- **Support 10,000+ active affiliates**
- **Support 1,000+ active offers**
- **Handle 10 million clicks/month**
- **Handle 100,000 conversions/month**
- **Horizontal scaling for click/conversion endpoints**

### 3.4 Data Retention
- **Click sessions**: 90 days (then purged or archived)
- **Conversions**: 7 years (financial/tax compliance)
- **Payment records**: 7 years
- **Logs**: 30 days

### 3.5 Disaster Recovery
- **Database backup**: Daily full backup + continuous replication
- **RTO (Recovery Time Objective)**: 4 hours
- **RPO (Recovery Point Objective)**: 15 minutes (max data loss)

## 4. Success Metrics (KPIs)

### 4.1 Platform Health
- **GMV (Gross Merchandise Value)**: Total sales attributed through network
- **Network Revenue**: Total commission earned from advertisers
- **Active Affiliates**: Affiliates with ≥1 conversion in last 30 days
- **Active Offers**: Offers with ≥1 conversion in last 30 days

### 4.2 Quality Metrics
- **Fraud Rate**: <2% of conversions flagged as fraud
- **False Positive Rate**: <1% of legitimate conversions rejected
- **Advertiser Satisfaction (NPS)**: >40
- **Affiliate Satisfaction (NPS)**: >50

### 4.3 Operational Metrics
- **Payment Processing Success**: >98%
- **Postback Delivery Rate**: >99.5%
- **Conversion Approval Time**: <7 days median
- **Support Response Time**: <24 hours

## 5. Roadmap Priorities

### Phase 1 (MVP - Months 1-3)
- Core tracking (click → conversion → payment)
- Basic anti-fraud (CTIT, duplicate detection)
- Manual affiliate approval
- NET-30 payment with single method (PayPal)
- Essential reporting (clicks, conversions, EPC)

### Phase 2 (Growth - Months 4-6)
- SmartLink/TDS with EPC optimization
- Tiered commission structures
- Sub-ID analytics
- API for advertisers (S2S postback)
- Multiple payment methods

### Phase 3 (Scale - Months 7-12)
- Advanced anti-fraud (ML-based scoring)
- Multi-touch attribution reporting
- Affiliate API and postbacks
- White-label solutions
- Mobile app (iOS/Android)

### Phase 4 (Enterprise - Year 2)
- Influencer-specific features (promo codes, social tracking)
- Product feed automation (dynamic offers)
- Recruiter/sub-network support (multi-tier commissions)
- Advanced ML routing (predictive conversion modeling)
- Blockchain-based transparent ledger (optional)

## 6. Competitive Positioning

| Feature | Our Platform | CJ Affiliate | ShareASale | Impact |
|---------|--------------|--------------|------------|--------|
| SmartLink/TDS | ✅ ML-powered | ❌ | ❌ | ❌ |
| Real-time fraud | ✅ | ✅ | ⚠️ Basic | ✅ |
| Sub-ID depth | 5 levels | 3 levels | 5 levels | 10 levels |
| API quality | ✅ REST + GraphQL | ⚠️ Legacy REST | ⚠️ Limited | ✅ Modern REST |
| Payment speed | NET-7 (VIP) | NET-30 | NET-30 | NET-30 |
| Pricing | 3% network fee | 3.5% fee | 3.5% fee + $500 setup | Custom |

**Differentiators**:
1. **AI-Powered SmartLinks**: 25% higher EPC vs. static links
2. **Fastest Payment Terms**: NET-7 for top performers (vs. industry NET-30)
3. **Real-Time Fraud Engine**: Stop fraud before payout (competitors detect post-payment)
4. **Transparent Pricing**: 3% flat fee, no setup costs (vs. $500-$2,000 setup at competitors)

---

**Document Version**: 1.0
**Last Updated**: 2026-09-08
**Owner**: Product Team
**Status**: Approved for Implementation
