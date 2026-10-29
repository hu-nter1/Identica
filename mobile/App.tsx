import { CameraView, useCameraPermissions } from 'expo-camera';
import { StatusBar } from 'expo-status-bar';
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  AppState,
  Image,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  SafeAreaView,
  Share,
  ScrollView,
  StatusBar as NativeStatusBar,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';

import { attendanceRepository, saveCapturedPhoto } from './src/repository';
import type {
  Attendance,
  AttendanceException,
  ExceptionType,
  NewStaff,
  Schedule,
  Staff,
} from './src/types';

type Screen = 'today' | 'staff' | 'exceptions' | 'history' | 'settings';
type StaffDraft = {
  staffNumber: string;
  fullName: string;
  department: string;
  role: string;
  phone: string;
};

const today = (): string => {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
};

const prettyDate = (date: string): string => {
  const parsed = new Date(`${date}T12:00:00`);
  return parsed.toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' });
};

const displayTime = (value: string | null): string => {
  if (!value) return '—';
  return new Date(value).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
};

const exceptionLabels: Record<ExceptionType, string> = {
  approved_absence: 'Approved absence',
  late_checkin: 'Late check-in',
  early_checkout: 'Early check-out',
  missed_checkout: 'Missed check-out',
};

const exceptionHelp: Record<ExceptionType, string> = {
  approved_absence: 'Excuses an absence for this date.',
  late_checkin: 'Allows check-in after the normal check-in window.',
  early_checkout: 'Allows official check-out before the configured time.',
  missed_checkout: 'Excuses a missing check-out at the end of the attendance day.',
};

const initialDraft: StaffDraft = {
  staffNumber: '',
  fullName: '',
  department: '',
  role: '',
  phone: '',
};

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : 'An unexpected error occurred.';
}

export default function App() {
  const [screen, setScreen] = useState<Screen>('today');
  const [staff, setStaff] = useState<Staff[]>([]);
  const [attendance, setAttendance] = useState<Attendance[]>([]);
  const [historyDate, setHistoryDate] = useState(today());
  const [historyAttendance, setHistoryAttendance] = useState<Attendance[]>([]);
  const [exceptions, setExceptions] = useState<AttendanceException[]>([]);
  const [schedule, setSchedule] = useState<Schedule>({
    checkInStart: '06:00',
    checkInEnd: '08:30',
    earliestCheckout: '15:00',
  });
  const [ready, setReady] = useState(false);
  const [staffModal, setStaffModal] = useState(false);
  const [exceptionModal, setExceptionModal] = useState(false);
  const [cameraOpen, setCameraOpen] = useState(false);
  const [draft, setDraft] = useState<StaffDraft>(initialDraft);
  const [photoUri, setPhotoUri] = useState<string | null>(null);
  const [fingerprintEnrolled, setFingerprintEnrolled] = useState(false);
  const [exceptionStaffId, setExceptionStaffId] = useState<number | null>(null);
  const [exceptionType, setExceptionType] = useState<ExceptionType>('approved_absence');
  const [exceptionDate, setExceptionDate] = useState(today());
  const [exceptionReason, setExceptionReason] = useState('');
  const [scheduleDraft, setScheduleDraft] = useState(schedule);

  const refresh = useCallback(async () => {
    try {
      await attendanceRepository.initialize();
      const [registeredStaff, todayAttendance, registeredExceptions, currentSchedule] = await Promise.all([
        attendanceRepository.getStaff(),
        attendanceRepository.getAttendance(today()),
        attendanceRepository.getExceptions(),
        attendanceRepository.getSchedule(),
      ]);
      setStaff(registeredStaff);
      setAttendance(todayAttendance);
      setExceptions(registeredExceptions);
      setSchedule(currentSchedule);
      setScheduleDraft(currentSchedule);
      setReady(true);
    } catch (error) {
      Alert.alert('Could not load attendance data', messageOf(error));
    }
  }, []);

  useEffect(() => {
    void refresh();
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') void refresh();
    });
    return () => subscription.remove();
  }, [refresh]);

  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>;
    const scheduleReconciliation = () => {
      const now = new Date();
      const nextRun = new Date(now);
      nextRun.setHours(24, 0, 1, 0);

      const [endHour, endMinute] = schedule.checkInEnd.split(':').map(Number);
      const checkInCutoff = new Date(now);
      checkInCutoff.setHours(endHour, endMinute + 1, 0, 0);
      if (checkInCutoff > now && checkInCutoff < nextRun) {
        nextRun.setTime(checkInCutoff.getTime());
      }

      timer = setTimeout(() => {
        if (AppState.currentState === 'active') void refresh();
        scheduleReconciliation();
      }, Math.max(nextRun.getTime() - now.getTime(), 1_000));
    };

    scheduleReconciliation();
    return () => clearTimeout(timer);
  }, [refresh, schedule.checkInEnd]);

  const attendanceByStaff = useMemo(
    () => new Map(attendance.map((record) => [record.staffId, record])),
    [attendance],
  );
  const presentCount = attendance.filter((record) => record.checkInAt).length;
  const absentCount = attendance.filter(
    (record) => record.status === 'absent' || record.status === 'absent_no_checkout',
  ).length;
  const excusedCount = attendance.filter((record) => record.status === 'excused').length;

  const confirmScan = (person: Staff) => {
    const record = attendanceByStaff.get(person.id);
    const checkingOut = Boolean(record?.checkInAt && !record.checkOutAt);
    const action = checkingOut ? 'check out' : 'check in';
    Alert.alert(
      'Prototype fingerprint check',
      `This demo cannot identify a fingerprint. Confirm the selected person (${person.fullName}) with the premise's approved fingerprint reader before recording ${action}.`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Simulate fingerprint match',
          onPress: () => void (async () => {
            try {
              if (checkingOut) await attendanceRepository.checkOut(person.id);
              else await attendanceRepository.checkIn(person.id);
              await refresh();
              Alert.alert('Attendance saved', `${person.fullName} ${checkingOut ? 'checked out' : 'checked in'} successfully.`);
            } catch (error) {
              Alert.alert('Attendance not recorded', messageOf(error));
              await refresh();
            }
          })(),
        },
      ],
    );
  };

  const openNewStaff = () => {
    setDraft(initialDraft);
    setPhotoUri(null);
    setFingerprintEnrolled(false);
    setStaffModal(true);
  };

  const createStaff = async (addAnother = false) => {
    if (!photoUri) {
      Alert.alert('Staff photo required', 'Take a photo directly with the camera before saving this staff profile.');
      return;
    }
    try {
      const newStaff: NewStaff = {
        ...draft,
        photoUri,
        fingerprintEnrolled,
      };
      await attendanceRepository.addStaff(newStaff);
      await refresh();
      if (addAnother) {
        setDraft(initialDraft);
        setPhotoUri(null);
        setFingerprintEnrolled(false);
        Alert.alert('Staff registered', 'Ready to capture the next staff member.');
      } else {
        setStaffModal(false);
        Alert.alert('Staff registered', 'The profile and camera photo are saved on this device only.');
      }
    } catch (error) {
      Alert.alert('Could not register staff', messageOf(error));
    }
  };

  const addException = async () => {
    if (!exceptionStaffId) {
      Alert.alert('Select a staff member', 'Choose who this admin exception applies to.');
      return;
    }
    try {
      await attendanceRepository.addException({
        staffId: exceptionStaffId,
        date: exceptionDate.trim(),
        type: exceptionType,
        reason: exceptionReason,
      });
      setExceptionModal(false);
      setExceptionReason('');
      setExceptionStaffId(null);
      await refresh();
    } catch (error) {
      Alert.alert('Could not save admin exception', messageOf(error));
    }
  };

  const saveSchedule = async () => {
    try {
      await attendanceRepository.saveSchedule(scheduleDraft);
      await refresh();
      Alert.alert('Schedule saved', 'The attendance window has been updated on this device.');
    } catch (error) {
      Alert.alert('Invalid schedule', messageOf(error));
    }
  };

  const loadHistory = async () => {
    try {
      setHistoryAttendance(await attendanceRepository.getAttendance(historyDate.trim()));
    } catch (error) {
      Alert.alert('Could not load attendance history', messageOf(error));
    }
  };

  const exportHistory = async () => {
    if (historyAttendance.length === 0) {
      Alert.alert('Nothing to export', 'There are no attendance records for this date.');
      return;
    }
    const csvRows = [
      ['Date', 'Staff number', 'Full name', 'Status', 'Check-in', 'Check-out'],
      ...historyAttendance.map((record) => [
        record.date,
        record.staffNumber,
        record.fullName,
        record.status,
        displayTime(record.checkInAt),
        displayTime(record.checkOutAt),
      ]),
    ];
    const csv = csvRows.map((row) =>
      row.map((cell) => `"${cell.replaceAll('"', '""')}"`).join(','),
    ).join('\n');
    try {
      await Share.share({ title: `Attendance ${historyDate}`, message: csv });
    } catch (error) {
      Alert.alert('Could not share attendance report', messageOf(error));
    }
  };

  if (!ready) {
    return (
      <SafeAreaView style={styles.safeArea}>
        <StatusBar style="light" />
        <View style={styles.loading}>
          <Text style={styles.brand}>Identica</Text>
          <Text style={styles.muted}>Opening local attendance register…</Text>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar style="light" />
      <View style={styles.app}>
        <View style={styles.header}>
          <View>
            <Text style={styles.brand}>Identica</Text>
            <Text style={styles.headerSub}>PREMISE ATTENDANCE</Text>
          </View>
          <View style={styles.prototypePill}>
            <View style={styles.prototypeDot} />
            <Text style={styles.prototypeText}>LOCAL DEMO</Text>
          </View>
        </View>

        <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
          {screen === 'today' && (
            <TodayScreen
              attendance={attendance}
              attendanceByStaff={attendanceByStaff}
              absentCount={absentCount}
              excusedCount={excusedCount}
              onScan={confirmScan}
              presentCount={presentCount}
              schedule={schedule}
              staff={staff}
              onAddStaff={openNewStaff}
            />
          )}
          {screen === 'staff' && (
            <StaffScreen staff={staff} onAddStaff={openNewStaff} onScan={confirmScan} />
          )}
          {screen === 'exceptions' && (
            <ExceptionsScreen exceptions={exceptions} onAdd={() => setExceptionModal(true)} />
          )}
          {screen === 'history' && (
            <HistoryScreen
              attendance={historyAttendance}
              date={historyDate}
              onChangeDate={setHistoryDate}
              onExport={() => void exportHistory()}
              onSearch={() => void loadHistory()}
            />
          )}
          {screen === 'settings' && (
            <SettingsScreen schedule={scheduleDraft} onChange={setScheduleDraft} onSave={saveSchedule} />
          )}
        </ScrollView>

        <View style={styles.navigation}>
          <NavButton active={screen === 'today'} label="Today" icon="◷" onPress={() => setScreen('today')} />
          <NavButton active={screen === 'staff'} label="Staff" icon="♙" onPress={() => setScreen('staff')} />
          <NavButton active={screen === 'exceptions'} label="Issues" icon="▤" onPress={() => setScreen('exceptions')} />
          <NavButton active={screen === 'history'} label="History" icon="≡" onPress={() => setScreen('history')} />
          <NavButton active={screen === 'settings'} label="Admin" icon="⚙" onPress={() => setScreen('settings')} />
        </View>
      </View>

      <StaffModal
        draft={draft}
        fingerprintEnrolled={fingerprintEnrolled}
        onChange={setDraft}
        onClose={() => setStaffModal(false)}
        onEnrollDemo={() => setFingerprintEnrolled((value) => !value)}
        onOpenCamera={() => setCameraOpen(true)}
        onSave={createStaff}
        photoUri={photoUri}
        visible={staffModal}
      />
      <CameraModal
        onClose={() => setCameraOpen(false)}
        onPhoto={setPhotoUri}
        visible={cameraOpen}
      />
      <ExceptionModal
        date={exceptionDate}
        onChangeDate={setExceptionDate}
        onChangeReason={setExceptionReason}
        onChangeStaff={setExceptionStaffId}
        onChangeType={setExceptionType}
        onClose={() => setExceptionModal(false)}
        onSave={addException}
        reason={exceptionReason}
        selectedStaffId={exceptionStaffId}
        staff={staff}
        type={exceptionType}
        visible={exceptionModal}
      />
    </SafeAreaView>
  );
}

function TodayScreen({
  attendance,
  attendanceByStaff,
  absentCount,
  excusedCount,
  onScan,
  presentCount,
  schedule,
  staff,
  onAddStaff,
}: {
  attendance: Attendance[];
  attendanceByStaff: Map<number, Attendance>;
  absentCount: number;
  excusedCount: number;
  onScan: (staff: Staff) => void;
  presentCount: number;
  schedule: Schedule;
  staff: Staff[];
  onAddStaff: () => void;
}) {
  return (
    <>
      <Text style={styles.eyebrow}>ATTENDANCE OVERVIEW</Text>
      <Text style={styles.title}>Good day, Admin</Text>
      <Text style={styles.muted}>{prettyDate(today())}</Text>

      <View style={styles.stats}>
        <StatCard label="ON SITE" value={presentCount} color={colors.green} />
        <StatCard label="ABSENT / INCOMPLETE" value={absentCount} color={colors.orange} />
        <StatCard label="EXCUSED" value={excusedCount} color={colors.cyan} />
      </View>

      <View style={styles.scheduleBanner}>
        <Text style={styles.scheduleLabel}>TODAY&apos;S SCHEDULE</Text>
        <Text style={styles.scheduleValue}>{schedule.checkInStart} – {schedule.checkInEnd} check-in</Text>
        <Text style={styles.scheduleFoot}>Check-out from {schedule.earliestCheckout} · no early cutoff</Text>
      </View>

      <SectionHeading title="Staff attendance" detail={`${staff.length} registered`} />
      {staff.length === 0 ? (
        <EmptyState
          title="No staff registered yet"
          detail="Add staff profiles before recording attendance."
          action="Register staff"
          onPress={onAddStaff}
        />
      ) : (
        staff.map((person) => {
          const record = attendanceByStaff.get(person.id);
          const actionLabel = record?.checkInAt && !record.checkOutAt ? 'CHECK OUT' : 'CHECK IN';
          return (
            <StaffAttendanceCard
              key={person.id}
              person={person}
              record={record}
              actionLabel={actionLabel}
              onPress={() => onScan(person)}
            />
          );
        })
      )}
      <Notice>
        Fingerprint matching is simulated in this build. The device&apos;s built-in biometric prompt
        cannot tell which staff member used it. Do not use the demo confirmation for real attendance.
      </Notice>
      <Text style={styles.hiddenCount}>{attendance.length} attendance records for today</Text>
    </>
  );
}

function StaffScreen({
  staff,
  onAddStaff,
  onScan,
}: {
  staff: Staff[];
  onAddStaff: () => void;
  onScan: (staff: Staff) => void;
}) {
  return (
    <>
      <Text style={styles.eyebrow}>PEOPLE DIRECTORY</Text>
      <Text style={styles.title}>Registered staff</Text>
      <Text style={styles.muted}>Profiles and on-device photo records</Text>
      <PrimaryButton label="+ Register staff" onPress={onAddStaff} />
      {staff.length === 0 ? (
        <EmptyState title="Your directory is empty" detail="Register a staff profile to get started." />
      ) : (
        staff.map((person) => (
          <View key={person.id} style={styles.staffCard}>
            <PersonAvatar name={person.fullName} photoUri={person.photoUri} />
            <View style={styles.personInfo}>
              <Text style={styles.personName}>{person.fullName}</Text>
              <Text style={styles.personMeta}>{person.staffNumber} · {person.department}</Text>
              <Text style={styles.personMeta}>{person.role}{person.phone ? ` · ${person.phone}` : ''}</Text>
              <Text style={styles.enrollmentLabel}>
                {person.fingerprintEnrolled ? 'DEMO ENROLLMENT · NOT A FINGERPRINT TEMPLATE' : 'FINGERPRINT READER NOT CONNECTED'}
              </Text>
            </View>
            <Pressable accessibilityRole="button" onPress={() => onScan(person)} style={styles.smallButton}>
              <Text style={styles.smallButtonText}>RECORD</Text>
            </Pressable>
          </View>
        ))
      )}
    </>
  );
}

function ExceptionsScreen({
  exceptions,
  onAdd,
}: {
  exceptions: AttendanceException[];
  onAdd: () => void;
}) {
  return (
    <>
      <Text style={styles.eyebrow}>ADMIN REGISTER</Text>
      <Text style={styles.title}>Special issues</Text>
      <Text style={styles.muted}>Each exception requires a reason and is kept in the local register.</Text>
      <PrimaryButton label="+ Record an exception" onPress={onAdd} />
      {exceptions.length === 0 ? (
        <EmptyState title="No exceptions recorded" detail="Approved absences and attendance issues will appear here." />
      ) : (
        exceptions.map((item) => (
          <View key={item.id} style={styles.exceptionCard}>
            <View style={styles.rowBetween}>
              <Text style={styles.personName}>{item.staffName}</Text>
              <Text style={styles.dateTag}>{item.date}</Text>
            </View>
            <Text style={styles.exceptionType}>{exceptionLabels[item.type]}</Text>
            <Text style={styles.exceptionReason}>{item.reason}</Text>
            <Text style={styles.exceptionStamp}>RECORDED {displayTime(item.createdAt)}</Text>
          </View>
        ))
      )}
    </>
  );
}

function HistoryScreen({
  attendance,
  date,
  onChangeDate,
  onExport,
  onSearch,
}: {
  attendance: Attendance[];
  date: string;
  onChangeDate: (value: string) => void;
  onExport: () => void;
  onSearch: () => void;
}) {
  return (
    <>
      <Text style={styles.eyebrow}>DAILY REGISTER</Text>
      <Text style={styles.title}>Attendance history</Text>
      <Text style={styles.muted}>Review a past date or share its attendance report.</Text>
      <View style={styles.historySearch}>
        <View style={styles.personInfo}>
          <FormField label="Date (YYYY-MM-DD)" value={date} onChangeText={onChangeDate} />
        </View>
        <Pressable accessibilityRole="button" onPress={onSearch} style={styles.smallButton}>
          <Text style={styles.smallButtonText}>VIEW</Text>
        </Pressable>
      </View>
      <Pressable accessibilityRole="button" onPress={onExport} style={styles.exportButton}>
        <Text style={styles.exportButtonText}>Share CSV report</Text>
      </Pressable>
      {attendance.length === 0 ? (
        <EmptyState title="No records for this date" detail="Choose a date with registered attendance." />
      ) : (
        attendance.map((record) => (
          <View key={record.id} style={styles.historyCard}>
            <View style={styles.rowBetween}>
              <View style={styles.personInfo}>
                <Text style={styles.personName}>{record.fullName}</Text>
                <Text style={styles.personMeta}>{record.staffNumber}</Text>
              </View>
              <Text
                style={[
                  styles.attendanceStatus,
                  {
                    color:
                      record.status === 'absent' || record.status === 'absent_no_checkout'
                        ? colors.orange
                        : record.status === 'excused'
                          ? colors.cyan
                          : colors.green,
                  },
                ]}
              >
                {record.status.replaceAll('_', ' ').toUpperCase()}
              </Text>
            </View>
            <Text style={styles.personMeta}>
              Check-in {displayTime(record.checkInAt)} · Check-out {displayTime(record.checkOutAt)}
            </Text>
          </View>
        ))
      )}
    </>
  );
}

function SettingsScreen({
  schedule,
  onChange,
  onSave,
}: {
  schedule: Schedule;
  onChange: (schedule: Schedule) => void;
  onSave: () => void;
}) {
  return (
    <>
      <Text style={styles.eyebrow}>ADMIN CONTROLS</Text>
      <Text style={styles.title}>Attendance rules</Text>
      <Text style={styles.muted}>Times use the device&apos;s local 24-hour clock.</Text>
      <View style={styles.formCard}>
        <Text style={styles.formLabel}>Check-in window</Text>
        <Text style={styles.helper}>Staff outside this window are absent unless an admin records a late check-in exception.</Text>
        <View style={styles.timeRow}>
          <TimeField label="OPENS" value={schedule.checkInStart} onChangeText={(value) => onChange({ ...schedule, checkInStart: value })} />
          <TimeField label="CLOSES" value={schedule.checkInEnd} onChangeText={(value) => onChange({ ...schedule, checkInEnd: value })} />
        </View>
        <View style={styles.divider} />
        <Text style={styles.formLabel}>Earliest official check-out</Text>
        <Text style={styles.helper}>Check-out is accepted from this time onward. There is no set evening cutoff; open check-outs are reconciled at the next day.</Text>
        <TimeField label="CHECK-OUT OPENS" value={schedule.earliestCheckout} onChangeText={(value) => onChange({ ...schedule, earliestCheckout: value })} />
        <PrimaryButton label="Save attendance rules" onPress={onSave} />
      </View>
      <Notice>
        This demo has no admin login or cloud sync yet. Anyone with access to this device can edit
        the register. Use test data only until authentication, backups, audit controls, and a
        supported fingerprint-reader SDK are added.
      </Notice>
    </>
  );
}

function StaffAttendanceCard({
  person,
  record,
  actionLabel,
  onPress,
}: {
  person: Staff;
  record?: Attendance;
  actionLabel: string;
  onPress: () => void;
}) {
  const statusText = record?.status === 'absent'
    ? 'ABSENT'
    : record?.status === 'absent_no_checkout'
      ? 'ABSENT · NO CHECK-OUT'
      : record?.status === 'excused'
        ? 'EXCUSED'
        : record?.checkOutAt
          ? 'CHECKED OUT'
          : record?.status === 'late'
            ? 'LATE · ON SITE'
            : record?.checkInAt
              ? 'ON SITE'
            : 'NOT RECORDED';
  const statusColor = record?.status === 'absent' || record?.status === 'absent_no_checkout'
    ? colors.orange
    : record?.status === 'excused'
      ? colors.cyan
      : record?.checkInAt
        ? colors.green
        : colors.muted;

  return (
    <View style={styles.attendanceCard}>
      <PersonAvatar name={person.fullName} photoUri={person.photoUri} />
      <View style={styles.personInfo}>
        <Text style={styles.personName}>{person.fullName}</Text>
        <Text style={styles.personMeta}>{person.staffNumber} · {person.department}</Text>
        <Text style={[styles.attendanceStatus, { color: statusColor }]}>{statusText}</Text>
        <Text style={styles.personMeta}>
          In {displayTime(record?.checkInAt ?? null)} · Out {displayTime(record?.checkOutAt ?? null)}
        </Text>
      </View>
      <Pressable accessibilityRole="button" onPress={onPress} style={styles.actionButton}>
        <Text style={styles.actionButtonText}>{actionLabel}</Text>
      </Pressable>
    </View>
  );
}

function StaffModal({
  draft,
  fingerprintEnrolled,
  onChange,
  onClose,
  onEnrollDemo,
  onOpenCamera,
  onSave,
  photoUri,
  visible,
}: {
  draft: StaffDraft;
  fingerprintEnrolled: boolean;
  onChange: (draft: StaffDraft) => void;
  onClose: () => void;
  onEnrollDemo: () => void;
  onOpenCamera: () => void;
  onSave: (addAnother: boolean) => void;
  photoUri: string | null;
  visible: boolean;
}) {
  const field = (key: keyof StaffDraft, label: string, required = false, keyboardType: 'default' | 'phone-pad' = 'default') => (
    <FormField
      key={key}
      label={`${label}${required ? ' *' : ''}`}
      value={draft[key]}
      onChangeText={(value) => onChange({ ...draft, [key]: value })}
      keyboardType={keyboardType}
    />
  );

  return (
    <Modal animationType="slide" onRequestClose={onClose} visible={visible}>
      <SafeAreaView style={styles.modalSafe}>
        <KeyboardAvoidingView style={styles.modalContainer} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
          <ModalHeader title="Register staff" onClose={onClose} />
          <ScrollView contentContainerStyle={styles.modalContent} keyboardShouldPersistTaps="handled">
            <Pressable style={styles.photoCapture} onPress={onOpenCamera}>
              {photoUri ? (
                <View style={styles.photoPreview}>
                  <Image source={{ uri: photoUri }} style={styles.capturedPhoto} />
                  <Text style={styles.photoRetake}>Tap to retake photo</Text>
                </View>
              ) : (
                <>
                  <Text style={styles.cameraIcon}>◎</Text>
                  <Text style={styles.photoTitle}>Take a staff photo *</Text>
                  <Text style={styles.helper}>Camera only · no photo uploads or gallery import</Text>
                </>
              )}
            </Pressable>
            {field('staffNumber', 'Staff number', true)}
            {field('fullName', 'Full name', true)}
            {field('department', 'Department', true)}
            {field('role', 'Job title / role', true)}
            {field('phone', 'Phone (optional)', false, 'phone-pad')}

            <View style={styles.fingerprintBox}>
              <Text style={styles.formLabel}>Fingerprint registration</Text>
              <Text style={styles.helper}>
                This demo cannot enroll or match fingerprints. A compatible premise reader and vendor SDK are required.
              </Text>
              <Pressable onPress={onEnrollDemo} style={styles.demoToggle}>
                <View style={[styles.checkbox, fingerprintEnrolled && styles.checkboxChecked]}>
                  {fingerprintEnrolled ? <Text style={styles.checkmark}>✓</Text> : null}
                </View>
                <Text style={styles.demoToggleText}>Mark as demo-enrolled (no fingerprint data stored)</Text>
              </Pressable>
            </View>
            <PrimaryButton label="Save staff profile" onPress={() => onSave(false)} />
            <Pressable
              accessibilityRole="button"
              onPress={() => onSave(true)}
              style={styles.secondaryButton}
            >
              <Text style={styles.secondaryButtonText}>Save & capture next staff member</Text>
            </Pressable>
          </ScrollView>
        </KeyboardAvoidingView>
      </SafeAreaView>
    </Modal>
  );
}

function CameraModal({
  onClose,
  onPhoto,
  visible,
}: {
  onClose: () => void;
  onPhoto: (uri: string) => void;
  visible: boolean;
}) {
  const [permission, requestPermission] = useCameraPermissions();
  const camera = useRef<CameraView>(null);
  const [saving, setSaving] = useState(false);
  const [cameraReady, setCameraReady] = useState(false);
  const [cameraError, setCameraError] = useState<string | null>(null);

  useEffect(() => {
    if (visible) {
      setCameraReady(false);
      setCameraError(null);
    }
  }, [visible]);

  const requestCameraAccess = async () => {
    try {
      const result = await requestPermission();
      if (!result.granted) {
        setCameraError('Camera permission was not granted. Allow camera access in your browser or device settings, then try again.');
      }
    } catch (error) {
      setCameraError(messageOf(error));
    }
  };

  const takePhoto = async () => {
    if (!camera.current || saving) return;
    setSaving(true);
    try {
      const picture = await camera.current.takePictureAsync({ quality: 0.8 });
      if (!picture?.uri) throw new Error('The camera did not return a photo.');
      const savedUri = await saveCapturedPhoto(picture.uri);
      onPhoto(savedUri);
      onClose();
    } catch (error) {
      Alert.alert('Could not save photo', messageOf(error));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal animationType="slide" onRequestClose={onClose} visible={visible}>
      <SafeAreaView style={styles.cameraSafe}>
        <View style={styles.cameraHeader}>
          <Text style={styles.cameraHeaderText}>TAKE STAFF PHOTO</Text>
          <Pressable accessibilityRole="button" onPress={onClose}><Text style={styles.closeText}>CLOSE</Text></Pressable>
        </View>
        {!permission ? (
          <View style={styles.cameraFallback}><Text style={styles.muted}>Checking camera permission…</Text></View>
        ) : !permission.granted ? (
          <View style={styles.cameraFallback}>
            <Text style={styles.formLabel}>Camera access required</Text>
            <Text style={styles.helper}>Photos are captured directly in the app and stored on this device.</Text>
            {cameraError ? <Text style={styles.cameraError}>{cameraError}</Text> : null}
            <PrimaryButton label="Allow camera" onPress={() => void requestCameraAccess()} />
          </View>
        ) : (
          <View style={styles.cameraBody}>
            <CameraView
              facing="front"
              onCameraReady={() => setCameraReady(true)}
              onMountError={(event) => {
                setCameraError(event.message);
                setCameraReady(false);
              }}
              ref={camera}
              style={styles.cameraPreview}
            />
            <Text style={styles.cameraHint}>
              {cameraError ?? (cameraReady ? 'Position the person in frame, then take the photo.' : 'Starting camera…')}
            </Text>
            <Pressable
              accessibilityRole="button"
              disabled={saving || !cameraReady}
              onPress={() => void takePhoto()}
              style={[styles.captureButton, (saving || !cameraReady) && styles.disabledCaptureButton]}
            >
              <Text style={styles.captureButtonText}>
                {saving ? 'SAVING…' : cameraReady ? 'CAPTURE PHOTO' : 'CAMERA STARTING'}
              </Text>
            </Pressable>
          </View>
        )}
      </SafeAreaView>
    </Modal>
  );
}

function ExceptionModal({
  date,
  onChangeDate,
  onChangeReason,
  onChangeStaff,
  onChangeType,
  onClose,
  onSave,
  reason,
  selectedStaffId,
  staff,
  type,
  visible,
}: {
  date: string;
  onChangeDate: (value: string) => void;
  onChangeReason: (value: string) => void;
  onChangeStaff: (value: number | null) => void;
  onChangeType: (value: ExceptionType) => void;
  onClose: () => void;
  onSave: () => void;
  reason: string;
  selectedStaffId: number | null;
  staff: Staff[];
  type: ExceptionType;
  visible: boolean;
}) {
  return (
    <Modal animationType="slide" onRequestClose={onClose} visible={visible}>
      <SafeAreaView style={styles.modalSafe}>
        <View style={styles.modalContainer}>
          <ModalHeader title="Record special issue" onClose={onClose} />
          <ScrollView contentContainerStyle={styles.modalContent} keyboardShouldPersistTaps="handled">
            <Text style={styles.formLabel}>Staff member</Text>
            {staff.length === 0 ? <Text style={styles.helper}>Register staff before adding exceptions.</Text> : null}
            <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.chipScroller}>
              {staff.map((person) => (
                <ChoiceChip
                  key={person.id}
                  label={person.fullName}
                  selected={person.id === selectedStaffId}
                  onPress={() => onChangeStaff(person.id)}
                />
              ))}
            </ScrollView>

            <FormField label="Attendance date (YYYY-MM-DD)" value={date} onChangeText={onChangeDate} />
            <Text style={styles.formLabel}>Exception type</Text>
            {(['approved_absence', 'late_checkin', 'early_checkout', 'missed_checkout'] as const).map((value) => (
              <Pressable key={value} onPress={() => onChangeType(value)} style={styles.exceptionChoice}>
                <View style={[styles.radio, value === type && styles.radioSelected]} />
                <View style={styles.personInfo}>
                  <Text style={styles.choiceTitle}>{exceptionLabels[value]}</Text>
                  <Text style={styles.helper}>{exceptionHelp[value]}</Text>
                </View>
              </Pressable>
            ))}
            <FormField
              label="Admin reason (required)"
              multiline
              value={reason}
              onChangeText={onChangeReason}
              placeholder="Explain the special issue"
            />
            <PrimaryButton label="Save to special-issues register" onPress={onSave} />
          </ScrollView>
        </View>
      </SafeAreaView>
    </Modal>
  );
}

function TimeField({
  label,
  value,
  onChangeText,
}: {
  label: string;
  value: string;
  onChangeText: (value: string) => void;
}) {
  return (
    <View style={styles.timeField}>
      <Text style={styles.fieldLabel}>{label}</Text>
      <TextInput
        autoCapitalize="none"
        autoCorrect={false}
        keyboardType="numbers-and-punctuation"
        maxLength={5}
        onChangeText={onChangeText}
        placeholder="06:00"
        placeholderTextColor={colors.subtle}
        style={styles.timeInput}
        value={value}
      />
    </View>
  );
}

function FormField({
  label,
  value,
  onChangeText,
  placeholder,
  keyboardType = 'default',
  multiline = false,
}: {
  label: string;
  value: string;
  onChangeText: (value: string) => void;
  placeholder?: string;
  keyboardType?: 'default' | 'phone-pad';
  multiline?: boolean;
}) {
  return (
    <View style={styles.formField}>
      <Text style={styles.fieldLabel}>{label}</Text>
      <TextInput
        autoCapitalize="sentences"
        keyboardType={keyboardType}
        multiline={multiline}
        onChangeText={onChangeText}
        placeholder={placeholder}
        placeholderTextColor={colors.subtle}
        style={[styles.textInput, multiline && styles.multilineInput]}
        value={value}
      />
    </View>
  );
}

function StatCard({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <View style={styles.statCard}>
      <Text style={[styles.statValue, { color }]}>{value}</Text>
      <Text style={styles.statLabel}>{label}</Text>
    </View>
  );
}

function SectionHeading({ title, detail }: { title: string; detail: string }) {
  return (
    <View style={styles.sectionHeading}>
      <Text style={styles.sectionTitle}>{title}</Text>
      <Text style={styles.sectionDetail}>{detail}</Text>
    </View>
  );
}

function PersonAvatar({ name, photoUri }: { name: string; photoUri: string | null }) {
  if (photoUri) return <Image source={{ uri: photoUri }} style={styles.avatar} />;
  return (
    <View style={styles.avatarPlaceholder}>
      <Text style={styles.avatarInitial}>{name.trim().charAt(0).toUpperCase() || '?'}</Text>
    </View>
  );
}

function NavButton({
  active,
  label,
  icon,
  onPress,
}: {
  active: boolean;
  label: string;
  icon: string;
  onPress: () => void;
}) {
  return (
    <Pressable accessibilityRole="button" onPress={onPress} style={styles.navButton}>
      <Text style={[styles.navIcon, active && styles.navActive]}>{icon}</Text>
      <Text style={[styles.navLabel, active && styles.navActive]}>{label}</Text>
    </Pressable>
  );
}

function EmptyState({
  title,
  detail,
  action,
  onPress,
}: {
  title: string;
  detail: string;
  action?: string;
  onPress?: () => void;
}) {
  return (
    <View style={styles.emptyState}>
      <Text style={styles.emptyMark}>○</Text>
      <Text style={styles.formLabel}>{title}</Text>
      <Text style={styles.helper}>{detail}</Text>
      {action && onPress ? <PrimaryButton label={action} onPress={onPress} /> : null}
    </View>
  );
}

function Notice({ children }: { children: React.ReactNode }) {
  return (
    <View style={styles.notice}>
      <Text style={styles.noticeMark}>i</Text>
      <Text style={styles.noticeText}>{children}</Text>
    </View>
  );
}

function PrimaryButton({ label, onPress }: { label: string; onPress: () => void }) {
  return (
    <Pressable accessibilityRole="button" onPress={onPress} style={styles.primaryButton}>
      <Text style={styles.primaryButtonText}>{label}</Text>
    </Pressable>
  );
}

function ChoiceChip({ label, selected, onPress }: { label: string; selected: boolean; onPress: () => void }) {
  return (
    <Pressable accessibilityRole="button" onPress={onPress} style={[styles.chip, selected && styles.chipSelected]}>
      <Text style={[styles.chipText, selected && styles.chipTextSelected]}>{label}</Text>
    </Pressable>
  );
}

function ModalHeader({ title, onClose }: { title: string; onClose: () => void }) {
  return (
    <View style={styles.modalHeader}>
      <Text style={styles.modalTitle}>{title}</Text>
      <Pressable accessibilityRole="button" onPress={onClose}><Text style={styles.closeText}>CLOSE</Text></Pressable>
    </View>
  );
}

const colors = {
  background: '#07151A',
  surface: '#0E2027',
  surfaceRaised: '#142A32',
  border: '#23404A',
  green: '#52D3A0',
  cyan: '#6BD8E5',
  orange: '#FFB66D',
  text: '#F1F7F8',
  muted: '#99ADB3',
  subtle: '#60777F',
};

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: colors.background, paddingTop: Platform.OS === 'android' ? NativeStatusBar.currentHeight : 0 },
  app: { flex: 1, backgroundColor: colors.background },
  header: { paddingHorizontal: 22, paddingTop: 16, paddingBottom: 15, borderBottomWidth: 1, borderBottomColor: colors.border, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  brand: { color: colors.text, fontSize: 24, fontWeight: '800', letterSpacing: 0.4 },
  headerSub: { color: colors.cyan, fontSize: 9, fontWeight: '700', letterSpacing: 2, marginTop: 3 },
  prototypePill: { flexDirection: 'row', alignItems: 'center', gap: 7, borderColor: '#76512F', borderWidth: 1, borderRadius: 20, paddingHorizontal: 10, paddingVertical: 7, backgroundColor: '#241B13' },
  prototypeDot: { width: 7, height: 7, borderRadius: 4, backgroundColor: colors.orange },
  prototypeText: { color: colors.orange, fontSize: 9, fontWeight: '800', letterSpacing: 1 },
  content: { padding: 20, paddingBottom: 30 },
  eyebrow: { color: colors.cyan, fontSize: 10, fontWeight: '800', letterSpacing: 1.8, marginTop: 10 },
  title: { color: colors.text, fontSize: 27, fontWeight: '700', marginTop: 7 },
  muted: { color: colors.muted, fontSize: 13, marginTop: 5, lineHeight: 19 },
  stats: { flexDirection: 'row', gap: 8, marginTop: 22 },
  statCard: { flex: 1, minHeight: 88, backgroundColor: colors.surface, borderRadius: 15, borderWidth: 1, borderColor: colors.border, paddingVertical: 13, paddingHorizontal: 11 },
  statValue: { fontSize: 25, fontWeight: '800' },
  statLabel: { color: colors.muted, fontSize: 8, lineHeight: 12, marginTop: 6, letterSpacing: 0.6, fontWeight: '800' },
  scheduleBanner: { backgroundColor: '#102A31', borderWidth: 1, borderColor: '#27515A', borderRadius: 15, padding: 15, marginTop: 14 },
  scheduleLabel: { color: colors.cyan, fontSize: 9, fontWeight: '800', letterSpacing: 1.3 },
  scheduleValue: { color: colors.text, fontSize: 17, fontWeight: '700', marginTop: 7 },
  scheduleFoot: { color: colors.muted, fontSize: 11, marginTop: 4 },
  sectionHeading: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline', marginTop: 25, marginBottom: 10 },
  sectionTitle: { color: colors.text, fontSize: 17, fontWeight: '700' },
  sectionDetail: { color: colors.muted, fontSize: 11 },
  attendanceCard: { flexDirection: 'row', alignItems: 'center', gap: 11, padding: 12, marginBottom: 9, backgroundColor: colors.surface, borderRadius: 14, borderWidth: 1, borderColor: colors.border },
  staffCard: { flexDirection: 'row', alignItems: 'center', gap: 12, padding: 14, marginBottom: 10, backgroundColor: colors.surface, borderRadius: 15, borderWidth: 1, borderColor: colors.border },
  avatar: { width: 45, height: 45, borderRadius: 14, backgroundColor: colors.surfaceRaised },
  avatarPlaceholder: { width: 45, height: 45, borderRadius: 14, backgroundColor: '#173941', alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: '#2A5D66' },
  avatarInitial: { color: colors.cyan, fontWeight: '800', fontSize: 18 },
  personInfo: { flex: 1 },
  personName: { color: colors.text, fontSize: 14, fontWeight: '700' },
  personMeta: { color: colors.muted, fontSize: 10, marginTop: 3 },
  attendanceStatus: { fontSize: 9, letterSpacing: 0.7, fontWeight: '800', marginTop: 4 },
  actionButton: { backgroundColor: '#164338', borderRadius: 9, paddingHorizontal: 10, paddingVertical: 9 },
  actionButtonText: { color: colors.green, fontWeight: '800', fontSize: 9, letterSpacing: 0.5 },
  smallButton: { borderWidth: 1, borderColor: colors.border, borderRadius: 9, paddingHorizontal: 9, paddingVertical: 8 },
  smallButtonText: { color: colors.cyan, fontWeight: '800', fontSize: 9 },
  enrollmentLabel: { color: colors.orange, fontSize: 8, fontWeight: '800', letterSpacing: 0.4, marginTop: 7 },
  notice: { flexDirection: 'row', alignItems: 'flex-start', gap: 9, backgroundColor: '#1C1B16', borderColor: '#51422B', borderWidth: 1, borderRadius: 12, padding: 12, marginTop: 16 },
  noticeMark: { color: colors.orange, fontWeight: '900', fontSize: 13 },
  noticeText: { color: '#D8CBB7', fontSize: 10, lineHeight: 16, flex: 1 },
  hiddenCount: { color: colors.subtle, fontSize: 9, marginTop: 12, textAlign: 'center' },
  navigation: { flexDirection: 'row', justifyContent: 'space-around', paddingTop: 10, paddingBottom: 7, backgroundColor: '#0A1B20', borderTopWidth: 1, borderTopColor: colors.border },
  navButton: { alignItems: 'center', minWidth: 58, paddingVertical: 3 },
  navIcon: { color: colors.muted, fontSize: 18 },
  navLabel: { color: colors.muted, fontSize: 9, marginTop: 3, fontWeight: '600' },
  navActive: { color: colors.cyan },
  primaryButton: { backgroundColor: colors.green, borderRadius: 12, alignItems: 'center', justifyContent: 'center', minHeight: 47, paddingHorizontal: 16, marginTop: 15 },
  primaryButtonText: { color: '#082119', fontWeight: '800', fontSize: 13 },
  secondaryButton: { backgroundColor: colors.surfaceRaised, borderWidth: 1, borderColor: colors.border, borderRadius: 12, alignItems: 'center', justifyContent: 'center', minHeight: 45, paddingHorizontal: 16, marginTop: 10 },
  secondaryButtonText: { color: colors.cyan, fontWeight: '800', fontSize: 12 },
  emptyState: { backgroundColor: colors.surface, borderColor: colors.border, borderWidth: 1, borderRadius: 15, alignItems: 'center', padding: 22, marginTop: 12 },
  emptyMark: { color: colors.cyan, fontSize: 26, marginBottom: 10 },
  exceptionCard: { backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.border, borderRadius: 14, padding: 14, marginBottom: 10 },
  rowBetween: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 10 },
  dateTag: { color: colors.cyan, fontSize: 10, fontWeight: '700' },
  exceptionType: { color: colors.orange, fontSize: 11, fontWeight: '800', marginTop: 11 },
  exceptionReason: { color: colors.text, fontSize: 12, marginTop: 5, lineHeight: 18 },
  exceptionStamp: { color: colors.subtle, fontSize: 8, fontWeight: '700', letterSpacing: 0.8, marginTop: 10 },
  historySearch: { flexDirection: 'row', alignItems: 'center', gap: 10, marginTop: 18 },
  historyCard: { backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.border, borderRadius: 13, padding: 13, marginBottom: 9, gap: 8 },
  exportButton: { backgroundColor: colors.surfaceRaised, borderWidth: 1, borderColor: colors.border, borderRadius: 10, alignItems: 'center', padding: 12, marginBottom: 12 },
  exportButtonText: { color: colors.cyan, fontWeight: '800', fontSize: 11 },
  formCard: { backgroundColor: colors.surface, borderColor: colors.border, borderWidth: 1, borderRadius: 16, padding: 16, marginTop: 20 },
  formLabel: { color: colors.text, fontSize: 13, fontWeight: '700' },
  helper: { color: colors.muted, fontSize: 10, lineHeight: 16, marginTop: 4 },
  timeRow: { flexDirection: 'row', gap: 12, marginTop: 14 },
  timeField: { flex: 1, marginTop: 10 },
  fieldLabel: { color: colors.muted, fontSize: 9, fontWeight: '800', letterSpacing: 0.8, marginBottom: 7 },
  timeInput: { backgroundColor: colors.background, borderWidth: 1, borderColor: colors.border, borderRadius: 10, color: colors.text, fontSize: 19, fontWeight: '700', paddingHorizontal: 12, paddingVertical: 10 },
  divider: { height: 1, backgroundColor: colors.border, marginVertical: 18 },
  formField: { marginBottom: 13 },
  textInput: { backgroundColor: colors.background, borderWidth: 1, borderColor: colors.border, borderRadius: 10, color: colors.text, fontSize: 13, paddingHorizontal: 12, paddingVertical: 11 },
  multilineInput: { minHeight: 82, textAlignVertical: 'top' },
  modalSafe: { flex: 1, backgroundColor: colors.background, paddingTop: Platform.OS === 'android' ? NativeStatusBar.currentHeight : 0 },
  modalContainer: { flex: 1, backgroundColor: colors.background },
  modalHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 20, paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: colors.border },
  modalTitle: { color: colors.text, fontSize: 19, fontWeight: '700' },
  closeText: { color: colors.cyan, fontSize: 10, fontWeight: '800', letterSpacing: 0.8, padding: 5 },
  modalContent: { padding: 20, paddingBottom: 35 },
  photoCapture: { minHeight: 150, backgroundColor: colors.surface, borderColor: colors.border, borderWidth: 1, borderStyle: 'dashed', borderRadius: 15, alignItems: 'center', justifyContent: 'center', marginBottom: 18, overflow: 'hidden', padding: 15 },
  photoPreview: { width: '100%', alignItems: 'center', gap: 8 },
  photoRetake: { color: colors.cyan, fontWeight: '700', fontSize: 10 },
  cameraIcon: { color: colors.cyan, fontSize: 32, marginBottom: 5 },
  photoTitle: { color: colors.text, fontSize: 13, fontWeight: '700' },
  capturedPhoto: { width: '100%', height: 190, borderRadius: 11 },
  fingerprintBox: { borderColor: '#51422B', borderWidth: 1, borderRadius: 13, backgroundColor: '#1C1B16', padding: 13, marginTop: 5 },
  demoToggle: { flexDirection: 'row', alignItems: 'center', gap: 9, marginTop: 11 },
  checkbox: { width: 19, height: 19, borderColor: colors.subtle, borderWidth: 1, borderRadius: 5, alignItems: 'center', justifyContent: 'center' },
  checkboxChecked: { backgroundColor: colors.green, borderColor: colors.green },
  checkmark: { color: colors.background, fontSize: 13, fontWeight: '900' },
  demoToggleText: { color: colors.orange, fontSize: 10, flex: 1, lineHeight: 15 },
  cameraSafe: { flex: 1, backgroundColor: colors.background, paddingTop: Platform.OS === 'android' ? NativeStatusBar.currentHeight : 0 },
  cameraHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 20, paddingVertical: 15 },
  cameraHeaderText: { color: colors.text, fontWeight: '800', letterSpacing: 1 },
  cameraFallback: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 28 },
  cameraBody: { flex: 1, padding: 16, justifyContent: 'center' },
  cameraPreview: { flex: 1, minHeight: 360, borderRadius: 20, overflow: 'hidden' },
  cameraHint: { color: colors.muted, fontSize: 12, textAlign: 'center', marginTop: 15 },
  cameraError: { color: colors.orange, fontSize: 11, lineHeight: 16, marginTop: 12, textAlign: 'center' },
  captureButton: { backgroundColor: colors.green, alignSelf: 'center', paddingHorizontal: 25, paddingVertical: 16, borderRadius: 30, marginVertical: 20 },
  disabledCaptureButton: { opacity: 0.45 },
  captureButtonText: { color: '#082119', fontSize: 12, fontWeight: '900', letterSpacing: 0.7 },
  chipScroller: { marginVertical: 11, maxHeight: 42 },
  chip: { borderColor: colors.border, borderWidth: 1, borderRadius: 20, paddingHorizontal: 12, paddingVertical: 9, marginRight: 7, backgroundColor: colors.surface },
  chipSelected: { backgroundColor: '#164338', borderColor: colors.green },
  chipText: { color: colors.muted, fontSize: 10, fontWeight: '700' },
  chipTextSelected: { color: colors.green },
  exceptionChoice: { flexDirection: 'row', alignItems: 'center', gap: 10, borderBottomColor: colors.border, borderBottomWidth: 1, paddingVertical: 11 },
  choiceTitle: { color: colors.text, fontSize: 12, fontWeight: '700' },
  radio: { width: 18, height: 18, borderRadius: 9, borderWidth: 1, borderColor: colors.subtle },
  radioSelected: { borderWidth: 5, borderColor: colors.green },
  loading: { flex: 1, justifyContent: 'center', alignItems: 'center', gap: 8 },
});
