---
version: alpha
name: Home Remote
description: A Russian Android remote with a physical directional pad and permanent projector focus keys.
colors:
  background: "#f3f2ec"
  text: "#192b24"
  muted: "#69736b"
  border: "#dce1d6"
  primary: "#c9e79a"
  remote: "#1a2b24"
  surface: "#fcfdf8"
typography:
  body:
    fontFamily: "sans-serif"
  heading:
    fontFamily: "sans-serif-medium"
rounded:
  DEFAULT: "17px"
  panel: "28px"
spacing:
  page: "22px"
  button-gap: "8px"
components:
  RemoteButton:
    height: "48px"
  FocusButton:
    height: "58px"
---

# Home Remote design

## Overview

The reference is a handheld projector remote: a dark directional pad, large OK and two lens keys. This is a product tool for Russian-speaking Android users controlling a projector from a sofa, including when the projected picture is blurry. Keep the existing muted green identity; do not introduce a dashboard, marketing hero, decorative animation or new typeface.

Runtime values in ReceiverActivity remain canonical. This document mirrors its BG, INK, MUTED, LINE, LIME, DARK and WHITE constants. Native Android density-independent pixels correspond to the documented px values; text uses scalable sp. GuideActivity and the receiver use the same existing palette. RemoteButton and RemoteIcon own phone controls.

## Colors

Background is a quiet warm grey. The remote pad is dark green; its controls use pale text. Pale green highlights OK and focus. Offline controls stay in place with reduced opacity and disabled semantics. Connection status uses text as well as color. Only the light theme is currently supported.

## Typography

Use Android sans-serif with Cyrillic support. Main title is 32sp, panel heading 24sp, normal controls 14–17sp, focus 24sp, utility labels 11sp. No downloaded fonts or font swaps. Short direct Russian labels describe actions.

## Layout

Order: identity/connection, focus, directional pad, navigation, volume, expandable image settings, screen reader, preferences/help. F− and F+ must remain on the main screen for a remembered projector while offline. Geometry controls are secondary and expandable. The outer ScrollView owns page scrolling. The directional pad shrinks at 320dp; image tiles become one column below 340dp or above 1.2 font scale. Bottom sheets own a bounded scrolling body; their navigation stays reachable.

## Elevation & Depth

Use tonal surfaces and thin borders. Remove Android default button elevation from phone controls. Sheets dim the existing screen; no additional shadow stack or blur.

## Shapes

Controls use 17dp rounded corners; the directional pad and sheets use 28dp. Preserve familiar arrow and OK geometry. The square app logo is an existing brand asset.

## Components

RemoteButton owns touch, haptics, repeat and release cancellation. RippleDrawable communicates pressed/focused state; Android native button semantics retain accessibility and keyboard activation. Repeating presses are bounded requests; offline requests are discarded. Connection labels use a fixed slot. Android AlertDialog owns pairing/confirmation semantics; native platform dialogs are accepted here because this is an Android application, not a web page.

Settings panels use the same title, close button, status, scrolling body and navigation. Icon-only arrows and close controls need Russian accessible names. New pairing codes revoke existing access and require an explicit confirmation. Transient network failures never reset the key or the remembered device type.

## Do's and Don'ts

Power lives in the top-right identity row. Its scrolling dialog distinguishes projection standby from Android shutdown. Unverified infrared support is secondary, explains the limitation and requires an observed Home response before exposing power. A sent command is never described as confirmed hardware action.

- Keep primary controls in a stable position during retries.
- Preserve focus visibility independently of network/capability request success.
- Never claim lens movement from an API acknowledgement alone.
- Test 320dp, ordinary phone width, increased font scale, offline and resumed states.
- Do not publish personal device IPs, pairing keys or private screenshots.
