import {
    RUNTIME_PROTOCOL_VERSION,
    RuntimeMethodHandler,
    RuntimeRequestEnvelope,
    RuntimeResponseEnvelope,
} from './types';
import {
    encodeResponseEnvelope,
    parseRequestEnvelope,
    ProtocolValidationError,
} from './validation';

export class RuntimeHandlerError extends Error {
    public readonly code: string;
    public readonly details: Record<string, unknown>;

    constructor(code: string, message: string, details: Record<string, unknown> = {}) {
        super(message);
        this.name = 'RuntimeHandlerError';
        this.code = code;
        this.details = details;
    }
}

export class RuntimeProtocolDispatcher {
    private readonly handlers = new Map<string, RuntimeMethodHandler>();

    constructor(
        handlers: Record<string, RuntimeMethodHandler> = {},
        private readonly maximumMessageBytes: number = 2 * 1024 * 1024,
    ) {
        if (!Number.isInteger(maximumMessageBytes) || maximumMessageBytes < 1024) {
            throw new Error('maximumMessageBytes must be an integer of at least 1024');
        }
        for (const [method, handler] of Object.entries(handlers)) {
            this.register(method, handler);
        }
    }

    public register(method: string, handler: RuntimeMethodHandler): void {
        if (this.handlers.has(method)) {
            throw new Error(`Runtime handler already registered for ${method}`);
        }
        this.handlers.set(method, handler);
    }

    public async dispatch(raw: string): Promise<string> {
        let request: RuntimeRequestEnvelope;
        try {
            request = parseRequestEnvelope(raw, this.maximumMessageBytes);
        } catch (error) {
            return this.encodeValidationFailure(raw, error);
        }

        const handler = this.handlers.get(request.method);
        if (!handler) {
            return this.encodeSafely(errorResponse(request, {
                code: 'METHOD_NOT_FOUND',
                message: `No runtime handler is registered for ${request.method}`,
                details: { method: request.method },
            }));
        }

        try {
            const result = await handler(request.payload, request);
            return this.encodeSafely({
                protocolVersion: RUNTIME_PROTOCOL_VERSION,
                bridgeToken: request.bridgeToken,
                sessionId: request.sessionId,
                documentId: request.documentId,
                requestId: request.requestId,
                status: 'success',
                result: result === undefined ? null : result,
            });
        } catch (error) {
            if (error instanceof RuntimeHandlerError) {
                return this.encodeSafely(errorResponse(request, {
                    code: error.code,
                    message: boundedMessage(error.message),
                    details: error.details,
                }));
            }
            return this.encodeSafely(errorResponse(request, {
                code: 'INTERNAL_ERROR',
                message: boundedMessage(error instanceof Error ? error.message : 'Unknown runtime error'),
                details: {},
            }));
        }
    }

    private encodeSafely(response: RuntimeResponseEnvelope): string {
        try {
            return encodeResponseEnvelope(response, this.maximumMessageBytes);
        } catch {
            return encodeResponseEnvelope({
                protocolVersion: RUNTIME_PROTOCOL_VERSION,
                bridgeToken: response.bridgeToken,
                sessionId: response.sessionId,
                documentId: response.documentId,
                requestId: response.requestId,
                status: 'error',
                error: {
                    code: 'RESPONSE_TOO_LARGE',
                    message: 'Runtime response exceeded the configured message limit',
                    details: {},
                },
            }, this.maximumMessageBytes);
        }
    }

    private encodeValidationFailure(raw: string, error: unknown): string {
        const identifiers = extractUntrustedIdentifiers(raw);
        const protocolError = error instanceof ProtocolValidationError
            ? { code: error.code, message: error.message, details: error.details }
            : { code: 'INVALID_REQUEST', message: 'Runtime request validation failed', details: {} };

        return this.encodeSafely({
            protocolVersion: RUNTIME_PROTOCOL_VERSION,
            bridgeToken: identifiers.bridgeToken,
            sessionId: identifiers.sessionId,
            documentId: identifiers.documentId,
            requestId: identifiers.requestId,
            status: 'error',
            error: protocolError,
        });
    }
}

function boundedMessage(message: string): string {
    const normalized = message.trim() || 'Runtime operation failed';
    return normalized.length <= 2_000 ? normalized : `${normalized.slice(0, 1_999)}…`;
}

function errorResponse(
    request: RuntimeRequestEnvelope,
    error: { code: string; message: string; details: Record<string, unknown> },
): RuntimeResponseEnvelope {
    return {
        protocolVersion: RUNTIME_PROTOCOL_VERSION,
        bridgeToken: request.bridgeToken,
        sessionId: request.sessionId,
        documentId: request.documentId,
        requestId: request.requestId,
        status: 'error',
        error,
    };
}

function extractUntrustedIdentifiers(raw: string): {
    sessionId: string;
    bridgeToken: string;
    documentId: string;
    requestId: string;
} {
    const fallback = { bridgeToken: 'unknown', sessionId: 'unknown', documentId: 'unknown', requestId: 'unknown' };
    try {
        const value = JSON.parse(raw) as Record<string, unknown>;
        return {
            bridgeToken: safeIdentifier(value?.bridgeToken) ?? fallback.bridgeToken,
            sessionId: safeIdentifier(value?.sessionId) ?? fallback.sessionId,
            documentId: safeIdentifier(value?.documentId) ?? fallback.documentId,
            requestId: safeIdentifier(value?.requestId) ?? fallback.requestId,
        };
    } catch {
        return fallback;
    }
}

function safeIdentifier(value: unknown): string | null {
    return typeof value === 'string' && value.trim().length > 0 && value.length <= 256
        ? value
        : null;
}
