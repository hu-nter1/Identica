import * as FileSystem from 'expo-file-system/legacy';
import * as SQLite from 'expo-sqlite';

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

export interface AttendanceRepository {
  initialize(): Promise<void>;
  getStaff(): Promise<Staff[]>;
  addStaff(input: NewStaff): Promise<Staff>;
  getSchedule(): Promise<Schedule>;
  saveSchedule(schedule: Schedule): Promise<void>;
  getAttendance(date: string): Promise<Attendance[]>;
  checkIn(staffId: number, now?: Date): Promise<void>;
  checkOut(staffId: number, now?: Date): Promise<void>;
  getExceptions(date?: string): Promise<AttendanceException[]>;
  addException(input: NewException): Promise<void>;
}

const database = SQLite.openDatabaseSync('identica-attendance.db');
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

const parseStatus = (value: string): AttendanceStatus => {
  switch (value) {
    case 'present':
    case 'late':
    case 'absent':
    case 'absent_no_checkout':
    case 'excused':
      return value;
    default:
      throw new Error(`Unknown attendance status: ${value}`);
  }
};

const validTime = (value: string): boolean => /^([01]\d|2[0-3]):[0-5]\d$/.test(value);

class LocalAttendanceStore {
  initialize(): void {
    database.execSync(`
      PRAGMA foreign_keys = ON;
      CREATE TABLE IF NOT EXISTS staff (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        staff_number TEXT NOT NULL UNIQUE,
        full_name TEXT NOT NULL,
        department TEXT NOT NULL,
        role TEXT NOT NULL,
        phone TEXT NOT NULL,
        photo_uri TEXT,
        fingerprint_enrolled INTEGER NOT NULL DEFAULT 0,
        active INTEGER NOT NULL DEFAULT 1,
        registered_at TEXT NOT NULL
      );
      CREATE TABLE IF NOT EXISTS attendance (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        staff_id INTEGER NOT NULL REFERENCES staff(id),
        attendance_date TEXT NOT NULL,
        check_in_at TEXT,
        check_out_at TEXT,
        status TEXT NOT NULL,
        UNIQUE(staff_id, attendance_date)
      );
      CREATE TABLE IF NOT EXISTS attendance_exceptions (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        staff_id INTEGER NOT NULL REFERENCES staff(id),
        attendance_date TEXT NOT NULL,
        type TEXT NOT NULL,
        reason TEXT NOT NULL,
        created_at TEXT NOT NULL
      );
      CREATE TABLE IF NOT EXISTS app_settings (
        setting_key TEXT PRIMARY KEY,
        setting_value TEXT NOT NULL
      );
      CREATE TABLE IF NOT EXISTS closed_attendance_days (
        attendance_date TEXT PRIMARY KEY,
        closed_at TEXT NOT NULL
      );
    `);

    database.withTransactionSync(() => {
      for (const [key, value] of Object.entries(defaultSchedule)) {
        database.runSync(
          'INSERT OR IGNORE INTO app_settings (setting_key, setting_value) VALUES (?, ?)',
          key,
          value,
        );
      }

      const lastClosed = database.getFirstSync<{ setting_value: string }>(
        "SELECT setting_value FROM app_settings WHERE setting_key = 'last_closed_date'",
      );
      if (!lastClosed) {
        database.runSync(
          'INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?)',
          'last_closed_date',
          localDate(new Date()),
        );
      }
    });

    const now = new Date();
    this.reconcileClosedDays(now);
    this.reconcileMissedCheckIn(now);
  }

  getStaff(): Staff[] {
    return database.getAllSync<{
      id: number;
      staff_number: string;
      full_name: string;
      department: string;
      role: string;
      phone: string;
      photo_uri: string | null;
      fingerprint_enrolled: number;
      active: number;
    }>('SELECT * FROM staff WHERE active = 1 ORDER BY full_name COLLATE NOCASE').map((row) => ({
      id: row.id,
      staffNumber: row.staff_number,
      fullName: row.full_name,
      department: row.department,
      role: row.role,
      phone: row.phone,
      photoUri: row.photo_uri,
      fingerprintEnrolled: row.fingerprint_enrolled === 1,
      active: row.active === 1,
    }));
  }

  addStaff(input: NewStaff): Staff {
    const staffNumber = input.staffNumber.trim();
    const fullName = input.fullName.trim();
    if (!staffNumber || !fullName || !input.department.trim() || !input.role.trim()) {
      throw new Error('Staff number, full name, department, and role are required.');
    }
    if (
      database.getFirstSync<{ id: number }>(
        'SELECT id FROM staff WHERE staff_number = ?',
        staffNumber,
      )
    ) {
      throw new Error('That staff number is already registered.');
    }

    const result = database.runSync(
      `INSERT INTO staff
       (staff_number, full_name, department, role, phone, photo_uri, fingerprint_enrolled, registered_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      staffNumber,
      fullName,
      input.department.trim(),
      input.role.trim(),
      input.phone.trim(),
      input.photoUri,
      input.fingerprintEnrolled ? 1 : 0,
      isoTimestamp(new Date()),
    );
    const staff = this.getStaff().find((person) => person.id === result.lastInsertRowId);
    if (!staff) throw new Error('The saved staff member could not be loaded.');
    return staff;
  }

  getSchedule(): Schedule {
    const values = database.getAllSync<{ setting_key: string; setting_value: string }>(
      `SELECT setting_key, setting_value FROM app_settings
       WHERE setting_key IN ('checkInStart', 'checkInEnd', 'earliestCheckout')`,
    );
    const settings = new Map(values.map(({ setting_key, setting_value }) => [setting_key, setting_value]));
    return {
      checkInStart: settings.get('checkInStart') ?? defaultSchedule.checkInStart,
      checkInEnd: settings.get('checkInEnd') ?? defaultSchedule.checkInEnd,
      earliestCheckout: settings.get('earliestCheckout') ?? defaultSchedule.earliestCheckout,
    };
  }

  saveSchedule(schedule: Schedule): void {
    if (
      !validTime(schedule.checkInStart) ||
      !validTime(schedule.checkInEnd) ||
      !validTime(schedule.earliestCheckout) ||
      schedule.checkInStart > schedule.checkInEnd
    ) {
      throw new Error('Enter valid 24-hour times and make sure check-in starts before it ends.');
    }
    database.withTransactionSync(() => {
      for (const [key, value] of Object.entries(schedule)) {
        database.runSync(
          `INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?)
           ON CONFLICT(setting_key) DO UPDATE SET setting_value = excluded.setting_value`,
          key,
          value,
        );
      }
    });
  }

  getAttendance(date: string): Attendance[] {
    return database.getAllSync<{
      id: number;
      staff_id: number;
      staff_number: string;
      full_name: string;
      photo_uri: string | null;
      attendance_date: string;
      check_in_at: string | null;
      check_out_at: string | null;
      status: string;
    }>(
      `SELECT a.id, a.staff_id, s.staff_number, s.full_name, s.photo_uri,
              a.attendance_date, a.check_in_at, a.check_out_at, a.status
       FROM attendance a JOIN staff s ON s.id = a.staff_id
       WHERE a.attendance_date = ? AND s.active = 1
       ORDER BY s.full_name COLLATE NOCASE`,
      date,
    ).map((row) => ({
      id: row.id,
      staffId: row.staff_id,
      staffNumber: row.staff_number,
      fullName: row.full_name,
      photoUri: row.photo_uri,
      date: row.attendance_date,
      checkInAt: row.check_in_at,
      checkOutAt: row.check_out_at,
      status: parseStatus(row.status),
    }));
  }

  checkIn(staffId: number, now = new Date()): void {
    const date = localDate(now);
    const time = localTime(now);
    const schedule = this.getSchedule();
    if (time < schedule.checkInStart) {
      throw new Error(`Check-in opens at ${schedule.checkInStart}.`);
    }
    const late = time > schedule.checkInEnd;
    if (late && !this.hasException(staffId, date, 'late_checkin')) {
      database.runSync(
        `INSERT OR IGNORE INTO attendance
         (staff_id, attendance_date, check_in_at, status) VALUES (?, ?, NULL, 'absent')`,
        staffId,
        date,
      );
      throw new Error(`The check-in window ended at ${schedule.checkInEnd}. This staff member is marked absent; an admin must record a late check-in exception to continue.`);
    }

    const existing = database.getFirstSync<{ check_in_at: string | null }>(
      'SELECT check_in_at FROM attendance WHERE staff_id = ? AND attendance_date = ?',
      staffId,
      date,
    );
    if (existing?.check_in_at) throw new Error('This staff member has already checked in today.');
    if (existing) {
      database.runSync(
        `UPDATE attendance SET check_in_at = ?, status = ?
         WHERE staff_id = ? AND attendance_date = ?`,
        isoTimestamp(now),
        late ? 'late' : 'present',
        staffId,
        date,
      );
    } else {
      database.runSync(
        `INSERT INTO attendance (staff_id, attendance_date, check_in_at, status)
         VALUES (?, ?, ?, ?)`,
        staffId,
        date,
        isoTimestamp(now),
        late ? 'late' : 'present',
      );
    }
  }

  checkOut(staffId: number, now = new Date()): void {
    const date = localDate(now);
    const attendance = database.getFirstSync<{
      check_in_at: string | null;
      check_out_at: string | null;
    }>(
      'SELECT check_in_at, check_out_at FROM attendance WHERE staff_id = ? AND attendance_date = ?',
      staffId,
      date,
    );
    if (!attendance?.check_in_at) throw new Error('A check-in is required before check-out.');
    if (attendance.check_out_at) throw new Error('This staff member has already checked out today.');
    if (
      localTime(now) < this.getSchedule().earliestCheckout &&
      !this.hasException(staffId, date, 'early_checkout')
    ) {
      throw new Error(`Official check-out opens at ${this.getSchedule().earliestCheckout}. An admin exception is required for an earlier check-out.`);
    }
    database.runSync(
      'UPDATE attendance SET check_out_at = ? WHERE staff_id = ? AND attendance_date = ?',
      isoTimestamp(now),
      staffId,
      date,
    );
  }

  getExceptions(date?: string): AttendanceException[] {
    const rows = date
      ? database.getAllSync<ExceptionRow>(`${exceptionQuery} WHERE e.attendance_date = ? ORDER BY e.created_at DESC`, date)
      : database.getAllSync<ExceptionRow>(`${exceptionQuery} ORDER BY e.created_at DESC LIMIT 200`);
    return rows.map((row) => ({
      id: row.id,
      staffId: row.staff_id,
      staffName: row.full_name,
      date: row.attendance_date,
      type: parseExceptionType(row.type),
      reason: row.reason,
      createdAt: row.created_at,
    }));
  }

  addException(input: NewException): void {
    if (!input.reason.trim()) throw new Error('An admin reason is required for every exception.');
    if (!validDate(input.date)) throw new Error('Choose a valid attendance date.');
    database.withTransactionSync(() => {
      database.runSync(
        `INSERT INTO attendance_exceptions
         (staff_id, attendance_date, type, reason, created_at) VALUES (?, ?, ?, ?, ?)`,
        input.staffId,
        input.date,
        input.type,
        input.reason.trim(),
        isoTimestamp(new Date()),
      );

      if (input.type === 'approved_absence') {
        database.runSync(
          `INSERT INTO attendance (staff_id, attendance_date, status)
           VALUES (?, ?, 'excused')
           ON CONFLICT(staff_id, attendance_date) DO UPDATE SET status = 'excused'
           WHERE attendance.check_in_at IS NULL`,
          input.staffId,
          input.date,
        );
      } else if (input.type === 'missed_checkout') {
        database.runSync(
          `UPDATE attendance SET status = 'excused'
           WHERE staff_id = ? AND attendance_date = ? AND check_in_at IS NOT NULL AND check_out_at IS NULL`,
          input.staffId,
          input.date,
        );
      }
    });
  }

  private hasException(staffId: number, date: string, type: ExceptionType): boolean {
    return Boolean(
      database.getFirstSync<{ id: number }>(
        `SELECT id FROM attendance_exceptions
         WHERE staff_id = ? AND attendance_date = ? AND type = ? LIMIT 1`,
        staffId,
        date,
        type,
      ),
    );
  }

  private reconcileClosedDays(now: Date): void {
    const today = localDate(now);
    const row = database.getFirstSync<{ setting_value: string }>(
      "SELECT setting_value FROM app_settings WHERE setting_key = 'last_closed_date'",
    );
    let cursor = row?.setting_value ?? today;
    while (cursor < today) {
      this.closeDay(cursor, now);
      cursor = shiftDate(cursor, 1);
    }
    if (localTime(now) === '23:59') this.closeDay(today, now);
  }

  private reconcileMissedCheckIn(now: Date): void {
    const date = localDate(now);
    if (localTime(now) <= this.getSchedule().checkInEnd) return;
    database.withTransactionSync(() => {
      database.runSync(
        `INSERT OR IGNORE INTO attendance (staff_id, attendance_date, status)
         SELECT s.id, ?, 'absent' FROM staff s WHERE s.active = 1`,
        date,
      );
      database.runSync(
        `UPDATE attendance SET status = CASE
           WHEN EXISTS (
             SELECT 1 FROM attendance_exceptions e
             WHERE e.staff_id = attendance.staff_id AND e.attendance_date = ?
               AND e.type = 'approved_absence'
           ) THEN 'excused'
           ELSE 'absent'
         END
         WHERE attendance_date = ? AND check_in_at IS NULL`,
        date,
        date,
      );
    });
  }

  private closeDay(date: string, now: Date): void {
    const alreadyClosed = Boolean(
      database.getFirstSync<{ attendance_date: string }>(
        'SELECT attendance_date FROM closed_attendance_days WHERE attendance_date = ?',
        date,
      ),
    );
    if (alreadyClosed) {
      database.runSync(
        `INSERT INTO app_settings (setting_key, setting_value) VALUES ('last_closed_date', ?)
         ON CONFLICT(setting_key) DO UPDATE SET setting_value = excluded.setting_value`,
        date,
      );
      return;
    }

    database.withTransactionSync(() => {
      database.runSync(
        `INSERT OR IGNORE INTO attendance (staff_id, attendance_date, status)
         SELECT s.id, ?, 'absent' FROM staff s WHERE s.active = 1`,
        date,
      );
      database.runSync(
        `UPDATE attendance SET status = 'excused'
         WHERE attendance_date = ? AND check_in_at IS NULL
           AND EXISTS (
             SELECT 1 FROM attendance_exceptions e
             WHERE e.staff_id = attendance.staff_id AND e.attendance_date = ?
               AND e.type = 'approved_absence'
           )`,
        date,
        date,
      );
      database.runSync(
        `UPDATE attendance SET status = CASE
           WHEN EXISTS (
             SELECT 1 FROM attendance_exceptions e
             WHERE e.staff_id = attendance.staff_id AND e.attendance_date = ?
               AND e.type = 'missed_checkout'
           ) THEN 'excused'
           ELSE 'absent_no_checkout'
         END
         WHERE attendance_date = ? AND check_in_at IS NOT NULL AND check_out_at IS NULL`,
        date,
        date,
      );
      database.runSync(
        'INSERT OR IGNORE INTO closed_attendance_days (attendance_date, closed_at) VALUES (?, ?)',
        date,
        isoTimestamp(now),
      );
      database.runSync(
        `INSERT INTO app_settings (setting_key, setting_value) VALUES ('last_closed_date', ?)
         ON CONFLICT(setting_key) DO UPDATE SET setting_value = excluded.setting_value`,
        date,
      );
    });
  }
}

type ExceptionRow = {
  id: number;
  staff_id: number;
  full_name: string;
  attendance_date: string;
  type: string;
  reason: string;
  created_at: string;
};

const exceptionQuery = `SELECT e.id, e.staff_id, s.full_name, e.attendance_date, e.type, e.reason, e.created_at
  FROM attendance_exceptions e JOIN staff s ON s.id = e.staff_id`;

const parseExceptionType = (value: string): ExceptionType => {
  switch (value) {
    case 'approved_absence':
    case 'late_checkin':
    case 'early_checkout':
    case 'missed_checkout':
      return value;
    default:
      throw new Error(`Unknown attendance exception: ${value}`);
  }
};

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

const localStore = new LocalAttendanceStore();

export const attendanceRepository: AttendanceRepository = {
  initialize: async () => localStore.initialize(),
  getStaff: async () => localStore.getStaff(),
  addStaff: async (input) => localStore.addStaff(input),
  getSchedule: async () => localStore.getSchedule(),
  saveSchedule: async (schedule) => localStore.saveSchedule(schedule),
  getAttendance: async (date) => localStore.getAttendance(date),
  checkIn: async (staffId, now) => localStore.checkIn(staffId, now),
  checkOut: async (staffId, now) => localStore.checkOut(staffId, now),
  getExceptions: async (date) => localStore.getExceptions(date),
  addException: async (input) => localStore.addException(input),
};

export async function saveCapturedPhoto(sourceUri: string): Promise<string> {
  if (!FileSystem.documentDirectory) throw new Error('Private app storage is unavailable.');
  const directory = `${FileSystem.documentDirectory}staff-photos/`;
  await FileSystem.makeDirectoryAsync(directory, { intermediates: true });
  const destination = `${directory}${Date.now()}.jpg`;
  await FileSystem.copyAsync({ from: sourceUri, to: destination });
  return destination;
}
