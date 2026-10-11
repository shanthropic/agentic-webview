# Configuration cookbook

`AgenticBrowserConfiguration` defines all policies, timeouts, memory bounds, action strategies, and security rules governing an `AgenticBrowserSession`.

Configuration instances are immutable data classes. An `AgenticBrowserHost` owns exactly one configuration for its lifetime; changing configuration identity cleanly creates a fresh session so that policies cannot drift under an active document.

---

## Configuration Overview

```kotlin
data class AgenticBrowserConfiguration(
    val runtime: RuntimeConfiguration = RuntimeConfiguration(),
    val observation: ObservationConfiguration = ObservationConfiguration(),
    val actions: ActionConfiguration = ActionConfiguration(),
    val navigation: NavigationPolicy = NavigationPolicy(),
    val screenshots: ScreenshotConfiguration = ScreenshotConfiguration(),
    val diagnostics: DiagnosticsConfiguration = DiagnosticsConfiguration(),
    val experimental: ExperimentalConfiguration = ExperimentalConfiguration(),
)
```

### Validation
Always ensure configuration validity:
```kotlin
val config = AgenticBrowserConfiguration(...).requireValid()
```
If conflicting host rules exist (e.g. the same domain is both allowed and denied), `validationErrors()` returns error messages and `requireValid()` throws an `IllegalArgumentException`.

---

## The 7 Sub-Configurations

### 1. `RuntimeConfiguration`
Controls protocol bridge communication between Android Kotlin and the in-page TypeScript runtime:

| Property | Default | Valid Range | Description |
| --- | --- | --- | --- |
| `requestTimeoutMs` | `5_000` | `1..120_000` | Timeout for normal JavaScript bridge requests |
| `initializationTimeoutMs` | `10_000` | `1..120_000` | Timeout for injecting and initializing the runtime on new documents |
| `readyQuietWindowMs` | `100` | `0..10_000` | Duration with no network requests or DOM mutations before declaring page ready |
| `maximumPendingRequests` | `64` | `1..1_024` | Maximum concurrent in-flight requests before failing with `ResourceLimitExceeded` |
| `maximumMessageBytes` | `2_097_152` (2 MB) | `1_024..16_777_216` | Maximum size of an individual protocol message payload |

---

### 2. `ObservationConfiguration`
Controls semantic DOM traversal depth and memory bounding:

| Property | Default | Valid Range | Description |
| --- | --- | --- | --- |
| `maximumVisitedNodes` | `10_000` | `1..1_000_000` | Upper bound of DOM nodes visited during observation |
| `maximumEmittedNodes` | `750` | `1..100_000` | Maximum semantic nodes included in observation output (`<= maximumVisitedNodes`) |
| `maximumTotalTextCharacters` | `100_000` | `1..10_000_000` | Total text length accumulated across the page |
| `maximumTextCharactersPerNode` | `2_000` | `1..100_000` | Maximum text length extracted from an individual node |
| `maximumTraversalMs` | `1_500` | `1..30_000` | Wall-clock limit for observation traversal before truncation |
| `viewportExpansionPx` | `0` | `>= -1` | Pixels beyond visible viewport to observe (`-1` observes entire document) |
| `maximumFrameDepth` | `8` | `0..64` | Maximum recursion depth for nested same-origin `<iframe>` elements |
| `maximumShadowDepth` | `16` | `0..64` | Maximum recursion depth for nested open Shadow DOM roots |

---

### 3. `ActionConfiguration`
Configures command preparation, geometric stabilization, and pointer dispatch:

| Property | Default | Valid Range | Description |
| --- | --- | --- | --- |
| `preparationTimeoutMs` | `3_000` | `1..120_000` | Max time spent waiting for target element to attach and stabilize |
| `verificationTimeoutMs` | `3_000` | `1..120_000` | Max time spent waiting for postcondition verification after dispatch |
| `geometryStableCycles` | `2` | `1..20` | Animation frame cycles bounding box must remain steady before interaction |
| `geometryTolerancePx` | `1.0` | `0.0..100.0` | Permitted pixel variance between successive geometry measurements |
| `pointerStrategy` | `DOM_ONLY` | `DOM_ONLY`, `NATIVE_PREFERRED` | Whether to use synthetic DOM events or hardware-like Android `MotionEvent` touch synthesis |

---

### 4. `NavigationPolicy`
Security governance for URLs, hosts, schemes, and privileged browser APIs:

| Property | Default | Description |
| --- | --- | --- |
| `allowedSchemes` | `setOf("https")` | Allowed URL schemes. Must be lowercase non-blank strings. |
| `allowedHosts` | `emptySet()` | Set of allowed `HostRule` instances. If non-empty, only matching hosts are permitted. |
| `deniedHosts` | `emptySet()` | Set of denied `HostRule` instances. Matching hosts are always blocked. |
| `popupPolicy` | `PrivilegedRequestPolicy.ASK` | Policy for `window.open` and new tabs (`ALLOW`, `DENY`, `ASK`). |
| `dialogPolicy` | `PrivilegedRequestPolicy.ASK` | Policy for JavaScript `alert`, `confirm`, and `prompt`. |
| `downloadPolicy` | `PrivilegedRequestPolicy.ASK` | Policy for file downloads. |
| `permissionPolicy` | `PrivilegedRequestPolicy.DENY` | Policy for web permissions (camera, microphone, geolocation). |
| `fileChooserPolicy` | `PrivilegedRequestPolicy.ASK` | Policy for file picker `<input type="file">`. |

#### Host Rules:
- `HostRule.Exact("example.com")`: Matches exact hostname `example.com`.
- `HostRule.DomainAndSubdomains("example.com")`: Matches `example.com` and all subdomains (e.g. `api.example.com`, `auth.example.com`).

---

### 5. `ScreenshotConfiguration`
Controls in-page and hardware screenshot capture parameters:

| Property | Default | Valid Range | Description |
| --- | --- | --- | --- |
| `maximumDimensionPx` | `1_920` | `1..16_384` | Maximum width or height; larger images are scaled down |
| `jpegQuality` | `75` | `0..100` | JPEG compression quality |
| `maximumEncodedBytes` | `8_388_608` (8 MB) | `1_024..67_108_864` | Maximum allowed compressed byte size |
| `maskCssSelectors` | `emptyList()` | Up to 100 selectors (<= 500 chars) | CSS selectors painted over with solid black before capture |

---

### 6. `DiagnosticsConfiguration`
Configures internal telemetry collection:

| Property | Default | Description |
| --- | --- | --- |
| `enabled` | `false` | When true, emits structured `BrowserDiagnosticEvent` telemetry |
| `includePageContent` | `false` | When true, includes page text and attributes in diagnostic events. Keep `false` in production. |

---

### 7. `ExperimentalConfiguration`
Optional prototype flags (disabled by default):

| Property | Default | Description |
| --- | --- | --- |
| `hideWebDriverProperty` | `false` | Masks `navigator.webdriver` property in page JavaScript |
| `installChromeLikeGlobals` | `false` | Installs dummy `window.chrome` objects for compatibility |
| `forceFutureShadowRootsOpen` | `false` | Overrides `attachShadow` to force `mode: "open"` on future roots |

> [!WARNING]
> Experimental flags modify page behavior and are not an anti-detection or security guarantee.

---

## Practical Configuration Recipes

### Recipe 1: Strict Production Whitelist
```kotlin
val productionConfig = AgenticBrowserConfiguration(
    navigation = NavigationPolicy(
        allowedSchemes = setOf("https"),
        allowedHosts = setOf(
            HostRule.DomainAndSubdomains("company.com"),
            HostRule.Exact("auth.partner.org"),
        ),
        deniedHosts = setOf(
            HostRule.Exact("internal-admin.company.com"),
        ),
        permissionPolicy = PrivilegedRequestPolicy.DENY,
        popupPolicy = PrivilegedRequestPolicy.DENY,
        downloadPolicy = PrivilegedRequestPolicy.DENY,
    ),
    screenshots = ScreenshotConfiguration(
        jpegQuality = 80,
        maskCssSelectors = listOf(
            ".credit-card-input",
            "#ssn-field",
            "[data-sensitive-info]",
        ),
    ),
).requireValid()
```

### Recipe 2: Developer Debugging & Local Testing
```kotlin
val devConfig = AgenticBrowserConfiguration(
    navigation = NavigationPolicy(
        allowedSchemes = setOf("https", "http"), // Opt-in to local HTTP test fixtures
        allowedHosts = setOf(
            HostRule.Exact("localhost"),
            HostRule.Exact("127.0.0.1"),
            HostRule.Exact("10.0.2.2"), // Android emulator host loopback
        ),
    ),
    diagnostics = DiagnosticsConfiguration(
        enabled = true,
        includePageContent = true, // Permissible in controlled test environments
    ),
).requireValid()
```

### Recipe 3: Native Touch Simulation for Modern SPAs
```kotlin
val nativePointerConfig = AgenticBrowserConfiguration(
    actions = ActionConfiguration(
        pointerStrategy = PointerActionStrategy.NATIVE_PREFERRED,
        geometryStableCycles = 3,
        geometryTolerancePx = 0.5,
        verificationTimeoutMs = 5_000,
    ),
).requireValid()
```

---

## Related documentation

- [Getting started](getting-started.md)
- [Android Views integration](views.md)
- [Jetpack Compose integration](compose.md)
- [Security policies](security.md)
- [Commands & pointer strategies](commands.md)
