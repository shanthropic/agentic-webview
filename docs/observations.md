# Observation model

`BrowserObservation` describes one document revision. It includes the machine-readable `contentTrust = UNTRUSTED_WEBPAGE` marker, document metadata, viewport metrics, frame capabilities, semantic nodes, compact text, optional screenshot bytes, truncation, warnings, and measurement counters.

Nodes include readable content as well as controls. Actionable nodes carry `ElementRef(documentId, frameId, elementId, observedAtRevision)`. References are object identities scoped to one document/frame; they are not CSS selectors and cannot be reconstructed from text.

Observation work is bounded by visited-node, emitted-node, text, traversal-time, frame-depth, Shadow-depth, and protocol-message limits. A bounded result is returned with structured truncation rather than allowing unbounded page work.

Password controls, sensitive autocomplete fields, and elements marked `data-agentic-sensitive` are redacted. Screenshot masking is configured independently with trusted CSS selectors.
The sensitive marker applies to the complete descendant subtree, including open Shadow DOM descendants. Observed link attributes remove embedded credentials, query values, and fragments by default.
