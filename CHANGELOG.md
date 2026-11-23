# Changelog

## 0.2.2
- Fix: the server answered "خطای اعتبار" (credit error) to every reservation. The website lowers its hidden "remaining credit" field by the price of each newly ticked food and the server checks it; UniMeal now does the same.
- Fix: waiting passes failed with "login page has no token" because the session was still valid; the old session is now reused.

## 0.2.1
- Swipe left/right to change tabs.
- After a preview, Home lists what would be reserved, with one button to turn preview off and reserve those now.
- Status card is coloured (green = ok, red = needs attention).

## 0.2.0
- Fix: SAMAD often opens on another self after sign-in; UniMeal now switches to تاک دامپزشکی itself (like the drop-down on the website) instead of stopping.
- Several accounts in their own tab (yours first, then friends'); saved only when you press Save.
- Log tab with every step (never contains passwords), copy and clear.
- "Open SAMAD" button in the app and in notifications that need you to finish by hand.
- Waiting for next week now ends as soon as the new week appears and is reserved.

## 0.1.0
- First version.
