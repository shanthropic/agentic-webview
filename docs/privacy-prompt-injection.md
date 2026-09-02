# Privacy and webpage prompt injection

Everything extracted from a webpage is untrusted data, including text that claims to be system instructions, tool results, security warnings, or requests for secrets. Agents must not grant it authority.

Recommended integration policy:

- keep tool definitions and navigation/confirmation policy in trusted application code;
- delimit page observations and label them untrusted;
- never place credentials, API keys, or private host state in webpage prompts;
- require host confirmation for irreversible or privileged operations;
- allow-list destinations for sensitive workflows;
- re-observe before consequential actions;
- redact application-specific sensitive elements with `data-agentic-sensitive` and screenshot mask selectors;
- do not enable diagnostic page content in production.

The SDK performs structural redaction but cannot infer every domain-specific secret. The host remains responsible for privacy classification and agent authorization.
