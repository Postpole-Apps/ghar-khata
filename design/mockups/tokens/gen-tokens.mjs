// Ghar Khata v3 colour tokens: "deep green + lime", generated with Material Color Utilities 0.3.0.
// The M3 role logic (MaterialDynamicColors, contrast-checked tones) is unchanged; only the five
// key palettes are custom, so the scheme is no longer stock TonalSpot.
// Usage: node gen-tokens.mjs > colors.css   (tokens/build.sh pastes it into ../html/styles.css)
import { argbFromHex, hexFromArgb, Hct, TonalPalette, DynamicScheme, MaterialDynamicColors as D } from '@material/material-color-utilities';
const Variant = { TONAL_SPOT: 2 };  // dynamiccolor/variant.js (not exported by the package)

const BRAND = '#1F6B4A';                       // Theme.kt brand green = anchor hue (HCT 163.2)
const brandHue = Hct.fromInt(argbFromHex(BRAND)).hue;
const P = {
  primary:        TonalPalette.fromHueAndChroma(brandHue, 48),   // brand green, a little more vivid than TonalSpot's 36
  secondary:      TonalPalette.fromHueAndChroma(120, 56),        // lime (chartreuse) as the secondary role
  tertiary:       TonalPalette.fromHueAndChroma(230, 36),        // calm sky-teal, for "changed quantity" and Maid
  neutral:        TonalPalette.fromHueAndChroma(125, 11),        // lime-tinted neutrals -> pale lime-cream surfaces
  neutralVariant: TonalPalette.fromHueAndChroma(140, 14),
};
// Custom (extended) palettes, not part of ColorScheme
const LIME    = TonalPalette.fromHueAndChroma(120, 72);          // the bright lime accent fill (#CDEA2B at tone 88)
const FOREST  = TonalPalette.fromHueAndChroma(160, 18);          // deep forest "hero" surfaces (PennyWise header)
const SAFFRON = TonalPalette.fromInt(argbFromHex('#C46A1B'));    // Pro + Half day accent only

const roles = ['primary','onPrimary','primaryContainer','onPrimaryContainer','inversePrimary',
 'secondary','onSecondary','secondaryContainer','onSecondaryContainer',
 'tertiary','onTertiary','tertiaryContainer','onTertiaryContainer',
 'error','onError','errorContainer','onErrorContainer',
 'background','onBackground','surface','onSurface','surfaceVariant','onSurfaceVariant','surfaceTint',
 'surfaceDim','surfaceBright','surfaceContainerLowest','surfaceContainerLow','surfaceContainer','surfaceContainerHigh','surfaceContainerHighest',
 'inverseSurface','inverseOnSurface','outline','outlineVariant','scrim','shadow'];
const kebab = s => s.replace(/[A-Z]/g, c => '-' + c.toLowerCase());
const hx = a => hexFromArgb(a).toUpperCase();
const out = [];
for (const isDark of [false, true]) {
  const s = new DynamicScheme({ sourceColorArgb: argbFromHex(BRAND), variant: Variant.TONAL_SPOT, contrastLevel: 0, isDark,
    primaryPalette: P.primary, secondaryPalette: P.secondary, tertiaryPalette: P.tertiary, neutralPalette: P.neutral, neutralVariantPalette: P.neutralVariant });
  out.push(`${isDark ? '.dark, [data-theme="dark"]' : ':root'} {  /* ${isDark ? 'darkColorScheme' : 'lightColorScheme'} */`);
  for (const r of roles) out.push(`  --md-sys-color-${kebab(r)}: ${hx(D[r].getArgb(s))};`);
  const ext = isDark ? {
    'lime': LIME.tone(88), 'lime-dim': LIME.tone(80), 'on-lime': LIME.tone(15), 'lime-container': LIME.tone(30), 'on-lime-container': LIME.tone(90),
    'hero': FOREST.tone(12), 'on-hero': FOREST.tone(95), 'hero-variant': FOREST.tone(20), 'on-hero-variant': FOREST.tone(80),
    'saffron': SAFFRON.tone(80), 'on-saffron': SAFFRON.tone(20), 'saffron-container': SAFFRON.tone(30), 'on-saffron-container': SAFFRON.tone(90),
  } : {
    'lime': LIME.tone(88), 'lime-dim': LIME.tone(80), 'on-lime': LIME.tone(15), 'lime-container': P.secondary.tone(94), 'on-lime-container': LIME.tone(20),
    'hero': FOREST.tone(20), 'on-hero': FOREST.tone(95), 'hero-variant': FOREST.tone(30), 'on-hero-variant': FOREST.tone(85),
    'saffron': SAFFRON.tone(40), 'on-saffron': SAFFRON.tone(100), 'saffron-container': SAFFRON.tone(90), 'on-saffron-container': SAFFRON.tone(10),
  };
  out.push('  /* extended colours (Compose: GharColors via staticCompositionLocalOf) */');
  for (const [k, v] of Object.entries(ext)) out.push(`  --gk-color-${k}: ${hx(v)};`);
  out.push('}');
}
out.push(':root {  /* reference tonal palettes (HCT tones) */');
for (const [n, p] of [...Object.entries(P).map(([k, v]) => [kebab(k), v]), ['lime', LIME], ['forest', FOREST], ['saffron', SAFFRON]])
  out.push('  ' + [0,10,15,20,30,40,50,60,70,80,85,88,90,95,98,100].map(t => `--md-ref-palette-${n}${t}: ${hx(p.tone(t))};`).join(' '));
out.push('}');
console.log(out.join('\n'));
