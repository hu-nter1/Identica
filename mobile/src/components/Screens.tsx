import React from 'react';
import { Pressable, Text, View } from 'react-native';

import { EmptyState, Notice, PersonAvatar, PrimaryButton, SectionHeading, StatCard } from './Shared';
import { FormField, TimeField } from './Forms';
import { displayTime, exceptionLabels, prettyDate, today } from '../attendanceFormatters';
import { colors, styles } from '../styles';
import type { Attendance, AttendanceException, Schedule, Staff } from '../types';
import type { FingerprintScannerStatus } from '../fingerprintScanner';

export function TodayScreen({
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
        Fingerprint scanning is a work in progress. The app does not simulate scans, and built-in
        phone biometrics cannot identify which staff member is present.
      </Notice>
      <Text style={styles.hiddenCount}>{attendance.length} attendance records for today</Text>
    </>
  );
}

export function StaffScreen({
  staff,
  scannerStatus,
  onAddStaff,
  onEnroll,
  onRefreshScanner,
  onScan,
}: {
  staff: Staff[];
  scannerStatus: FingerprintScannerStatus;
  onAddStaff: () => void;
  onEnroll: (staff: Staff) => void;
  onRefreshScanner: () => void;
  onScan: (staff: Staff) => void;
}) {
  return (
    <>
      <Text style={styles.eyebrow}>PEOPLE DIRECTORY</Text>
      <Text style={styles.title}>Registered staff</Text>
      <Text style={styles.muted}>Profiles and on-device photo records</Text>
      <PrimaryButton label="+ Register staff" onPress={onAddStaff} />
      <ScannerStatusCard status={scannerStatus} onRefresh={onRefreshScanner} />
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
                {person.fingerprintCredentialId ? 'FINGERPRINT ENROLLED' : 'FINGERPRINT NOT ENROLLED'}
              </Text>
            </View>
            <View style={styles.staffActions}>
              <Pressable
                accessibilityRole="button"
                disabled={scannerStatus.availability !== 'ready' || Boolean(person.fingerprintCredentialId)}
                onPress={() => onEnroll(person)}
                style={[styles.smallButton, scannerStatus.availability !== 'ready' && styles.disabledButton]}
              >
                <Text style={styles.smallButtonText}>{person.fingerprintCredentialId ? 'ENROLLED' : 'ENROLL'}</Text>
              </Pressable>
              <Pressable accessibilityRole="button" onPress={() => onScan(person)} style={styles.smallButton}>
                <Text style={styles.smallButtonText}>SCAN</Text>
              </Pressable>
            </View>
          </View>
        ))
      )}
    </>
  );
}

export function ScannerStatusCard({
  status,
  onRefresh,
}: {
  status: FingerprintScannerStatus;
  onRefresh: () => void;
}) {
  const ready = status.availability === 'ready';
  return (
    <View style={styles.scannerCard}>
      <View style={styles.scannerHeading}>
        <View style={[styles.scannerDot, ready && styles.scannerConnectedDot]} />
        <Text style={styles.formLabel}>
          {ready
            ? 'Fingerprint scanner connected'
            : status.availability === 'unsupported'
              ? 'Fingerprint integration unavailable'
              : 'Fingerprint support in progress'}
        </Text>
      </View>
      {status.deviceName ? <Text style={styles.scannerDevice}>{status.deviceName}</Text> : null}
      <Text style={styles.helper}>{status.detail}</Text>
      <Pressable accessibilityRole="button" onPress={onRefresh} style={styles.scannerRefresh}>
        <Text style={styles.scannerRefreshText}>Check scanner connection</Text>
      </Pressable>
    </View>
  );
}

export function ExceptionsScreen({
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

export function HistoryScreen({
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

export function SettingsScreen({
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
        This app currently has no admin sign-in or cloud sync. Anyone with access to this device
        can edit its local register. Use test data only until authentication, backups, audit
        controls, and a verified fingerprint-reader integration are added.
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