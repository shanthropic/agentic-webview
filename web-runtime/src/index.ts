import { RuntimeProtocolDispatcher } from './protocol/dispatcher';
import { RUNTIME_PROTOCOL_VERSION, RuntimeMethods } from './protocol/types';
import { SemanticRuntime } from './runtime/semanticRuntime';
import { SemanticObservationOptions } from './runtime/semanticObserver';

const bootstrapConfiguration = window.__AgenticWebRuntimeBootstrapConfiguration;
const semanticRuntime = new SemanticRuntime();
if (bootstrapConfiguration) {
    semanticRuntime.configure(bootstrapConfiguration);
    delete window.__AgenticWebRuntimeBootstrapConfiguration;
}

const protocolDispatcher = new RuntimeProtocolDispatcher({
    [RuntimeMethods.ping]: (_payload, request) => {
        semanticRuntime.activate(request.documentId);
        return {
            ready: true,
            protocolVersion: RUNTIME_PROTOCOL_VERSION,
            capabilities: semanticRuntime.capabilities(),
        };
    },
    [RuntimeMethods.configure]: (payload, request) => {
        semanticRuntime.activate(request.documentId);
        semanticRuntime.configure(payload);
        return { configured: true };
    },
    [RuntimeMethods.captureObservation]: (payload, request) =>
        semanticRuntime.capture(request.documentId, payload as Partial<SemanticObservationOptions>),
    [RuntimeMethods.executeAction]: (payload, request) => {
        const command = payload.command;
        if (typeof command !== 'object' || command === null || Array.isArray(command)) {
            throw new Error('action.execute requires a command object');
        }
        const options = typeof payload.options === 'object' && payload.options !== null && !Array.isArray(payload.options)
            ? payload.options as Record<string, unknown>
            : {};
        return semanticRuntime.execute(request.documentId, command as Record<string, unknown>, options);
    },
    [RuntimeMethods.prepareNativeClick]: (payload, request) =>
        semanticRuntime.prepareNativeClick(
            request.documentId,
            payload.target,
            typeof payload.options === 'object' && payload.options !== null && !Array.isArray(payload.options)
                ? payload.options as Record<string, unknown>
                : {},
        ),
    [RuntimeMethods.verifyNativeClick]: (payload, request) => {
        if (typeof payload.token !== 'string' || !payload.token.trim()) {
            throw new Error('action.verify_native_click requires a token');
        }
        return semanticRuntime.verifyNativeClick(request.documentId, payload.token);
    },
}, runtimeMessageLimit(bootstrapConfiguration));

export const AgenticWebRuntime = Object.freeze({
    protocolVersion: RUNTIME_PROTOCOL_VERSION,
    dispatchProtocol(requestJson: string): Promise<string> {
        return protocolDispatcher.dispatch(requestJson);
    },
});

declare global {
    interface Window {
        __AgenticWebRuntime?: typeof AgenticWebRuntime;
        __AgenticWebRuntimeBootstrapConfiguration?: Record<string, unknown>;
    }
}

Object.defineProperty(window, '__AgenticWebRuntime', {
    value: AgenticWebRuntime,
    writable: false,
    configurable: false,
    enumerable: false,
});

function runtimeMessageLimit(configuration: Record<string, unknown> | undefined): number {
    const runtime = configuration?.runtime;
    if (typeof runtime !== 'object' || runtime === null || Array.isArray(runtime)) {
        return 2 * 1024 * 1024;
    }
    const value = (runtime as Record<string, unknown>).maximumMessageBytes;
    return typeof value === 'number' && Number.isInteger(value) && value >= 1024 && value <= 16 * 1024 * 1024
        ? value
        : 2 * 1024 * 1024;
}
