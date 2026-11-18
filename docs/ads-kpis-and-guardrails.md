# Ads KPIs and pull-back guardrails

**Owner: Monetisation / Ram.** Monetisation / Ram reads these KPIs and applies the guardrails below. The Analytics Manager runs the GA4 queries once the events ship; this repo does not contain a dashboard.

## Events these KPIs rely on

| Signal | Source |
| --- | --- |
| `remove_ads_paywall_view` | First-party. Logged each time the purchasable Remove ads row appears in Settings (not on every Settings open). |
| `remove_ads_purchase_start` / `_success` / `_fail` | First-party. Params: `product_id`, `price_micros`, `value`, `currency` (when known). Fail = cancel, launch failure or Play error. No success on Restore or on a refresh that finds an existing entitlement. |
| `pro_screen_view`, `purchase_started`, `purchase`, `purchase_restored` | First-party, **Pro only**. Never fired for Remove ads. |
| `ad_impression` | Firebase automatic event, with value / currency. Needs Firebase linked to the AdMob app (Release and QA, in the consoles). Until linked, impression and ad-revenue KPIs are unavailable. See [Measure ad revenue](https://firebase.google.com/docs/analytics/measure-ad-revenue). |

No ads event carries provider names, phone numbers, notes, bill amounts, provider type or bill status.

## KPIs (compare against the frozen baseline)

- ARPDAU (ads and IAP, reported separately)
- Ad impressions per DAU, and estimated ad revenue per DAU
- `remove_ads` paywall view → purchase conversion
- Pro paywall view → purchase conversion (must not collapse)
- D1 / D7 / D30 retention
- Play rating, 1-star rate, and a count of reviews that mention ads
- Crash-free users and ANR rate versus the existing halt criteria

## Baseline freeze (required, do not skip)

Retention, ratings (if any) and Pro conversion must be frozen as a baseline:

1. **Immediately before the ads closed-test build is widely used.**
2. **Immediately before any production ads release.**
3. If production launches without ads first, also freeze a **pre-ads production baseline** before judging the guardrails.

The numbers are recorded by Monetisation / Ram outside this repo; they are not invented here. Guardrails are not judged until the relevant baseline exists.

## Pull-back guardrails (ads plan §4.3)

If **any** trips, tighten or kill ads the **same day** and tell Ram, using Remote Config with **no Play release**:

1. D1 or D7 retention is **5 or more percentage points below** the baseline, after at least 7 days and a usable sample.
2. Pro conversion is **25% or more below** the baseline, with a usable sample (do not judge on single-digit purchases).
3. Crash or ANR rate hits the halt threshold, or a crash cluster is in the ads SDK.
4. A visible spike in 1-star reviews or support mail about ads, frequency or accidental clicks.
5. Interstitial fill appears on open / exit, on a form, or stacked. Treat as a policy bug and set `ads_interstitial_enabled=false` immediately.

### Order of response

1. **First pull-back:** `ads_interstitial_enabled=false` (banners stay).
2. **Second pull-back:** `ads_enabled=false`.

### Who flips the keys

**Monetisation / Ram** sets these Remote Config keys (the `AdsKeys` constants from gk-01). Turning ads back on also needs Ram, then Ajith, after the cause is fixed.

In-app defaults stay the gk-01 launch defaults already registered with Remote Config; do not change them to pull back. Pull back in the Remote Config console only. Remote Config values apply on the next fetch, so confirm the change reached a test device.
