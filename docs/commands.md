# Command reference

| Command | Strategy | Verification |
| --- | --- | --- |
| `Click`, `LongPress` | DOM pointer/mouse sequence | expected events are received and an observable DOM, focus, value, revision, disconnection, or navigation effect follows |
| `TypeText` | native input/textarea setter or content-editable update | resulting value equals the requested postcondition |
| `SelectOption` | native select update plus input/change events | selected value matches |
| `PressKeys` | keyboard events on the deepest focused element | key down/up reach the target and produce an observable effect |
| `Scroll` | page or nearest scroll container | scroll position changes |
| `ScrollIntoView` | DOM scrolling | target intersects its viewport |

Text modes are `REPLACE_ALL`, `APPEND`, `INSERT_AT_SELECTION`, and `CLEAR`. Actions validate the active document, frame, attachment, enabled/read-only state, geometry, and occlusion before dispatch. Receipts report strategy, revisions, dispatch, verification, and whether navigation occurred. A click or key sequence that was dispatched but produced no observable postcondition returns `ActionNotVerified` with its receipt instead of claiming success.

Unsafe commands are not automatically retried. Observe again after mutation or any stale-reference failure.
