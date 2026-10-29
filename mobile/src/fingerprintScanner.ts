import { fingerprintScanner } from './fingerprintScannerImpl';

export type ScannerAvailability = 'ready' | 'disconnected' | 'unsupported';

export type FingerprintScannerStatus = {
  availability: ScannerAvailability;
  deviceName: string | null;
  detail: string;
};

export type FingerprintEnrollment = {
  staffNumber: string;
  fullName: string;
};

export interface FingerprintScanner {
  getStatus(): Promise<FingerprintScannerStatus>;
  enroll(input: FingerprintEnrollment): Promise<string>;
  identify(): Promise<string>;
}

export { fingerprintScanner };
