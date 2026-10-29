export type AttendanceStatus =
  | 'present'
  | 'late'
  | 'absent'
  | 'absent_no_checkout'
  | 'excused';

export type ExceptionType =
  | 'approved_absence'
  | 'late_checkin'
  | 'early_checkout'
  | 'missed_checkout';

export type Staff = {
  id: number;
  staffNumber: string;
  fullName: string;
  department: string;
  role: string;
  phone: string;
  photoUri: string | null;
  fingerprintCredentialId: string | null;
  active: boolean;
};

export type Attendance = {
  id: number;
  staffId: number;
  staffNumber: string;
  fullName: string;
  photoUri: string | null;
  date: string;
  checkInAt: string | null;
  checkOutAt: string | null;
  status: AttendanceStatus;
};

export type AttendanceException = {
  id: number;
  staffId: number;
  staffName: string;
  date: string;
  type: ExceptionType;
  reason: string;
  createdAt: string;
};

export type Schedule = {
  checkInStart: string;
  checkInEnd: string;
  earliestCheckout: string;
};

export type NewStaff = Omit<Staff, 'id' | 'active' | 'fingerprintCredentialId'>;
export type NewException = Omit<AttendanceException, 'id' | 'staffName' | 'createdAt'>;
