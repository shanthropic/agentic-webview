# Navigation and WebView security

Configure allowed schemes and host rules before creating the host:

```kotlin
val policy = NavigationPolicy(
    allowedSchemes = setOf("https"),
    allowedHosts = setOf(HostRule.DomainAndSubdomains("example.com")),
    deniedHosts = setOf(HostRule.Exact("admin.example.com")),
    permissionPolicy = PrivilegedRequestPolicy.DENY,
)
```

Host matching is normalized, IDN-aware, and boundary-safe. A domain rule matches the domain and its subdomains, not lookalike suffixes. Allow/deny conflicts fail configuration validation.

The default navigation policy allows HTTPS only. Plain HTTP is an explicit opt-in for local fixtures or a host application that has separately accepted the cleartext transport risk.

File and content access are disabled; mixed content is blocked. TLS errors are always cancelled and Safe Browsing hits return to safety. Dialogs, popups, downloads, permissions, and file selection follow explicit policy and `BrowserHostDelegate` decisions. Popup allowance redirects the created browsing context into the current governed session instead of creating an unmanaged agent browser.

The optional experimental page patches are off by default and must not be described as a security or anti-detection guarantee.
