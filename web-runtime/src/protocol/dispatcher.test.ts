import { RuntimeHandlerError, RuntimeProtocolDispatcher } from './dispatcher';
import { RUNTIME_PROTOCOL_VERSION, RuntimeMethods, RuntimeResponseEnvelope } from './types';
import { readFileSync } from 'fs';
import { resolve } from 'path';

function request(overrides: Record<string, unknown> = {}): string {
    return JSON.stringify({
        protocolVersion: RUNTIME_PROTOCOL_VERSION,
        bridgeToken: 'bridge-token-12345678901234567890123456789012',
        sessionId: 'session-1',
        documentId: 'document-1',
        requestId: 'request-1',
        method: RuntimeMethods.ping,
        payload: {},
        ...overrides,
    });
}

describe('RuntimeProtocolDispatcher', () => {
    it('matches the shared version-one ping fixtures', async () => {
        const fixture = (name: string): string => readFileSync(
            resolve(process.cwd(), '..', 'protocol-fixtures', 'v1', name),
            'utf8',
        );
        const dispatcher = new RuntimeProtocolDispatcher({
            [RuntimeMethods.ping]: () => ({ ready: true }),
        });

        const actual = JSON.parse(await dispatcher.dispatch(fixture('request-ping.json')));
        const expected = JSON.parse(fixture('response-ping-success.json'));

        expect(actual).toEqual(expected);
    });
    it('dispatches a valid request and preserves correlation fields', async () => {
        const dispatcher = new RuntimeProtocolDispatcher({
            [RuntimeMethods.ping]: () => ({ ready: true }),
        });

        const response = JSON.parse(await dispatcher.dispatch(request())) as RuntimeResponseEnvelope;

        expect(response).toEqual({
            protocolVersion: 1,
            bridgeToken: 'bridge-token-12345678901234567890123456789012',
            sessionId: 'session-1',
            documentId: 'document-1',
            requestId: 'request-1',
            status: 'success',
            result: { ready: true },
        });
    });

    it('rejects unknown methods with a structured error', async () => {
        const dispatcher = new RuntimeProtocolDispatcher();

        const response = JSON.parse(await dispatcher.dispatch(request())) as RuntimeResponseEnvelope;

        expect(response.status).toBe('error');
        expect(response.error?.code).toBe('METHOD_NOT_FOUND');
    });

    it('rejects protocol version mismatches', async () => {
        const dispatcher = new RuntimeProtocolDispatcher();

        const response = JSON.parse(await dispatcher.dispatch(request({ protocolVersion: 99 }))) as RuntimeResponseEnvelope;

        expect(response.status).toBe('error');
        expect(response.error?.code).toBe('PROTOCOL_VERSION_MISMATCH');
        expect(response.requestId).toBe('request-1');
    });

    it('rejects malformed JSON without throwing', async () => {
        const dispatcher = new RuntimeProtocolDispatcher();

        const response = JSON.parse(await dispatcher.dispatch('{broken')) as RuntimeResponseEnvelope;

        expect(response.status).toBe('error');
        expect(response.error?.code).toBe('MALFORMED_JSON');
        expect(response.requestId).toBe('unknown');
    });

    it('maps expected handler failures without losing details', async () => {
        const dispatcher = new RuntimeProtocolDispatcher({
            [RuntimeMethods.ping]: () => {
                throw new RuntimeHandlerError('NOT_READY', 'Runtime is not ready', { phase: 'initializing' });
            },
        });

        const response = JSON.parse(await dispatcher.dispatch(request())) as RuntimeResponseEnvelope;

        expect(response.status).toBe('error');
        expect(response.error).toEqual({
            code: 'NOT_READY',
            message: 'Runtime is not ready',
            details: { phase: 'initializing' },
        });
    });

    it('returns a correlated error when a handler result exceeds the response budget', async () => {
        const dispatcher = new RuntimeProtocolDispatcher({
            [RuntimeMethods.ping]: () => ({ value: 'x'.repeat(2_000) }),
        }, 1_024);

        const response = JSON.parse(await dispatcher.dispatch(request())) as RuntimeResponseEnvelope;

        expect(response.requestId).toBe('request-1');
        expect(response.status).toBe('error');
        expect(response.error?.code).toBe('RESPONSE_TOO_LARGE');
    });

    it('rejects duplicate handler registration', () => {
        const dispatcher = new RuntimeProtocolDispatcher({
            [RuntimeMethods.ping]: () => null,
        });

        expect(() => dispatcher.register(RuntimeMethods.ping, () => null)).toThrow(
            'Runtime handler already registered',
        );
    });
});
