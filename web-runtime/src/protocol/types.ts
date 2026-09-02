export const RUNTIME_PROTOCOL_VERSION = 1 as const;

export type RuntimeResponseStatus = 'success' | 'error';

export interface RuntimeRequestEnvelope {
    protocolVersion: number;
    bridgeToken: string;
    sessionId: string;
    documentId: string;
    requestId: string;
    method: string;
    payload: Record<string, unknown>;
}

export interface RuntimeProtocolError {
    code: string;
    message: string;
    details: Record<string, unknown>;
}

export interface RuntimeResponseEnvelope {
    protocolVersion: number;
    bridgeToken: string;
    sessionId: string;
    documentId: string;
    requestId: string;
    status: RuntimeResponseStatus;
    result?: unknown;
    error?: RuntimeProtocolError;
}

export type RuntimeMethodHandler = (
    payload: Record<string, unknown>,
    request: RuntimeRequestEnvelope,
) => unknown | Promise<unknown>;

export const RuntimeMethods = {
    ping: 'system.ping',
    configure: 'runtime.configure',
    captureObservation: 'observation.capture',
    executeAction: 'action.execute',
    prepareNativeClick: 'action.prepare_native_click',
    verifyNativeClick: 'action.verify_native_click',
} as const;
