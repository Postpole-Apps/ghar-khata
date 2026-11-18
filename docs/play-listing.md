# Play Store listing (draft)

## Title (29 chars)
Ghar Khata - Milk & Maid Bill

## Short description (63 chars)
Mark milk, maid & newspaper days. Auto monthly bill. No sign-up.

## Category
House & Home (not Finance)

## Full description (draft)

Ghar Khata is a simple home diary for everyone who comes to your door: the doodh wala, your kaamwali bai, the newspaper boy and the water can man. Mark the days, and the monthly bill adds itself up.

**Doodh hisab without the notebook**
Everything is "present" by default. Tap only the days something changed: no milk today, half a litre extra, a double delivery. Your milk diary keeps the litres and the rate, and the monthly bill is ready on the 1st.

**Maid attendance and kaamwali bai salary**
Set a monthly salary and paid leaves. Ghar Khata counts absences and works out the salary for the month. Change the salary later and old months keep their old amount.

**Newspaper bill, water can and more**
Newspaper copies per day, water can deliveries, cook or driver visits: pick per unit, per visit or monthly.

**Share the monthly bill**
Send the bill as text on WhatsApp. Pro adds a bill image or PDF.

**Private and simple**
Your diary data stays on your device. No account, no sign-up. The app shows ads, which Pro removes. Back up your data to a file you choose, and restore it any time. Backup is always free. Usage analytics and crash reports (not your diary entries) are described in the Privacy Policy.

**Free**
Marking days, rates, paid leaves, the monthly bill and backup are free for up to 3 providers.

**Pro (one-time purchase, no subscription)**
Unlimited providers, bill as image or PDF, CSV export, year summary and a daily "mark today" reminder.

Available in English and Hindi (follows your phone's language).

Search keywords: doodh hisab, milk diary, maid attendance, kaamwali bai salary, newspaper bill, water can, monthly bill.

## Wording rules
Avoid in the listing: "expense tracker", "budget", "loan", "udhaar/credit", "payments".

## Financial features declaration
Answer: **"My app doesn't provide any financial features."**
"Mark paid" is only a personal note; no money moves through the app. There is no UPI or payment integration.

## Other
- Privacy policy URL and support email are set in `app/src/main/res/values/app_config.xml`.
- Permissions: INTERNET and AD_ID (analytics), POST_NOTIFICATIONS (optional Pro reminder). No SMS, contacts, location, storage or exact-alarm permissions.

## Release and QA checklist (issue #26, ads build only)
Do these in the same release as the ads-containing AAB. If compliance is not ready, production ships ad-free and this checklist repeats in the later ads update.

- [ ] Play Console: Contains ads = **Yes**, in the same release as the first ads.
- [ ] Data safety per the full AdMob disclosure (https://developers.google.com/admob/android/privacy/play-data-disclosure): Device or other IDs, Approximate location, App interactions and Diagnostics are Collected and Shared (Advertising, Analytics, Fraud prevention/security/compliance); Crash logs collected, not shared; Purchase history collected, not shared; diary names, phones and attendance not collected; encrypted in transit: Yes.
- [ ] Advertising ID declaration: Yes (Advertising or marketing, Analytics, Fraud prevention, Personalization after consent).
- [ ] Publish the policy from `gharkhata-privacy-ads.md` (set "Last updated"), then confirm `privacy_policy_url` in `app_config.xml` matches the published page.
- [ ] Developer website: https://pocket-ledger-postpole.web.app
- [ ] Target audience 18+ only; Designed for children: No.
- [ ] Feature graphic: rebuild from `store-graphics` (outside this repo) with the pill "Your data stays on your device · No sign-up · English & हिन्दी", then upload.
- [ ] Update listing copy in the Console to match this file; check screenshots for a leftover "No ads" pill.
