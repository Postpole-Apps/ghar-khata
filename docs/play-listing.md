# Play Store listing (draft)

This file is the Indian-English (en-IN) ads-era draft. Global default listing: docs/play-listing-en-US.md. Hindi listing: docs/play-listing-hi-IN.md.

## Title (29 chars)
Ghar Khata - Milk & Maid Bill

## Short description (70 chars)
Mark milk, maid & newspaper days and get your monthly bill in one tap.

## Category
House & Home (not Finance)

## Full description (draft)

Ghar Khata is a simple home diary for everyone who comes to your door: the doodh wala, your kaamwali bai, the newspaper boy and the water can man. Mark the days, and the monthly bill adds itself up.

DOODH HISAB WITHOUT THE NOTEBOOK
Everything is "present" by default. Tap only the days something changed: no milk today, half a litre extra, a double delivery. Your milk diary keeps the litres and the rate, and the monthly bill is ready on the 1st.

MAID ATTENDANCE AND KAAMWALI BAI SALARY
Set a monthly salary and paid leaves. Ghar Khata counts absences and works out the salary for the month. Change the salary later and old months keep their old amount.

NEWSPAPER BILL, WATER CAN AND MORE
Newspaper copies per day, water can deliveries, cook or driver visits: pick per unit, per visit or monthly.

SHARE THE MONTHLY BILL
Send the bill as text on WhatsApp. Pro adds a bill image or PDF.

PRIVATE AND SIMPLE
Your data is stored on your device. No account, no sign-up. The free version shows ads; you can remove them with a one-time purchase. Ad and data practices are described in the Privacy Policy; in regions that require it (such as the EEA, UK and Switzerland), ad choices are also available under Settings > Privacy options. Back up your data to a file you choose, and restore it any time. Backup is always free. Usage analytics and crash reports (not your diary entries) are described in the Privacy Policy.

FREE
Marking days, rates, paid leaves, the monthly bill and backup are free for up to 3 providers.

PRO (ONE-TIME PURCHASE, NO SUBSCRIPTION)
Unlimited providers, bill as image or PDF, CSV export, year summary, a daily "mark today" reminder. Pro also removes ads.

REMOVE ADS (ONE-TIME PURCHASE)
Just want the ads gone? Remove ads hides them. Pro features stay locked.

Available in English and Hindi (follows your phone's language).

Internal note, never pasted into Play. Search keywords: doodh hisab, milk diary, maid attendance, kaamwali bai salary, newspaper bill, water can, monthly bill.

## Wording rules
Avoid in the listing: "expense tracker", "budget", "loan", "udhaar/credit", "payments".

## Financial features declaration
Answer: **"My app doesn't provide any financial features."**
"Mark paid" is only a personal note; no money moves through the app. There is no UPI or payment integration.

## Other
- Privacy policy URL and support email are set in `app/src/main/res/values/app_config.xml`.
- Contains ads: the ads-shipping build shows ads (Google Mobile Ads, UMP consent). Declare in Play Console at release time (not done by this change).
- Permissions: INTERNET and AD_ID (analytics and ads), POST_NOTIFICATIONS (optional Pro reminder). No SMS, contacts, location, storage or exact-alarm permissions.
