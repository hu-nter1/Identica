export type Screen = 'today' | 'staff' | 'exceptions' | 'history' | 'settings';

export type StaffDraft = {
  staffNumber: string;
  fullName: string;
  department: string;
  role: string;
  phone: string;
};
