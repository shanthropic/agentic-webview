# JSON-RPC 2.0 tool server

The `integration-jsonrpc` module provides a transport-neutral [JSON-RPC 2.0](https://www.jsonrpc.org/specification) endpoint for discovering and executing browser agent tools.

This enables exposing Agentic WebView tools to external processes, remote agent runners, or language runtimes (such as Python, Node.js, Go, or Rust) over WebSockets, HTTP, stdio, or Android IPC.

## Dependency setup

```kotlin
// build.gradle.kts
dependencies {
    implementation("dev.shantoislam.agenticwebview:browser-webview:0.3.1")
    implementation("dev.shantoislam.agenticwebview:agent-tools:0.3.1")
    implementation("dev.shantoislam.agenticwebview:integration-jsonrpc:0.3.1")
}
```

---

## API Overview

```kotlin
package dev.shantoislam.agenticwebview.integrations.jsonrpc

class JsonRpcBrowserToolServer(
    private val dispatcher: AgentToolDispatcher,
    private val json: Json = Json { ... },
) {
    suspend fun handle(requestJson: String): String?
    suspend fun handleObject(request: JsonObject): JsonObject?
}
```

- **`handle(requestJson: String): String?`**: Accepts a raw JSON string and returns a serialized JSON-RPC response string (or `null` if the request was a notification). Handles parse errors with standard `-32700` error envelopes.
- **`handleObject(request: JsonObject): JsonObject?`**: Accepts a parsed `JsonObject` and returns a response `JsonObject?`.

---

## JSON-RPC 2.0 Protocol Specification

### 1. Tool Discovery (`tools/list`)

Request:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/list"
}
```

Response:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "tools": [
      {
        "name": "browser_navigate",
        "description": "Navigate to an HTTP or HTTPS URL allowed by the host policy.",
        "inputSchema": {
          "type": "object",
          "properties": {
            "url": { "type": "string", "minLength": 1 }
          },
          "required": ["url"],
          "additionalProperties": false
        },
        "outputSchema": { ... }
      },
      {
        "name": "browser_click",
        "description": "Click an actionable element from the latest observation.",
        "inputSchema": { ... },
        "outputSchema": { ... }
      }
    ]
  }
}
```

---

### 2. Tool Execution (`tools/call`)

Request:
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "browser_click",
    "arguments": {
      "target": {
        "documentId": "doc_1",
        "frameId": "frame_0",
        "elementId": "submit_btn",
        "observedAtRevision": 1
      }
    }
  }
}
```

Response:
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "success": true,
    "output": {
      "status": "success",
      "summary": "Browser action completed",
      "value": {
        "commandType": "click",
        "strategy": "DOM_POINTER",
        "documentId": "doc_1",
        "revisionBefore": 1,
        "revisionAfter": 2,
        "dispatched": true,
        "verified": true,
        "pageChanged": true
      },
      "recommendObservation": true,
      "context": {
        "documentId": "doc_1",
        "revision": 2
      },
      "diagnostics": {
        "durationMs": 32,
        "strategy": "DOM_POINTER",
        "attempts": 1,
        "warnings": []
      }
    }
  }
}
```

---

### 3. Error Codes

When protocol errors occur, `JsonRpcBrowserToolServer` returns standard JSON-RPC 2.0 error envelopes:

| Code | Message | Cause |
| --- | --- | --- |
| `-32700` | Parse error | Request string is not valid JSON |
| `-32600` | Invalid Request | Missing `"jsonrpc": "2.0"`, missing `"method"`, or malformed structure |
| `-32601` | Method not found | Method is neither `"tools/list"` nor `"tools/call"` |
| `-32602` | Invalid params | Missing `"params"`, `"params.name"`, or `"params.arguments"` is not an object |

Tool-level execution failures (such as `STALE_ELEMENT` or `NAVIGATION_BLOCKED`) are returned as successful JSON-RPC results with `success: false` and the structured error output in `result.output` (see [Framework-neutral agent tools](agent-tools.md)).

---

## Practical Integration Recipe: Ktor WebSocket Server

Here is an example hosting `JsonRpcBrowserToolServer` inside a lightweight in-app Ktor WebSocket server:

```kotlin
package com.example.app

import dev.shantoislam.agenticwebview.api.AgenticBrowserSession
import dev.shantoislam.agenticwebview.integrations.jsonrpc.JsonRpcBrowserToolServer
import dev.shantoislam.agenticwebview.tools.BrowserToolProfile
import dev.shantoislam.agenticwebview.tools.StandardBrowserTools
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send

class BrowserRpcServer(session: AgenticBrowserSession, port: Int = 8080) {

    private val tools = StandardBrowserTools(session, BrowserToolProfile.STANDARD)
    private val rpcServer = JsonRpcBrowserToolServer(tools)

    private val server = embeddedServer(Netty, port = port) {
        install(WebSockets)
        routing {
            webSocket("/browser-rpc") {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val requestJson = frame.readText()
                        val responseJson = rpcServer.handle(requestJson)
                        if (responseJson != null) {
                            send(Frame.Text(responseJson))
                        }
                    }
                }
            }
        }
    }

    fun start() = server.start(wait = false)
    fun stop() = server.stop(1000, 2000)
}
```

---

## Related documentation

- [Framework-neutral agent tools](agent-tools.md)
- [Koog agent integration](koog.md)
- [Command reference](commands.md)
- [Getting started](getting-started.md)
