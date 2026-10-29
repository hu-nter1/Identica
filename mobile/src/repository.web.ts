import type {
  Attendance,
  AttendanceException,
  AttendanceStatus,
  ExceptionType,
  NewException,
  NewStaff,
  Schedule,
  Staff,
} from './types';
import type { AttendanceRepository } from './repository';

type WebState = {
  staff: Staff[];
  attendance: Attendance[];
  exceptions: AttendanceException[];
  schedule: Schedule;
  closedDays: string[];
  lastClosedDate: string;
  nextStaffId: number;
  nextAttendanceId: number;
  nextExceptionId: number;
};

const databaseName = 'identica-attendance';
const storeName = 'app-state';
const stateKey = 'state';
const defaultSchedule: Schedule = {
  checkInStart: '06:00',
  checkInEnd: '08:30',
  earliestCheckout: '15:00',
};

const localDate = (date: Date): string => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

const localTime = (date: Date): string =>
  `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;

const isoTimestamp = (date: Date): string => date.toISOString();

const validTime = (value: string): boolean => /^([01]\d|2[0-3]):[0-5]\d$/.test(value);

const validDate = (value: string): boolean => {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T12:00:00`);
  return !Number.isNaN(date.getTime()) && localDate(date) === value;
};

const shiftDate = (date: string, days: number): string => {
  const next = new Date(`${date}T12:00:00`);
  next.setDate(next.getDate() + days);
  return localDate(next);
};

const emptyState = (date: string): WebState => ({
  staff: [],
  attendance: [],
  exceptions: [],
  schedule: { ...defaultSchedule },
  closedDays: [],
  lastClosedDate: date,
  nextStaffId: 1,
  nextAttendanceId: 1,
  nextExceptionId: 1,
});

class BrowserAttendanceRepository implements AttendanceRepository {
  private database: IDBDatabase | null = null;
  private state: WebState | null = null;
  private initialization: Promise<void> | null = null;
  private mutationQueue: Promise<void> = Promise.resolve();

  async initialize(): Promise<void> {
    if (!this.initialization) this.initialization = this.loadState();
    await this.initialization;
    const now = new Date();
    await this.reconcileClosedDays(now);
    await this.reconcileMissedCheckIn(now);
  }

  async getStaff(): Promise<Staff[]> {
    return this.requireState().staff
      .filter((person) => person.active)
      .slice()
      .map((person) => ({ ...person, fingerprintCredentialId: person.fingerprintCredentialId ?? null }))
      .sort((left, right) => left.fullName.localeCompare(right.fullName));
  }

  async addStaff(input: NewStaff): Promise<Staff> {
    const staffNumber = input.staffNumber.trim();
    const fullName = input.fullName.trim();
    if (!staffNumber || !fullName || !input.department.trim() || !input.role.trim()) {
      throw new Error('Staff number, full name, department, and role are required.');
    }

    let created: Staff | null = null;
    await this.mutate((state) => {
      if (state.staff.some((person) => person.staffNumber.toLowerCase() === staffNumber.toLowerCase())) {
        throw new Error('That staff number is already registered.');
      }
      created = {
        ...input,
        id: state.nextStaffId++,
        staffNumber,
        fullName,
        department: input.department.trim(),
        role: input.role.trim(),
        phone: input.phone.trim(),
        fingerprintCredentialId: null,
        active: true,
      };
      state.staff.push(created);
    });
    if (!created) throw new Error('The saved staff member could not be loaded.');
    return created;
  }

  async saveFingerprintCredential(staffId: number, credentialId: string): Promise<void> {
    if (!credentialId.trim()) throw new Error('A scanner enrollment reference is required.');
    await this.mutate((state) => {
      const staff = state.staff.find((person) => person.id === staffId && person.active);
      if (!staff) throw new Error('The selected staff member could not be found.');
      staff.fingerprintCredentialId = credentialId.trim();
    });
  }

  async getSchedule(): Promise<Schedule> {
    return { ...this.requireState().schedule };
  }

  async saveSchedule(schedule: Schedule): Promise<void> {
    if (
      !validTime(schedule.checkInStart) ||
      !validTime(schedule.checkInEnd) ||
      !validTime(schedule.earliestCheckout) ||
      schedule.checkInStart > schedule.checkInEnd
    ) {
      throw new Error('Enter valid 24-hour times and make sure check-in starts before it ends.');
    }
    await this.mutate((state) => {
      state.schedule = { ...schedule };
    });
  }

  async getAttendance(date: string): Promise<Attendance[]> {
    return this.requireState().attendance
      .filter((record) => record.date === date)
      .map((record) => {
        if (!isAttendanceStatus(record.status)) {
          throw new Error(`Unknown attendance status: ${record.status}`);
        }
        return { ...record };
      })
      .sort((left, right) => left.fullName.localeCompare(right.fullName));
  }

  async checkIn(staffId: number, now = new Date()): Promise<void> {
    const date = localDate(now);
    const time = localTime(now);
    const schedule = this.requireState().schedule;
    if (time < schedule.checkInStart) {
      throw new Error(`Check-in opens at ${schedule.checkInStart}.`);
    }
    const late = time > schedule.checkInEnd;
    if (late && !this.hasException(staffId, date, 'late_checkin')) {
      await this.mutate((state) => {
        if (!state.attendance.some((record) => record.staffId === staffId && record.date === date)) {
          state.attendance.push(this.newAttendance(state, staffId, date, 'absent'));
        }
      });
      throw new Error(`The check-in window ended at ${schedule.checkInEnd}. This staff member is marked absent; an admin must record a late check-in exception to continue.`);
    }

    await this.mutate((state) => {
      const existing = state.attendance.find((record) => record.staffId === staffId && record.date === date);
      if (existing?.checkInAt) throw new Error('This staff member has already checked in today.');
      if (existing) {
        existing.checkInAt = isoTimestamp(now);
        existing.status = late ? 'late' : 'present';
      } else {
        state.attendance.push(this.newAttendance(state, staffId, date, late ? 'late' : 'present', isoTimestamp(now)));
      }
    });
  }

  async checkOut(staffId: number, now = new Date()): Promise<void> {
    const date = localDate(now);
    const schedule = this.requireState().schedule;
    await this.mutate((state) => {
      const attendance = state.attendance.find((record) => record.staffId === staffId && record.date === date);
      if (!attendance?.checkInAt) throw new Error('A check-in is required before check-out.');
      if (attendance.checkOutAt) throw new Error('This staff member has already checked out today.');
      if (
        localTime(now) < schedule.earliestCheckout &&
        !this.hasExceptionIn(state, staffId, date, 'early_checkout')
      ) {
        throw new Error(`Official check-out opens at ${schedule.earliestCheckout}. An admin exception is required for an earlier check-out.`);
      }
      attendance.checkOutAt = isoTimestamp(now);
    });
  }

  async getExceptions(date?: string): Promise<AttendanceException[]> {
    return this.requireState().exceptions
      .filter((item) => date === undefined || item.date === date)
      .slice()
      .sort((left, right) => right.createdAt.localeCompare(left.createdAt));
  }

  async addException(input: NewException): Promise<void> {
    if (!input.reason.trim()) throw new Error('An admin reason is required for every exception.');
    if (!validDate(input.date)) throw new Error('Choose a valid attendance date.');
    await this.mutate((state) => {
      const person = state.staff.find((staff) => staff.id === input.staffId && staff.active);
      if (!person) throw new Error('The selected staff member could not be found.');
      state.exceptions.push({
        ...input,
        id: state.nextExceptionId++,
        staffName: person.fullName,
        reason: input.reason.trim(),
        createdAt: isoTimestamp(new Date()),
      });

      if (input.type === 'approved_absence') {
        const attendance = state.attendance.find(
          (record) => record.staffId === input.staffId && record.date === input.date,
        );
        if (attendance && !attendance.checkInAt) attendance.status = 'excused';
        else if (!attendance) state.attendance.push(this.newAttendance(state, input.staffId, input.date, 'excused'));
      } else if (input.type === 'missed_checkout') {
        const attendance = state.attendance.find(
          (record) => record.staffId === input.staffId && record.date === input.date,
        );
        if (attendance?.checkInAt && !attendance.checkOutAt) attendance.status = 'excused';
      }
    });
  }

  private async loadState(): Promise<void> {
    this.database = await openDatabase();
    const transaction = this.database.transaction(storeName, 'readonly');
    const request = transaction.objectStore(storeName).get(stateKey) as IDBRequest<WebState | undefined>;
    const savedState = await requestResult(request);
    this.state = savedState ?? emptyState(localDate(new Date()));
    if (!savedState) await this.persist(this.state);
  }

  private requireState(): WebState {
    if (!this.state) throw new Error('The browser attendance database has not been initialized.');
    return this.state;
  }

  private async mutate(operation: (state: WebState) => void): Promise<void> {
    const mutation = this.mutationQueue.then(async () => {
      const next = JSON.parse(JSON.stringify(this.requireState())) as WebState;
      operation(next);
      await this.persist(next);
      this.state = next;
    });
    this.mutationQueue = mutation.then(() => undefined, () => undefined);
    await mutation;
  }

  private async persist(state: WebState): Promise<void> {
    const database = this.database;
    if (!database) throw new Error('The browser attendance database is not available.');
    await transactionResult(database, storeName, (store) => {
      store.put(state, stateKey);
    });
  }

  private newAttendance(
    state: WebState,
    staffId: number,
    date: string,
    status: AttendanceStatus,
    checkInAt: string | null = null,
  ): Attendance {
    const person = state.staff.find((staff) => staff.id === staffId);
    if (!person) throw new Error('The selected staff member could not be found.');
    return {
      id: state.nextAttendanceId++,
      staffId,
      staffNumber: person.staffNumber,
      fullName: person.fullName,
      photoUri: person.photoUri,
      date,
      checkInAt,
      checkOutAt: null,
      status,
    };
  }

  private hasException(staffId: number, date: string, type: ExceptionType): boolean {
    return this.hasExceptionIn(this.requireState(), staffId, date, type);
  }

  private hasExceptionIn(state: WebState, staffId: number, date: string, type: ExceptionType): boolean {
    return state.exceptions.some(
      (exception) =>
        exception.staffId === staffId &&
        exception.date === date &&
        exception.type === type,
    );
  }

  private async reconcileClosedDays(now: Date): Promise<void> {
    const today = localDate(now);
    let cursor = this.requireState().lastClosedDate;
    while (cursor < today) {
      await this.closeDay(cursor);
      cursor = shiftDate(cursor, 1);
    }
    if (localTime(now) === '23:59') await this.closeDay(today);
  }

  private async reconcileMissedCheckIn(now: Date): Promise<void> {
    const date = localDate(now);
    const state = this.requireState();
    if (localTime(now) <= state.schedule.checkInEnd) return;
    await this.mutate((next) => {
      for (const person of next.staff.filter((staff) => staff.active)) {
        const attendance = next.attendance.find((record) => record.staffId === person.id && record.date === date);
        if (!attendance) next.attendance.push(this.newAttendance(next, person.id, date, 'absent'));
        else if (!attendance.checkInAt) {
          attendance.status = this.hasExceptionIn(next, person.id, date, 'approved_absence') ? 'excused' : 'absent';
        }
      }
    });
  }

  private async closeDay(date: string): Promise<void> {
    await this.mutate((state) => {
      if (state.closedDays.includes(date)) {
        state.lastClosedDate = date;
        return;
      }
      for (const person of state.staff.filter((staff) => staff.active)) {
        let attendance = state.attendance.find((record) => record.staffId === person.id && record.date === date);
        if (!attendance) {
          attendance = this.newAttendance(
            state,
            person.id,
            date,
            this.hasExceptionIn(state, person.id, date, 'approved_absence') ? 'excused' : 'absent',
          );
          state.attendance.push(attendance);
        } else if (attendance.checkInAt && !attendance.checkOutAt) {
          attendance.status = this.hasExceptionIn(state, person.id, date, 'missed_checkout')
            ? 'excused'
            : 'absent_no_checkout';
        } else if (!attendance.checkInAt && this.hasExceptionIn(state, person.id, date, 'approved_absence')) {
          attendance.status = 'excused';
        }
      }
      state.closedDays.push(date);
      state.lastClosedDate = date;
    });
  }
}

function isAttendanceStatus(value: string): value is AttendanceStatus {
  return value === 'present' ||
    value === 'late' ||
    value === 'absent' ||
    value === 'absent_no_checkout' ||
    value === 'excused';
}

function openDatabase(): Promise<IDBDatabase> {
  if (typeof indexedDB === 'undefined') {
    return Promise.reject(new Error('IndexedDB is unavailable in this browser.'));
  }
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(databaseName, 1);
    request.onupgradeneeded = () => {
      request.result.createObjectStore(storeName);
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error('Could not open browser storage.'));
    request.onblocked = () => reject(new Error('Browser storage is blocked by another tab.'));
  });
}

function requestResult<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error('Could not read browser storage.'));
  });
}

function transactionResult(
  database: IDBDatabase,
  store: string,
  write: (objectStore: IDBObjectStore) => void,
): Promise<void> {
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(store, 'readwrite');
    transaction.oncomplete = () => resolve();
    transaction.onerror = () => reject(transaction.error ?? new Error('Could not write browser storage.'));
    transaction.onabort = () => reject(transaction.error ?? new Error('Browser storage write was cancelled.'));
    write(transaction.objectStore(store));
  });
}

export const attendanceRepository: AttendanceRepository = new BrowserAttendanceRepository();

export async function saveCapturedPhoto(sourceUri: string): Promise<string> {
  const response = await fetch(sourceUri);
  if (!response.ok) throw new Error('Could not read the captured photo.');
  const blob = await response.blob();
  const objectUrl = URL.createObjectURL(blob);
  try {
    const image = new Image();
    await new Promise<void>((resolve, reject) => {
      image.onload = () => resolve();
      image.onerror = () => reject(new Error('Could not open the captured photo.'));
      image.src = objectUrl;
    });
    const maxDimension = 480;
    const scale = Math.min(1, maxDimension / Math.max(image.naturalWidth, image.naturalHeight));
    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(image.naturalWidth * scale));
    canvas.height = Math.max(1, Math.round(image.naturalHeight * scale));
    const context = canvas.getContext('2d');
    if (!context) throw new Error('The browser cannot process the captured photo.');
    context.drawImage(image, 0, 0, canvas.width, canvas.height);
    return canvas.toDataURL('image/jpeg', 0.72);
  } finally {
    URL.revokeObjectURL(objectUrl);
  }
}
