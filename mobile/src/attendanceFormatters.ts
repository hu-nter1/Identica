import type { ExceptionType } from './types';

export const today = (): string => {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
};

export const prettyDate = (date: string): string => {
  const parsed = new Date(`${date}T12:00:00`);
  return parsed.toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' });
};

export const displayTime = (value: string | null): string => {
  if (!value) return '—';
  return new Date(value).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
};

export const exceptionLabels: Record<ExceptionType, string> = {
  approved_absence: 'Approved absence',
  late_checkin: 'Late check-in',
  early_checkout: 'Early check-out',
  missed_checkout: 'Missed check-out',
};

export const exceptionHelp: Record<ExceptionType, string> = {
  approved_absence: 'Excuses an absence for this date.',
  late_checkin: 'Allows check-in after the normal check-in window.',
  early_checkout: 'Allows official check-out before the configured time.',
  missed_checkout: 'Excuses a missing check-out at the end of the attendance day.',
};

export const initialDraft = {
  staffNumber: '',
  fullName: '',
  department: '',
  role: '',
  phone: '',
};

export function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : 'An unexpected error occurred.';
}
