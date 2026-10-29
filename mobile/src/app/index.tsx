import { StatusBar } from 'expo-status-bar';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, AppState, SafeAreaView, ScrollView, Share, Text, View } from 'react-native';

import { CameraModal, ExceptionModal, StaffModal } from '../components/Forms';
import { NavButton } from '../components/Shared';
import {
  ExceptionsScreen,
  HistoryScreen,
  SettingsScreen,
  StaffScreen,
  TodayScreen,
} from '../components/Screens';
import { styles } from '../styles';
import { displayTime, initialDraft, messageOf, today } from '../attendanceFormatters';
import { attendanceRepository } from '../repository';
import { fingerprintScanner, type FingerprintScannerStatus } from '../fingerprintScanner';
import type { Attendance, AttendanceException, ExceptionType, NewStaff, Schedule, Staff } from '../types';
import type { Screen, StaffDraft } from '../uiTypes';

export default function App() {
  const [screen, setScreen] = useState<Screen>('today');
  const [staff, setStaff] = useState<Staff[]>([]);
  const [attendance, setAttendance] = useState<Attendance[]>([]);
  const [historyDate, setHistoryDate] = useState(today());
  const [historyAttendance, setHistoryAttendance] = useState<Attendance[]>([]);
  const [exceptions, setExceptions] = useState<AttendanceException[]>([]);
  const [scannerStatus, setScannerStatus] = useState<FingerprintScannerStatus>({
    availability: 'disconnected',
    deviceName: null,
    detail: 'Checking scanner integration…',
  });
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
  const [exceptionStaffId, setExceptionStaffId] = useState<number | null>(null);
  const [exceptionType, setExceptionType] = useState<ExceptionType>('approved_absence');
  const [exceptionDate, setExceptionDate] = useState(today());
  const [exceptionReason, setExceptionReason] = useState('');
  const [scheduleDraft, setScheduleDraft] = useState(schedule);

  const refresh = useCallback(async () => {
    try {
      await attendanceRepository.initialize();
      const [registeredStaff, todayAttendance, registeredExceptions, currentSchedule, currentScannerStatus] = await Promise.all([
        attendanceRepository.getStaff(),
        attendanceRepository.getAttendance(today()),
        attendanceRepository.getExceptions(),
        attendanceRepository.getSchedule(),
        fingerprintScanner.getStatus(),
      ]);
      setStaff(registeredStaff);
      setAttendance(todayAttendance);
      setExceptions(registeredExceptions);
      setSchedule(currentSchedule);
      setScheduleDraft(currentSchedule);
      setScannerStatus(currentScannerStatus);
      setReady(true);
    } catch (error) {
      Alert.alert('Could not load attendance data', messageOf(error));
    }
  }, []);

  useEffect(() => {
    void Promise.resolve().then(refresh);
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
    void (async () => {
      try {
        const status = await fingerprintScanner.getStatus();
        setScannerStatus(status);
        if (status.availability !== 'ready') {
          Alert.alert('Fingerprint scanner unavailable', status.detail);
          return;
        }
        const staffNumber = await fingerprintScanner.identify();
        if (staffNumber.toLowerCase() !== person.staffNumber.toLowerCase()) {
          throw new Error(`The scanned fingerprint belongs to staff number ${staffNumber}, not ${person.staffNumber}. No attendance was recorded.`);
        }
        const record = attendanceByStaff.get(person.id);
        const checkingOut = Boolean(record?.checkInAt && !record.checkOutAt);
        if (checkingOut) await attendanceRepository.checkOut(person.id);
        else await attendanceRepository.checkIn(person.id);
        await refresh();
        Alert.alert('Attendance saved', `${person.fullName} ${checkingOut ? 'checked out' : 'checked in'} successfully.`);
      } catch (error) {
        Alert.alert('Attendance not recorded', messageOf(error));
        await refresh();
      }
    })();
  };

  const enrollFingerprint = async (person: Staff) => {
    try {
      const status = await fingerprintScanner.getStatus();
      setScannerStatus(status);
      if (status.availability !== 'ready') {
        Alert.alert('Fingerprint scanner unavailable', status.detail);
        return;
      }
      const credentialId = await fingerprintScanner.enroll({
        staffNumber: person.staffNumber,
        fullName: person.fullName,
      });
      await attendanceRepository.saveFingerprintCredential(person.id, credentialId);
      await refresh();
      Alert.alert('Fingerprint enrolled', `${person.fullName} was enrolled on the connected scanner.`);
    } catch (error) {
      Alert.alert('Could not enroll fingerprint', messageOf(error));
      await refresh();
    }
  };

  const openNewStaff = () => {
    setDraft(initialDraft);
    setPhotoUri(null);
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
      };
      await attendanceRepository.addStaff(newStaff);
      await refresh();
      if (addAnother) {
        setDraft(initialDraft);
        setPhotoUri(null);
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
            <StaffScreen
              staff={staff}
              scannerStatus={scannerStatus}
              onAddStaff={openNewStaff}
              onEnroll={enrollFingerprint}
              onRefreshScanner={() => void refresh()}
              onScan={confirmScan}
            />
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
        onChange={setDraft}
        onClose={() => setStaffModal(false)}
        onOpenCamera={() => setCameraOpen(true)}
        onSave={createStaff}
        photoUri={photoUri}
        visible={staffModal}
      />
      <CameraModal
        key={cameraOpen ? 'open' : 'closed'}
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