# Ghar Khata UI mockups v3 (approved)

Ajith approved this direction on 12 Nov 2026 (IST): deep green + lime, in the PennyWise style, on Material 3 Expressive. Screens are 720×1600 (360×800 dp @2x).

Tokens for `Theme.kt` are in `styles.css` (header comment has the Compose mapping). Sources that rebuild that file live in `tokens/` (`gen-tokens.mjs`, `header.css`, `colors.css`, `components.css`, `shapes.css`). Do not commit `node_modules`.

Font: system Roboto (`FontFamily.Default`). Dynamic colour is off by default (a Settings toggle ships in issue #8).

## Screens

| File | Screen | Issue |
|---|---|---|
| `00-overview.png` | Overview of the v3 set | all |
| `01-today.png` | Today (light), day-pill strip, hero, floating pill nav | #5 |
| `01b-today-empty.png` | Today empty state with doorstep illustration and presets | #5 |
| `01c-today-dark.png` | Today in dark theme | #5 |
| `02-providers.png` | Providers list (active then archived), per-provider 30-day strip | #4 |
| `03-add-provider.png` | Add provider form | #4 |
| `03b-edit-rate-change.png` | Edit provider + "Effective from" rate change | #4 |
| `04-provider-month.png` | Provider month calendar, days-delivered strip, daily bars | #6 |
| `04b-day-sheet.png` | Day-mark edit sheet | #6 |
| `05-bill-preview.png` | Bill preview with attendance donut | #6 |
| `05b-mark-paid.png` | Mark paid dialog | #6 |
| `06-pro-sheet.png` | Pro upgrade as a modal bottom sheet | #7 |
| `07-settings.png` | Settings (reminder + language, Upgrade card in the hero) | #8 |

v1 and v2 archives are not in this folder.

## Approved design decisions

- Floating **solid** pill bottom nav: Today, Providers, Settings. Inactive tabs are icon-only but must have `contentDescription`. Do not use a blurred nav (solid stays smooth on budget phones).
- An **Add pill** replaces the FAB, so it does not clash with the floating nav.
- On Today, a **day strip** replaces prev/next arrows. Future days are disabled. Tapping the date label opens a `DatePicker` for older dates.
- Pro is a `ModalBottomSheet`, not a full screen.
- Minimum **48 dp touch targets** on the 32 dp chips and pills.
- Charts are **per-provider only**. No household spend dashboard. Today's hero shows marking progress ("3 of 3 marked"), not spending.
- Saffron is an accent only (Pro, Half day, free-limit warnings). Lime is the bright accent fill; text on lime is always on-lime.
- Sample archived provider in the mockup is a Bisleri can; treat that as example content, not a required seed.

## Key colour roles (from `styles.css`)

| Role | Light | Dark |
|---|---|---|
| primary | `#006C47` | `#71DAA6` |
| secondary | `#566500` | `#BBD14D` |
| surface | `#F8FBE4` | `#111508` |
| lime / lime-dim / on-lime | `#CDEA2B` / `#B7D300` / `#222900` | same |
| hero / hero-variant / on-hero | `#1E3527` / `#344C3D` / `#DCF8E2` | `#0D2417` / `#1E3527` / `#DCF8E2` |
| saffron | `#944A00` | `#FFB784` |

See the `COMPOSE MAPPING (Theme.kt)` header in `styles.css` for the full ColorScheme, `GharColors`, Shapes, Typography, and numeral styles.
