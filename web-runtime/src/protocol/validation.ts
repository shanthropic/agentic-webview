import {
    RUNTIME_PROTOCOL_VERSION,
    RuntimeRequestEnvelope,
    RuntimeResponseEnvelope,
} from './types';

const METHOD_PATTERN = /^[a-z][a-z0-9]*(?:\.[a-z][a-z0-9_]*)+$/;

export class ProtocolValidationError extends Error {
    public readonly code: string;
    public readonly details: Record<string, unknown>;

    constructor(code: string, message: string, details: Record<string, unknown> = {}) {
        super(message);
        this.name = 'ProtocolValidationError';
        this.code = code;
        this.details = details;
    }
}

export function parseRequestEnvelope(raw: string, maximumMessageBytes: number): RuntimeRequestEnvelope {
    const byteCount = utf8ByteLength(raw);
    if (byteCount > maximumMessageBytes) {
        throw new ProtocolValidationError(
            'MESSAGE_TOO_LARGE',
            `Runtime request is ${byteCount} bytes; limit is ${maximumMessageBytes}`,
            { byteCount, maximumMessageBytes },
        );
    }

    let candidate: unknown;
    try {
        candidate = JSON.parse(raw);
    } catch {
        throw new ProtocolValidationError('MALFORMED_JSON', 'Runtime request is not valid JSON');
    }

    if (!isRecord(candidate)) {
        throw new ProtocolValidationError('INVALID_ENVELOPE', 'Runtime request must be a JSON object');
    }

    const protocolVersion = requireInteger(candidate.protocolVersion, 'protocolVersion');
    if (protocolVersion !== RUNTIME_PROTOCOL_VERSION) {
        throw new ProtocolValidationError(
            'PROTOCOL_VERSION_MISMATCH',
            `Expected protocol version ${RUNTIME_PROTOCOL_VERSION} but received ${protocolVersion}`,
            { expected: RUNTIME_PROTOCOL_VERSION, actual: protocolVersion },
        );
    }

    const method = requireNonBlankString(candidate.method, 'method');
    if (!METHOD_PATTERN.test(method)) {
        throw new ProtocolValidationError('INVALID_METHOD', 'Runtime method has an invalid format');
    }

    const payload = candidate.payload === undefined ? {} : candidate.payload;
    if (!isRecord(payload)) {
        throw new ProtocolValidationError('INVALID_PAYLOAD', 'Runtime payload must be a JSON object');
    }

    return {
        protocolVersion,
        bridgeToken: requireSecret(candidate.bridgeToken, 'bridgeToken'),
        sessionId: requireNonBlankString(candidate.sessionId, 'sessionId'),
        documentId: requireNonBlankString(candidate.documentId, 'documentId'),
        requestId: requireNonBlankString(candidate.requestId, 'requestId'),
        method,
        payload,
    };
}

export function encodeResponseEnvelope(
    response: RuntimeResponseEnvelope,
    maximumMessageBytes: number,
): string {
    const encoded = JSON.stringify(response);
    const byteCount = utf8ByteLength(encoded);
    if (byteCount > maximumMessageBytes) {
        const fallback: RuntimeResponseEnvelope = {
            protocolVersion: RUNTIME_PROTOCOL_VERSION,
            bridgeToken: response.bridgeToken,
            sessionId: response.sessionId,
            documentId: response.documentId,
            requestId: response.requestId,
            status: 'error',
            error: {
                code: 'RESPONSE_TOO_LARGE',
                message: `Runtime response exceeded the ${maximumMessageBytes} byte limit`,
                details: { byteCount, maximumMessageBytes },
            },
        };
        const fallbackEncoded = JSON.stringify(fallback);
        if (utf8ByteLength(fallbackEncoded) > maximumMessageBytes) {
            throw new ProtocolValidationError(
                'MESSAGE_LIMIT_TOO_SMALL',
                'Message limit is too small to encode a protocol error',
            );
        }
        return fallbackEncoded;
    }
    return encoded;
}

function requireNonBlankString(value: unknown, field: string): string {
    if (typeof value !== 'string' || value.trim().length === 0) {
        throw new ProtocolValidationError('INVALID_ENVELOPE', `${field} must be a non-blank string`);
    }
    return value;
}

function requireSecret(value: unknown, field: string): string {
    const secret = requireNonBlankString(value, field);
    if (secret.length < 32 || secret.length > 256) {
        throw new ProtocolValidationError('INVALID_ENVELOPE', `${field} must contain 32..256 characters`);
    }
    return secret;
}

function requireInteger(value: unknown, field: string): number {
    if (typeof value !== 'number' || !Number.isInteger(value)) {
        throw new ProtocolValidationError('INVALID_ENVELOPE', `${field} must be an integer`);
    }
    return value;
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function utf8ByteLength(value: string): number {
    let bytes = 0;
    for (const character of value) {
        const codePoint = character.codePointAt(0)!;
        if (codePoint <= 0x7f) bytes += 1;
        else if (codePoint <= 0x7ff) bytes += 2;
        else if (codePoint <= 0xffff) bytes += 3;
        else bytes += 4;
    }
    return bytes;
}
