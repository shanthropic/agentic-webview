# ADR 0001: Replace the prototype without compatibility

- Status: accepted
- Date: 2026-09-01

## Decision

Replace the prototype controller/custom-view architecture with separate contract, WebView, Compose, generic-tool, adapter, and page-runtime modules. Delete the former API, parser, selector/hash identity, bridge, and duplicated configuration rather than maintain an adapter façade.

## Rationale

The prototype mixed UI ownership, browser behavior, agent formatting, and runtime transport. Compatibility would preserve two lifecycles and two error/configuration models, making cancellation, security policy, and element identity ambiguous. The project is still in development, so a clean break has lower long-term risk.

## Consequences

Consumers must migrate to `AgenticBrowserHost` and `AgenticBrowserSession`. Every behavior is now typed and every framework integration goes through `AgentToolDispatcher`. Protocol and artifact versioning become explicit. The repository carries no deprecated prototype path.
