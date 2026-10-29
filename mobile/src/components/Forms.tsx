import { CameraView, useCameraPermissions } from 'expo-camera';
import React, { useRef, useState } from 'react';
import { Alert, Image, KeyboardAvoidingView, Modal, Platform, Pressable, SafeAreaView, ScrollView, Text, TextInput, View } from 'react-native';

import { ChoiceChip, ModalHeader, PrimaryButton } from './Shared';
import { exceptionHelp, exceptionLabels, messageOf } from '../attendanceFormatters';
import { saveCapturedPhoto } from '../repository';
import { colors, styles } from '../styles';
import type { ExceptionType, Staff } from '../types';
import type { StaffDraft } from '../uiTypes';

export function StaffModal({
  draft,
  onChange,
  onClose,
  onOpenCamera,
  onSave,
  photoUri,
  visible,
}: {
  draft: StaffDraft;
  onChange: (draft: StaffDraft) => void;
  onClose: () => void;
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
                Fingerprint enrollment is a work in progress. Android still needs the scanner vendor SDK. Desktop use needs a separately installed local bridge and a reader verified for that operating system. This app does not capture or store fingerprints yet.
              </Text>
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

export function CameraModal({
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

export function ExceptionModal({
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

export function TimeField({
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

export function FormField({
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