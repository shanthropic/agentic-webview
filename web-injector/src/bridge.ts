export const BRIDGE_VERSION = 1;

declare global {
    interface Window {
        AgenticBridge?: {
            onDomUpdate(version: number, json: string): void;
            onError(errorJson: string): void;
        };
    }
}

export class Bridge {
    public notifyDomUpdate(json: string): void {
        if (window.AgenticBridge) {
            window.AgenticBridge.onDomUpdate(BRIDGE_VERSION, json);
        }
    }

    public sendError(message: string, stack?: string): void {
        if (window.AgenticBridge) {
            window.AgenticBridge.onError(JSON.stringify({ error: message, stack }));
        }
    }
}
