import type {
  FingerprintEnrollment,
  FingerprintScanner,
  FingerprintScannerStatus,
} from './fingerprintScanner';

type BridgeStatus = {
  protocolVersion: string;
  paired: boolean;
  connected: boolean;
  deviceName?: string;
  capabilities?: string[];
};

type BridgeEnrollment = {
  credentialId: string;
};

type BridgeIdentification = {
  staffNumber: string;
};

const bridgeUrl = 'http://127.0.0.1:38721/v1';
const requestTimeoutMs = 1800;

class LocalFingerprintBridge implements FingerprintScanner {
  async getStatus(): Promise<FingerprintScannerStatus> {
    try {
      const status = await this.request<BridgeStatus>('/status', { method: 'GET' });
      if (status.protocolVersion !== '1') {
        return {
          availability: 'unsupported',
          deviceName: status.deviceName ?? null,
          detail: 'The local scanner bridge uses an unsupported protocol version.',
        };
      }

      if (!status.paired) {
        return {
          availability: 'disconnected',
          deviceName: status.deviceName ?? null,
          detail: 'The desktop scanner bridge is not paired with this Identica app.',
        };
      }

      if (!status.connected) {
        return {
          availability: 'disconnected',
          deviceName: status.deviceName ?? null,
          detail: 'Scanner bridge found, but no supported USB fingerprint reader is connected.',
        };
      }

      const capabilities = new Set(status.capabilities ?? []);
      if (!capabilities.has('enroll') || !capabilities.has('identify')) {
        return {
          availability: 'unsupported',
          deviceName: status.deviceName ?? null,
          detail: 'The connected scanner bridge does not support enrollment and identification.',
        };
      }

      return {
        availability: 'ready',
        deviceName: status.deviceName ?? 'USB fingerprint reader',
        detail: 'Scanner bridge connected.',
      };
    } catch {
      return {
        availability: 'disconnected',
        deviceName: null,
        detail: 'Desktop scanner bridge is not installed or running on this computer.',
      };
    }
  }

  async enroll(input: FingerprintEnrollment): Promise<string> {
    const result = await this.request<BridgeEnrollment>('/enroll', {
      method: 'POST',
      body: JSON.stringify(input),
    });
    if (!result.credentialId?.trim()) {
      throw new Error('The scanner bridge returned no enrollment reference.');
    }
    return result.credentialId;
  }

  async identify(): Promise<string> {
    const result = await this.request<BridgeIdentification>('/identify', {
      method: 'POST',
      body: JSON.stringify({}),
    });
    if (!result.staffNumber?.trim()) {
      throw new Error('The scanner bridge did not identify a registered staff number.');
    }
    return result.staffNumber;
  }

  private async request<T>(path: string, init: RequestInit): Promise<T> {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), requestTimeoutMs);
    try {
      const response = await fetch(`${bridgeUrl}${path}`, {
        ...init,
        signal: controller.signal,
        headers: {
          Accept: 'application/json',
          ...(init.body ? { 'Content-Type': 'application/json' } : {}),
          ...init.headers,
        },
      });
      if (!response.ok) throw new Error(`Scanner bridge request failed (${response.status}).`);
      return await response.json() as T;
    } finally {
      clearTimeout(timeout);
    }
  }
}

export const fingerprintScanner: FingerprintScanner = new LocalFingerprintBridge();
