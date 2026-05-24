export const BRIDGE_VERSION = 1;

declare global {
    interface Window {
        AgenticBridge?: {
            onDomUpdate(token: string, version: number, json: string): void;
            onError(token: string, errorJson: string): void;
        };
    }
}

export class Bridge {
    private sessionToken: string | null = null;

    public setSessionToken(token: string): void {
        this.sessionToken = token;
    }

    public notifyDomUpdate(json: string): void {
        if (window.AgenticBridge && this.sessionToken) {
            (window.AgenticBridge as any).onDomUpdate(this.sessionToken, BRIDGE_VERSION, json);
        }
    }

    public sendError(message: string, stack?: string): void {
        if (window.AgenticBridge && this.sessionToken) {
            (window.AgenticBridge as any).onError(this.sessionToken, JSON.stringify({ error: message, stack }));
        }
    }

    public resolvePromise(promiseId: string, result: string): void {
        if (window.AgenticBridge && this.sessionToken) {
            (window.AgenticBridge as any).resolvePromise(this.sessionToken, promiseId, result);
        }
    }
}
