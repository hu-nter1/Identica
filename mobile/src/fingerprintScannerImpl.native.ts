import { Platform } from 'react-native';

import type {
  FingerprintEnrollment,
  FingerprintScanner,
  FingerprintScannerStatus,
} from './fingerprintScanner';

class NativeFingerprintScanner implements FingerprintScanner {
  async getStatus(): Promise<FingerprintScannerStatus> {
    if (Platform.OS === 'android') {
      return {
        availability: 'disconnected',
        deviceName: null,
        detail: 'Android scanner adapter is prepared, but the SecuGen Android SDK is not installed.',
      };
    }

    return {
      availability: 'unsupported',
      deviceName: null,
      detail: 'This USB scanner adapter is not available on iOS.',
    };
  }

  async enroll(_input: FingerprintEnrollment): Promise<string> {
    throw new Error('Fingerprint enrollment is unavailable until the platform scanner SDK is installed.');
  }

  async identify(): Promise<string> {
    throw new Error('Fingerprint identification is unavailable until the platform scanner SDK is installed.');
  }
}

export const fingerprintScanner: FingerprintScanner = new NativeFingerprintScanner();
