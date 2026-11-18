# Ads KPIs and pull-back guardrails

**Owner: Monetisation / Ram.** Monetisation / Ram reads these numbers and applies the guardrails below.
Analytics Manager runs the GA4 queries once events ship; this repo does not build a dashboard.

## Events

| Event | When |
|---|---|
| `remove_ads_paywall_view` | A purchasable Remove ads offer (Settings row) is shown, once per Settings visit |
| `ad_paywall_entry` | Remove ads row tapped. Param `source` is an enum only (`settings_row`) |
| `remove_ads_purchase_start` | Remove ads purchase sheet launched |
| `remove_ads_purchase_success` | Fresh Remove ads purchase completed. Never fires on Restore or on a refresh that finds an existing entitlement |
| `remove_ads_purchase_fail` | Cancel, launch failure, Play error or already-owned. Param `reason` is an enum |
| `ad_impression` | Firebase automatic event, not first-party. Needs Firebase linked to the AdMob app (done by Release and QA in the consoles) |

Purchase events carry `item_id` (product id), `price_micros`, `value` and `currency` when known. Pro keeps
`pro_screen_view`, `purchase_started`, `purchase` and `purchase_restored`; those never fire for Remove ads.
No ads event carries provider names, phone numbers, notes, bill amounts, provider type or bill status.

## KPIs (always compared with the pre-ads / closed-test baseline)

- ARPDAU, with ads and IAP split out
- Ad impressions per DAU, and estimated ad revenue per DAU
- `remove_ads_paywall_view` to `remove_ads_purchase_success` conversion
- Pro paywall view (`pro_screen_view`) to `purchase` conversion. It must not collapse
- D1 / D7 / D30 retention
- Play rating, 1-star rate, and a count of reviews that mention ads
- Crash-free users and ANR rate versus the existing halt criteria

## Required step: freeze the baseline (do not skip)

Record retention, ratings (if any) and Pro conversion:

1. Immediately before the ads closed-test build is widely used.
2. Immediately before any production ads release.
3. If production launches without ads first, also freeze a pre-ads production baseline before judging any guardrail.

The numbers are recorded by Monetisation / Ram outside this repo; they are not invented here.

## Pull-back guardrails (ads plan §4.3)

If **any** of these trips, tighten or kill ads **the same day** and tell Ram. Use Remote Config, with
**no Play release**, via the existing `AdsKeys` (`com.postpole.gharkhata.ads.AdsKeys`).

1. D1 or D7 retention is 5 or more percentage points below the baseline after at least 7 days and a usable sample.
2. Pro conversion is 25% or more below the baseline, with a usable sample (never judge on single-digit purchases).
3. Crash or ANR rate hits the halt threshold, or a crash cluster is in the ads SDK.
4. A visible spike in 1-star reviews or support mail about ads, frequency or accidental clicks.
5. Interstitial fill appears on open / exit, on a form, or stacked. This is a policy bug: set `ads_interstitial_enabled=false` immediately.

Order of response:

1. First pull-back: `ads_interstitial_enabled=false` (banners stay).
2. Second pull-back: `ads_enabled=false`.

**Who flips the keys: Monetisation / Ram.** Turning ads back on needs Ram, then Ajith, after the cause is fixed.
The in-app defaults stay the gk-01 launch defaults already registered with Remote Config
(`AdsSettings.DEFAULT`); do not change them to implement a pull-back.
