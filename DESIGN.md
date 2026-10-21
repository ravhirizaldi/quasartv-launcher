---
name: QuasarTV
description: A quiet native Android TV launcher with open midnight space and warm, precise remote focus.
colors:
  midnight-background: "#0C111B"
  muted-plum-background: "#171521"
  ember-night-background: "#1D1715"
  slate-surface: "#1B2330"
  warm-ivory-text: "#F4F1EA"
  cool-muted-text: "#A8B2C2"
  ember-primary: "#D8AA62"
  ember-focus: "#F1C780"
  brass-metadata: "#D2B37F"
  slate-outline: "#333F50"
  error: "#EF8D8A"
typography:
  hero:
    fontFamily: "sans-serif-medium"
    fontSize: "42sp"
    fontWeight: 500
  title:
    fontFamily: "sans-serif"
    fontSize: "34sp"
    fontWeight: 700
  section:
    fontFamily: "sans-serif-medium"
    fontSize: "22sp"
    fontWeight: 500
  body:
    fontFamily: "sans-serif"
    fontSize: "18sp"
    fontWeight: 400
  action:
    fontFamily: "sans-serif"
    fontSize: "16sp"
    fontWeight: 700
  source-label:
    fontFamily: "sans-serif"
    fontSize: "16sp"
    fontWeight: 400
  metadata:
    fontFamily: "sans-serif"
    fontSize: "14sp"
    fontWeight: 400
  clock:
    fontFamily: "sans-serif"
    fontSize: "24sp"
    fontWeight: 400
  creator-name:
    fontFamily: "sans-serif"
    fontSize: "24sp"
    fontWeight: 700
rounded:
  control: "8dp"
  tile: "10dp"
  settings-circle: "24dp"
spacing:
  screen-gutter: "28dp"
  minimum-action-size: "48dp"
  header-button-gap: "12dp"
  header-clock-gap: "20dp"
  scroll-header-gap: "24dp"
  scroll-header-clearance: "12dp"
  home-content-end-inset: "24dp"
  shelf-gap: "14dp"
  library-gap-relaxed: "18dp"
  library-gap-compact: "10dp"
components:
  launcher-settings-button:
    backgroundColor: "{colors.slate-surface}"
    textColor: "{colors.warm-ivory-text}"
    rounded: "{rounded.settings-circle}"
    padding: "13dp"
    size: "{spacing.minimum-action-size}"
  tv-settings-button:
    backgroundColor: "{colors.slate-surface}"
    textColor: "{colors.warm-ivory-text}"
    rounded: "{rounded.settings-circle}"
    padding: "13dp"
    size: "{spacing.minimum-action-size}"
  action-button:
    backgroundColor: "{colors.slate-surface}"
    textColor: "{colors.warm-ivory-text}"
    rounded: "{rounded.control}"
    padding: "0dp 20dp"
    height: "{spacing.minimum-action-size}"
  favorite-card:
    backgroundColor: "{colors.slate-surface}"
    textColor: "{colors.warm-ivory-text}"
    typography: "{typography.action}"
    rounded: "{rounded.tile}"
    padding: "4dp 10dp 8dp"
    width: "166dp"
  all-apps-card:
    backgroundColor: "{colors.slate-surface}"
    textColor: "{colors.warm-ivory-text}"
    typography: "{typography.action}"
    rounded: "{rounded.tile}"
    width: "126dp"
  app-card:
    backgroundColor: "{colors.slate-surface}"
    textColor: "{colors.warm-ivory-text}"
    rounded: "{rounded.tile}"
    padding: "10dp 10dp 8dp"
    height: "166dp"
  source-button:
    backgroundColor: "{colors.slate-surface}"
    textColor: "{colors.warm-ivory-text}"
    typography: "{typography.source-label}"
    rounded: "{rounded.tile}"
    padding: "10dp 12dp"
    width: "136dp"
    height: "90dp"
  source-button-inactive:
    backgroundColor: "{colors.slate-surface}"
    textColor: "{colors.cool-muted-text}"
    typography: "{typography.source-label}"
    rounded: "{rounded.tile}"
    padding: "10dp 12dp"
    width: "136dp"
    height: "90dp"
  settings-row:
    backgroundColor: "{colors.slate-surface}"
    textColor: "{colors.warm-ivory-text}"
    rounded: "{rounded.control}"
    padding: "12dp 18dp"
  creator-card:
    backgroundColor: "{colors.slate-surface}"
    textColor: "{colors.warm-ivory-text}"
    rounded: "{rounded.control}"
    padding: "16dp 20dp"
  featured-artwork:
    width: "264dp"
    height: "match_parent"
---

# Design System: QuasarTV

## Overview

**Creative North Star: "The Quiet Screening Room"**

QuasarTV gives a viewer on a sofa a clear place to begin. Deep midnight space, slate app plates, warm ivory type, and a scarce ember accent make the focused app immediately legible. The open composition gives app identity room without introducing a recommendation feed or promotional imagery.

Native Kotlin Views and XML define the system. Android system sans, original installed app banners and icons, and bounded focus motion keep the interface direct on an Android 9 television. PatchWall source glyphs and header vectors retain their extracted geometry under the device owner's authorization; the original APK is not bundled. The full-width content and compact information header leave attention on the selected favorite.

**Key Characteristics:**
- Open dark space with a strong type hierarchy and recognizable app artwork.
- Full-width content with two direct, round settings controls beside the clock.
- Unframed installed app banners that remain stable while focus moves to settings or inputs.
- Slate controls at rest and one warm, outlined remote focus.
- A fixed Home viewport with horizontal shelves, spoken labels, and all detected source buttons visible.
- Warm ivory active sources and grey, focusable inactive sources with safe OK behavior.
- Static surfaces with short, optional scale motion.

## Colors

Midnight and slate establish the room; warm ivory carries reading, cool muted text supports it, and ember identifies the current action.

### Primary
- **Ember Primary** (`colors.ember-primary`): The warm Android theme accent and the tint blended into focused controls.
- **Ember Focus** (`colors.ember-focus`): The active focus outline and native activated controls.

### Secondary
- **Brass Metadata** (`colors.brass-metadata`): Favorite status in the app library and current setting values. It supports state rather than competing with the focus outline.

### Neutral
- **Midnight Background** (`colors.midnight-background`): Default root/content background, initial system bars, and fallback icon backing.
- **Muted Plum Background** (`colors.muted-plum-background`): Optional restrained static background, labeled Muted plum in Settings.
- **Ember Night Background** (`colors.ember-night-background`): Optional restrained warm static background.
- **Slate Surface** (`colors.slate-surface`): App cards, source buttons, action buttons, settings rows, the creator card, and round header buttons.
- **Warm Ivory Text** (`colors.warm-ivory-text`): Titles, app names, main labels, header icons, and clock.
- **Cool Muted Text** (`colors.cool-muted-text`): Supporting copy, date, inactive source labels/icons, and loading/empty messages.
- **Slate Outline** (`colors.slate-outline`): Quiet section dividers and fallback icon strokes.
- **Error** (`colors.error`): App discovery failure text.

**The Scarce Ember Rule.** Use the warm accent for focus, selection, and meaningful state. Resting controls keep slate surfaces.

## Typography

**Display Font:** Android `sans-serif-medium` with system fallback.
**Body Font:** Android `sans-serif` with system fallback.

System sans keeps app names familiar and readable without loading a font. Medium display type supplies the open Home hierarchy; bold native labels identify library apps and controls. Text uses `sp`, honors Android font scale, and disables extra font padding. Do not impose a web line-height scale on native text metrics.

### Hierarchy
- **Hero:** Featured app title, up to two lines with end ellipsis.
- **Title:** Apps and launcher Settings headings; bold and marked as accessibility headings.
- **Section:** The Home Favorites heading. Settings section headings use bold body size.
- **Body:** Explanatory, empty, loading, and error copy; settings row titles use its bold counterpart.
- **Action:** Favorite names and All apps. Hero metadata uses the same size at regular weight; the empty Home action uses medium weight.
- **Source label:** Centered regular-weight names beneath source glyphs. Status remains in the spoken label rather than a second visible line.
- **Metadata:** Supporting status and details; the app library uses smaller (13sp) favorite status and bold (17sp) app names.
- **Clock:** Right-aligned time above smaller (12sp) muted date text. The header has no wordmark or navigation labels; its two controls have spoken labels.
- **Creator name:** Bold identity line in the inline About card, between muted metadata-size attribution and plain URL text.

**The Sofa Readability Rule.** Preserve native text scaling, meaningful spoken labels, and a visible focus outline. Truncation must never remove the accessible app or source name.

## Layout

The fullscreen shell uses the screen gutter on all sides and gives content the full remaining width. A fixed information header (48dp, growing to 54dp when Android font scale exceeds 1.1) aligns two circular settings buttons immediately before the right-aligned clock/date. The buttons use the header-button gap, followed by the header-clock gap. The clock/date and Home content share a 24dp end inset inside the screen gutter, aligning the time, featured image and shelf at the right. There is no sidebar, Quasar mark, wordmark, or settings chooser menu.

Destination content clips vertically below that header. Its clip rectangle allows an extra horizontal gutter (24dp per side) so focus outlines can remain visible. Preserve this distinction when changing containers: content must not cover the header, and side clipping must not cut the scaled focus.

Apps and Settings reserve the scroll-header gap below the information header. Its first scroll-header-clearance stays completely clear beneath the two focus circles; only the remaining lower half paints the static soft separator. Focusing either header control preserves the list's current scroll position. Home omits this gap and separator.

Home is a fixed vertical LinearLayout, not a vertical scroller. Its hero takes the remaining height through a zero-height, weight-one region; the unframed artwork view matches that region's height, adjusts its width up to 264dp, and fits the source image against the shared right edge. Long titles remain within two ellipsized lines. Empty Home uses the same flexible region instead of a fixed-height empty container. Repeated Down on a source button must never move the page.

The Favorites heading starts after a top margin (12dp). Its shelf has a viewport height (112dp), inset content (4dp vertically), fixed-width favorite tiles, and a final All apps tile. Every shelf tile, including All apps, has the trailing shelf gap; the terminal gap preserves the complete scaled outline at the scroll limit. The shelf viewport reserves horizontal focus padding (6dp per side). Focused favorites update the featured app; OK launches directly without a separate Open app control. The source-button strip sits below, without an Inputs heading. Only the two horizontal shelves scroll. The exact Home composition follows the current layout resources.

Apps displays every launchable app Android reports in a scrolling grid. It uses four columns unless both physical width reaches 1600px and logical width reaches 900dp, when it uses five. Relaxed and Compact preferences adjust horizontal gaps. Rows grow (190dp) when font scale exceeds 1.1; ordinary rows use the app-card height. Launcher Settings uses a single scrolling stack of content-sized rows with a minimum height (68dp). Inputs have no separate launcher destination.

**The Visible Bounds Rule.** Native focus scrolling must include the scaled control and outline. `FocusLayout` extends its drawing rectangle by 2% of each dimension plus horizontal (10dp) and vertical (4dp) allowances.

## Elevation & Depth

Depth comes from midnight space, slate control plates, and the focused tonal lift. Native elevation and state-list elevation animation are disabled. Controls have no drop shadows, blur, or continuous background effects.

Apps and Settings use a static native separator drawable below the clear header allowance. It fades vertically from transparent through black at 24% alpha (`#3D000000`) and back to transparent, with no hard line. Its upper half is unpainted: the gradient must not touch the information or gear focus circle. This is a small fixed drawable, not live blur, Android elevation, an animated shadow, or a control shadow. The exact effect belongs in the sidecar.

The featured artwork view adds no background, gradient, border, frame, or padding. Package-supplied artwork may contain its own background; preserve that original image instead of adding launcher chrome.

**The Flat Room Rule.** Keep controls flat at rest. Focus adds an outline and bounded tonal change; the static scrolling-page separator stays below its clear header allowance.

## Shapes

Quietly rounded native rectangles establish the control language: shared controls use the control radius, app cards and source buttons use the tile radius, and the two header buttons use the settings-circle radius. App fallback icons use the control radius and a thin outline (1dp). The full-width shell remains an unboxed plane; featured artwork uses the tile radius (10dp), clipped to the fitted image bounds with a native outline. This rounds the actual image even when the view has vertical letterboxing; it adds no frame, background, padding or bitmap copy.

Every remote action keeps at least the minimum action bound. Preserve the source artwork's aspect ratio with native `fitCenter`, and preserve the parent space required for focused control bounds.

## Components

### Remote focus

All destinations use the shared `applyFocusStyle` treatment: a 12% blend of Ember Primary into the base color and an Ember Focus outline (2dp). The resting outline is transparent. Full motion scales to 1.04 over 140ms; Reduced scales to 1.015 over 70ms; Off stays at 1.0 immediately. Android-disabled animators override the launcher preference. The interpolator is Android `DecelerateInterpolator`; outline and tonal feedback remain visible with motion off.

`MainActivity` owns initial and restored destination focus. Screen builders create controls without posting focus requests. A state-refresh render preserves header focus or a valid prior content tag; otherwise it restores the stored app/input target, falling back to the first enabled clickable control. The posted render callback checks that its screen remains attached and that a state refresh has not been superseded by a newer focus generation. Child callbacks must not reclaim focus after the viewer moves.

### Header settings controls

Two native ImageButtons use slate circular backgrounds, extracted PatchWall vector geometry, ivory tint, and the shared focus treatment. Preserve the information glyph's original opacity and the gear's filled silhouette. The vector content fits the padded button with `fitCenter`. The information icon opens Launcher settings directly; the gear opens Android TV settings directly. Their spoken labels are Launcher settings and TV settings. Up from the favorite shelf or first library row reaches the information button; Left/Right moves between the header buttons, and Down restores stored content focus. Back returns launcher sections to Home or moves from the header to content.

### Actions and app cards

Empty/recovery actions are compact slate controls with warm ivory labels, centered content, and the minimum action height. Home favorite cards use installed artwork (66dp view) above a single-line name. All apps uses the existing app-grid vector and opens the library. The library uses larger icon views (82dp), two-line names, and explicit favorite/action status. Shared icon bitmaps are bounded (128px) during catalog loading.

Featured artwork is loaded separately from package banners, then high-density package icons when a banner is absent. Its bitmap request retains a bounded size (264 by 156dp at display density, capped at 512 by 288 pixels), while the image view fits it within the flexible hero. A bounded cache (1MiB) and one asynchronous worker load artwork; cache hits display synchronously before the next frame. Selecting the same package does not reset its image; the activity retains the last featured package through header/input focus and input redraws. Keep these behaviors together to avoid banner flashing.

OK launches a library app. In Choose favorites mode, OK toggles its favorite state. Hold OK, Menu, Info, or X opens native favorite actions, including reordering when available. Keep native popup behavior and Back return paths.

### Input sources

Home shows every detected source below favorites, without an Inputs label. The horizontal viewport (98dp high) holds the source-button height plus inner padding (4dp above and below). Each button places a fitted source glyph (40 by 25dp) above its centered name, separated by a gap (12dp); the row uses the shelf gap between buttons. There is no visible metadata line in this compact form. HDMI, AV, and TV glyphs use the extracted original small PNGs; other types retain the native fallback vector. The original PNGs (79 by 49 pixels) render smaller on the target TV rather than being enlarged.

Sort active passthrough sources first, then hardware type and input ID. Prefer the TV's custom label, then its provider label. A source is active only when it supports passthrough and its state is exactly CONNECTED: its glyph and label use warm ivory and OK can activate it. All other detected sources remain grey and focusable with no click callback. Keep the reason in the spoken description, use the input ID as its stable tag, and point Down back to the same button so repeated Down cannot scroll or move Home. A connected tuner service alone does not establish supported passthrough switching.

Native TvInputManager callbacks register while the launcher is visible. A standby callback changes tone and availability without removing the source; rediscovery can restore clickability. Empty/discovery failures remain in the source line; direct-switch failures can fall back to Android's available picker without adding an Inputs destination. Retain Quasar's slate surfaces and warm focus around the user-requested PatchWall button geometry.

### Settings rows

Content-sized slate rows pair an ivory title and muted explanation with a brass current value. Appearance contains Background, Focus motion, and App spacing; Favorites contains Manage favorites; About contains the inline creator card. TV settings has its own direct header control. Preferences open native single-choice dialogs with Cancel; this does not introduce an intermediate settings chooser. The stack can grow with text rather than clipping into fixed row heights.

### Creator card and photo viewer

The About card uses the shared slate surface and focus style, with a minimum height (132dp). It displays “Made for the moments in between.”, “Ravhi Rizaldi”, and “https://github.com/ravhirizaldi” as three native text lines. The first and third lines are muted; the identity uses the creator-name role. The URL is plain text with linking disabled.

The first nine clicks leave the card in place. The tenth opens the bundled portrait in a fullscreen native dialog and resets the counter. The fitted image keeps its aspect ratio within the screen gutter on a midnight background. Close sits at the top right (96 by 48dp), receives initial focus, and dismisses the viewer; Back also dismisses it and restores focus to the attached creator card.

The user-supplied portrait is bundled offline as a bounded JPEG (539 by 720 pixels, 82,079 bytes including embedded metadata). One transient worker decodes it with RGB_565 preference and scaling disabled. Dismissal clears and recycles the displayed bitmap; a decode finishing after dismissal is recycled instead of being attached. This viewer requires no network permission or image dependency.

### Feedback and exceptional states

Empty Home keeps the fixed composition, explains favorites, and offers Browse apps. Discovery failures explain the problem and provide Android settings where appropriate. Loading copy describes the current discovery operation. The Apps empty state states that Android reported no launchable apps. The Home source line distinguishes no detected inputs, discovery, unsupported service, and failure. There are no launcher Toasts, transient message overlays, or permanent remote-instruction footers; operation failures can be logged.

## Do's and Don'ts

### Do:
- **Do** use native Kotlin Views/XML, Android system typography, original installed app artwork, and the documented PatchWall source/header glyphs.
- **Do** keep the current app, focused action, and available return path clear from the sofa.
- **Do** preserve minimum action bounds, font scaling, spoken labels, and the shared focus system.
- **Do** keep Home fixed, allow only horizontal shelf scrolling, and retain side room for focus below the fixed header.
- **Do** preserve the last featured app while focusing header controls or inputs, and deliver cached artwork before the next frame.
- **Do** show all detected sources with the TV's custom labels, grey inactive states, and native callback refresh.
- **Do** keep inactive source buttons focusable without activation and repeated Down on the same source.
- **Do** keep the upper half of the scrolling-page separator clear beneath both header focus circles and preserve the list position on header focus.
- **Do** keep the creator URL plain text and restore creator-card focus when its offline photo viewer closes.

### Don't:
- **Don't** add a sidebar, Quasar mark, wordmark, Open app control, separate Inputs destination, or intermediate settings chooser.
- **Don't** add artwork frames, backgrounds, padding, launcher Toasts, transient overlays, or permanent instruction footers.
- **Don't** add ads, recommendations, promotional feeds, or invented artwork.
- **Don't** add blur, control drop shadows, animated shadows, continuous effects, or mandatory motion.
- **Don't** turn the scrolling-page separator into a hard line or paint it against the information or gear circle.
- **Don't** hide detected inactive sources or treat a tuner service connection as passthrough support.
- **Don't** add an Inputs heading, a compact source metadata line, or a vertical Home scroller.
- **Don't** introduce Compose, WebView, network dependencies, or downloadable fonts for presentation.
- **Don't** claim Xiaomi performance or hardware input switching from emulator evidence.
