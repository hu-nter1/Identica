package com.example.identica;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.fingerprint.FingerprintManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class MainActivity extends Activity {

    private static final int SCREEN_LOGIN = 0;
    private static final int SCREEN_HOME = 1;
    private static final int SCREEN_REGISTER = 2;
    private static final int SCREEN_SCAN = 3;
    private static final int SCREEN_REGISTRY = 4;
    private static final int SCREEN_AUDIT = 5;
    private static final int SCREEN_ADMIN_REGISTER = 6;
    private static final int SCREEN_FORGOT_PASSWORD = 7;

    private static final int REQUEST_PICK_PORTRAIT = 101;
    private static final int REQUEST_TAKE_PORTRAIT = 102;
    private static final int REQUEST_PICK_SCAN = 103;
    private static final int REQUEST_TAKE_SCAN = 104;
    private static final int PASSWORD_ITERATIONS = 120_000;

    private DatabaseHelper database;
    private FrameLayout screenContainer;
    private LinearLayout bottomNavigation;
    private CancellationSignal fingerprintCancellation;
    private String operatorName = "";
    private String portraitPath = "";
    private String scanPhotoPath = "";
    private String pendingCameraPath = "";
    private String scanType = "FACE";
    private boolean ownerVerified;
    private boolean loggedIn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        database = new DatabaseHelper(this);
        screenContainer = findViewById(R.id.screenContainer);
        bottomNavigation = findViewById(R.id.bottomNavigation);
        connectNavigation();
        showScreen(SCREEN_LOGIN);
    }

    private void connectNavigation() {
        findViewById(R.id.navHome).setOnClickListener(view -> showScreen(SCREEN_HOME));
        findViewById(R.id.navRegister).setOnClickListener(view -> showScreen(SCREEN_REGISTER));
        findViewById(R.id.navScan).setOnClickListener(view -> showScreen(SCREEN_SCAN));
        findViewById(R.id.navRegistry).setOnClickListener(view -> showScreen(SCREEN_REGISTRY));
        findViewById(R.id.navAudit).setOnClickListener(view -> showScreen(SCREEN_AUDIT));
    }

    private void showScreen(int screen) {
        if (!loggedIn && screen != SCREEN_LOGIN && screen != SCREEN_ADMIN_REGISTER
                && screen != SCREEN_FORGOT_PASSWORD) {
            screen = SCREEN_LOGIN;
        }

        int layoutId;
        switch (screen) {
            case SCREEN_HOME:
                layoutId = R.layout.screen_home;
                break;
            case SCREEN_REGISTER:
                layoutId = R.layout.screen_register_identity;
                break;
            case SCREEN_SCAN:
                layoutId = R.layout.screen_scan;
                break;
            case SCREEN_REGISTRY:
                layoutId = R.layout.screen_registry;
                break;
            case SCREEN_AUDIT:
                layoutId = R.layout.screen_audit;
                break;
            case SCREEN_ADMIN_REGISTER:
                layoutId = R.layout.screen_admin_register;
                break;
            case SCREEN_FORGOT_PASSWORD:
                layoutId = R.layout.screen_forgot_password;
                break;
            default:
                screen = SCREEN_LOGIN;
                layoutId = R.layout.screen_login;
        }

        View page = LayoutInflater.from(this).inflate(layoutId, screenContainer, false);
        screenContainer.removeAllViews();
        screenContainer.addView(page);
        bottomNavigation.setVisibility(
                screen == SCREEN_LOGIN || screen == SCREEN_ADMIN_REGISTER
                        || screen == SCREEN_FORGOT_PASSWORD ? View.GONE : View.VISIBLE);
        updateNavigation(screen);

        if (screen == SCREEN_LOGIN) {
            setupLogin(page);
        } else if (screen == SCREEN_HOME) {
            setupHome(page);
        } else if (screen == SCREEN_REGISTER) {
            setupIdentityRegistration(page);
        } else if (screen == SCREEN_SCAN) {
            setupScan(page);
        } else if (screen == SCREEN_REGISTRY) {
            setupRegistry(page, null, null);
        } else if (screen == SCREEN_AUDIT) {
            setupAudit(page);
        } else if (screen == SCREEN_ADMIN_REGISTER) {
            setupAdminRegistration(page);
        } else if (screen == SCREEN_FORGOT_PASSWORD) {
            setupForgotPassword(page);
        }
    }

    private void updateNavigation(int screen) {
        int[] buttonIds = {
                R.id.navHome, R.id.navRegister, R.id.navScan, R.id.navRegistry, R.id.navAudit
        };
        int[] screens = {
                SCREEN_HOME, SCREEN_REGISTER, SCREEN_SCAN, SCREEN_REGISTRY, SCREEN_AUDIT
        };
        for (int i = 0; i < buttonIds.length; i++) {
            Button button = findViewById(buttonIds[i]);
            boolean selected = screens[i] == screen;
            button.setSelected(selected);
            button.setBackgroundResource(selected
                    ? R.drawable.bg_nav_selected : android.R.color.transparent);
            button.setTextColor(getColor(
                    selected ? R.color.mint : R.color.text_secondary));
        }
    }

    private void setupLogin(View page) {
        EditText username = page.findViewById(R.id.loginUsername);
        EditText password = page.findViewById(R.id.loginPassword);
        Button createAdminButton = page.findViewById(R.id.btnCreateAdmin);
        togglePassword(password, page.findViewById(R.id.btnToggleLoginPassword));
        createAdminButton.setVisibility(database.hasAdmins() ? View.GONE : View.VISIBLE);
        page.findViewById(R.id.btnForgotPassword)
                .setVisibility(database.hasAdmins() ? View.VISIBLE : View.GONE);

        page.findViewById(R.id.btnLogin).setOnClickListener(view -> {
            String enteredName = username.getText().toString().trim();
            String enteredPassword = password.getText().toString();
            if (enteredName.isEmpty() || enteredPassword.isEmpty()) {
                showMessage("Enter your admin username and password.");
                return;
            }

            byte[] salt = database.getPasswordSalt(enteredName);
            if (salt == null || !database.authenticate(enteredName, hashPassword(enteredPassword, salt))) {
                showMessage("The username or password is incorrect.");
                return;
            }

            operatorName = enteredName;
            if (!database.hasRecoveryCode(enteredName)) {
                showRecoveryCodeSetup(enteredName);
            } else {
                completeLogin();
            }
        });

        createAdminButton.setOnClickListener(view -> showScreen(SCREEN_ADMIN_REGISTER));
        page.findViewById(R.id.btnForgotPassword).setOnClickListener(
                view -> showScreen(SCREEN_FORGOT_PASSWORD));
    }

    private void showRecoveryCodeSetup(String username) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(12), dp(20), 0);

        EditText firstCode = addRecoveryCodeInput(form, "New 4-digit recovery code");
        EditText confirmCode = addRecoveryCodeInput(form, "Confirm recovery code");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Set a recovery code")
                .setMessage("This account was created before recovery codes were added. Set a 4-digit code to continue.")
                .setView(form)
                .setCancelable(false)
                .setPositiveButton("Save code", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String code = firstCode.getText().toString();
                    String confirmation = confirmCode.getText().toString();
                    if (!code.matches("[0-9]{4}")) {
                        showMessage("Recovery code must be exactly 4 digits.");
                        return;
                    }
                    if (!code.equals(confirmation)) {
                        showMessage("The recovery codes do not match.");
                        return;
                    }

                    byte[] salt = new byte[16];
                    new SecureRandom().nextBytes(salt);
                    if (!database.setRecoveryCode(username, salt, hashPassword(code, salt))) {
                        showMessage("Could not save the recovery code. Please try again.");
                        return;
                    }

                    dialog.dismiss();
                    showMessage("Recovery code saved.");
                    completeLogin();
                }));
        dialog.show();
    }

    private void completeLogin() {
        loggedIn = true;
        addAuditEvent("ADMIN_LOGIN");
        showScreen(SCREEN_HOME);
    }

    private void setupHome(View page) {
        TextView operator = page.findViewById(R.id.homeOperator);
        TextView count = page.findViewById(R.id.homeIdentityCount);
        operator.setText("Operator: " + operatorName.toUpperCase(Locale.ROOT));
        count.setText(String.valueOf(database.getIdentities(null).size()));

        page.findViewById(R.id.btnLogout).setOnClickListener(view -> {
            addAuditEvent("ADMIN_LOGOUT");
            loggedIn = false;
            showScreen(SCREEN_LOGIN);
        });
    }

    private void setupIdentityRegistration(View page) {
        EditText nameInput = page.findViewById(R.id.personName);
        EditText ageInput = page.findViewById(R.id.personAge);
        EditText idInput = page.findViewById(R.id.personId);
        EditText documentInput = page.findViewById(R.id.personDocument);
        RadioGroup sexGroup = page.findViewById(R.id.personSex);
        TextView faceStatus = page.findViewById(R.id.faceStatus);
        TextView fingerprintStatus = page.findViewById(R.id.fingerprintStatus);
        ImageView portraitPreview = page.findViewById(R.id.portraitPreview);

        portraitPath = "";
        ownerVerified = false;
        portraitPreview.setImageResource(R.drawable.ic_person);

        page.findViewById(R.id.btnCaptureFace).setOnClickListener(view ->
                takePhoto(REQUEST_TAKE_PORTRAIT));
        page.findViewById(R.id.btnChoosePortrait).setOnClickListener(view ->
                choosePhoto(REQUEST_PICK_PORTRAIT));
        page.findViewById(R.id.btnCaptureFingerprint).setOnClickListener(view ->
                verifyDeviceFingerprint(() -> {
                    ownerVerified = true;
                    fingerprintStatus.setText("Device owner verified successfully");
                    addAuditEvent("DEVICE_OWNER_VERIFIED_FOR_REGISTRATION");
                }));

        page.findViewById(R.id.btnSaveIdentity).setOnClickListener(view -> {
            String name = nameInput.getText().toString().trim();
            String ageText = ageInput.getText().toString().trim();
            String idNumber = idInput.getText().toString().trim();

            if (name.isEmpty() || ageText.isEmpty() || idNumber.isEmpty()) {
                showMessage("Enter a name, age, and ID number.");
                return;
            }
            if (sexGroup.getCheckedRadioButtonId() == -1) {
                showMessage("Choose an option for sex.");
                return;
            }

            int age;
            try {
                age = Integer.parseInt(ageText);
            } catch (NumberFormatException exception) {
                showMessage("Enter a valid age.");
                return;
            }
            if (age < 1 || age > 120) {
                showMessage("Enter an age from 1 to 120.");
                return;
            }

            String sex = sexGroup.getCheckedRadioButtonId() == R.id.sexMale ? "Male"
                    : sexGroup.getCheckedRadioButtonId() == R.id.sexFemale ? "Female" : "Other";
            database.addIdentity(name, age, sex, maskId(idNumber),
                    documentInput.getText().toString().trim(), portraitPath, ownerVerified);
            addAuditEvent("PERSON_REGISTERED: " + name);
            showMessage("Identity saved on this device.");
            showScreen(SCREEN_REGISTRY);
        });
    }

    private void setupScan(View page) {
        TextView title = page.findViewById(R.id.scanPreviewTitle);
        TextView percent = page.findViewById(R.id.scanPreviewPercent);
        TextView status = page.findViewById(R.id.scanStatus);
        Button scanButton = page.findViewById(R.id.btnScan);
        Button uploadButton = page.findViewById(R.id.btnUploadScan);
        EditText searchText = page.findViewById(R.id.searchText);
        ImageView scanPhotoPreview = page.findViewById(R.id.scanPhotoPreview);
        Button photoTab = page.findViewById(R.id.btnFaceTab);
        Button phoneCheckTab = page.findViewById(R.id.btnFingerprintTab);

        scanType = "FACE";
        scanPhotoPath = "";
        scanPhotoPreview.setImageDrawable(null);
        scanPhotoPreview.setVisibility(View.GONE);
        title.setText("PHOTO SEARCH");
        percent.setText("READY");
        status.setText("Search by name/ID or choose a portrait");
        scanButton.setText("Take portrait photo");
        updateScanTabs(photoTab, phoneCheckTab, true);

        photoTab.setOnClickListener(view -> {
            scanType = "FACE";
            title.setText("PHOTO SEARCH");
            percent.setText(scanPhotoPath.isEmpty() ? "READY" : "PHOTO READY");
            scanButton.setText("Take portrait photo");
            uploadButton.setVisibility(View.VISIBLE);
            status.setText(scanPhotoPath.isEmpty()
                    ? "Choose or take a photo, then compare profiles manually."
                    : "Photo selected. Results are for manual comparison only.");
            updateScanTabs(photoTab, phoneCheckTab, true);
        });

        phoneCheckTab.setOnClickListener(view -> {
            scanType = "FINGERPRINT";
            title.setText("PHONE OWNER CHECK");
            percent.setText("READY");
            scanButton.setText("Verify with device fingerprint");
            uploadButton.setVisibility(View.GONE);
            status.setText("Verifies this phone's owner; does not identify a profile");
            updateScanTabs(photoTab, phoneCheckTab, false);
        });

        scanButton.setOnClickListener(view -> {
            if ("FINGERPRINT".equals(scanType)) {
                verifyDeviceFingerprint(() -> {
                    percent.setText("VERIFIED");
                    status.setText("Phone owner verified. This is not a registry match.");
                    addAuditEvent("DEVICE_OWNER_VERIFIED_DURING_SEARCH");
                });
            } else {
                takePhoto(REQUEST_TAKE_SCAN);
            }
        });

        uploadButton.setOnClickListener(view -> choosePhoto(REQUEST_PICK_SCAN));
        page.findViewById(R.id.btnStartSearch).setOnClickListener(view -> {
            String query = searchText.getText().toString().trim();
            if (query.isEmpty() && scanPhotoPath.isEmpty()) {
                showMessage("Enter a name/ID or choose a photo for manual comparison.");
                return;
            }
            setupRegistryWithSearch(query, scanPhotoPath);
        });
    }

    private void setupRegistryWithSearch(String query, String referencePhotoPath) {
        View page = LayoutInflater.from(this).inflate(
                R.layout.screen_registry, screenContainer, false);
        screenContainer.removeAllViews();
        screenContainer.addView(page);
        updateNavigation(SCREEN_REGISTRY);
        setupRegistry(page, query.isEmpty() ? null : query, referencePhotoPath);
    }

    private void setupRegistry(View page, String searchText, String referencePhotoPath) {
        TextView count = page.findViewById(R.id.registryIdentityCount);
        LinearLayout list = page.findViewById(R.id.identityList);
        ImageView referencePhoto = page.findViewById(R.id.searchReferencePhoto);
        TextView referenceNote = page.findViewById(R.id.searchReferenceNote);
        ArrayList<DatabaseHelper.Identity> identities = database.getIdentities(searchText);
        String label = "SEARCH RESULTS: ";
        count.setText(searchText == null
                ? identities.size() + " REGISTERED " + (identities.size() == 1 ? "PROFILE" : "PROFILES")
                : label + identities.size());

        if (referencePhotoPath != null && !referencePhotoPath.isEmpty()) {
            referencePhoto.setImageBitmap(decodePreview(referencePhotoPath, dp(500)));
            referencePhoto.setVisibility(View.VISIBLE);
            referenceNote.setVisibility(View.VISIBLE);
            referenceNote.setText(searchText == null
                    ? "Photo reference selected. Compare the profiles below manually."
                    : "Photo reference selected. Text results are filtered; compare profiles manually.");
        }

        for (DatabaseHelper.Identity identity : identities) {
            LinearLayout card = new LinearLayout(this);
            card.setBackgroundResource(R.drawable.bg_card);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setPadding(dp(12), dp(12), dp(12), dp(12));

            ImageView photo = new ImageView(this);
            LinearLayout.LayoutParams photoParams = new LinearLayout.LayoutParams(dp(88), dp(100));
            photoParams.setMarginEnd(dp(12));
            photo.setLayoutParams(photoParams);
            photo.setContentDescription("Portrait of " + identity.name);
            photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            photo.setImageResource(R.drawable.ic_person);
            if (!identity.photoPath.isEmpty()) {
                Bitmap bitmap = decodePreview(identity.photoPath, dp(220));
                if (bitmap != null) {
                    photo.setImageBitmap(bitmap);
                }
            }
            card.addView(photo);

            LinearLayout details = new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
            details.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            TextView profileText = new TextView(this);
            String verification = identity.ownerVerified ? "\nPhone owner verified at registration" : "";
            profileText.setText(identity.name + "\nAge " + identity.age + "  ·  " + identity.sex
                    + "\nID " + identity.maskedId + verification);
            profileText.setTextColor(getColor(R.color.text_primary));
            profileText.setTextSize(15);
            profileText.setLineSpacing(dp(2), 1f);
            details.addView(profileText);

            Button deleteButton = new Button(this);
            deleteButton.setText("Delete profile");
            deleteButton.setAllCaps(false);
            deleteButton.setTextSize(13);
            deleteButton.setTextColor(getColor(R.color.text_primary));
            deleteButton.setBackgroundResource(R.drawable.bg_button_outline);
            deleteButton.setMinHeight(dp(44));
            deleteButton.setPadding(dp(12), 0, dp(12), 0);
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(44));
            deleteParams.topMargin = dp(8);
            deleteButton.setLayoutParams(deleteParams);
            deleteButton.setOnClickListener(view -> new AlertDialog.Builder(this)
                    .setTitle("Delete " + identity.name + "?")
                    .setMessage("This permanently removes the profile from this device.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete", (dialog, which) -> {
                        database.deleteIdentity(identity.id);
                        deletePortrait(identity.photoPath);
                        addAuditEvent("PERSON_DELETED: " + identity.name);
                        if (searchText == null && referencePhotoPath == null) {
                            showScreen(SCREEN_REGISTRY);
                        } else {
                            setupRegistryWithSearch(
                                    searchText == null ? "" : searchText, referencePhotoPath);
                        }
                    })
                    .show());
            details.addView(deleteButton);
            card.addView(details);

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.bottomMargin = dp(12);
            list.addView(card, cardParams);

            card.setOnClickListener(view -> showIdentityDetails(identity));
        }

        if (identities.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(searchText == null
                    ? "No profiles yet. Register an identity to start building your local registry."
                    : "No profiles match this search. Try another name or ID.");
            empty.setBackgroundResource(R.drawable.bg_card);
            empty.setTextColor(getColor(R.color.text_secondary));
            empty.setTextSize(14);
            empty.setPadding(dp(18), dp(18), dp(18), dp(18));
            list.addView(empty);
        }
    }

    private void showIdentityDetails(DatabaseHelper.Identity identity) {
        String details = "Age: " + identity.age + "\nSex: " + identity.sex
                + "\nID: " + identity.maskedId
                + "\nDocument: " + identity.documentDetails
                + "\nPhone owner verified at registration: "
                + (identity.ownerVerified ? "Yes" : "No");
        new AlertDialog.Builder(this)
                .setTitle(identity.name)
                .setMessage(details)
                .setPositiveButton("Close", null)
                .show();
    }

    private void setupAudit(View page) {
        LinearLayout list = page.findViewById(R.id.auditList);
        TextView count = page.findViewById(R.id.auditEventCount);
        ArrayList<String> events = database.getAuditEvents();
        count.setText(events.size() + (events.size() == 1
                ? " RECORDED EVENT" : " RECORDED EVENTS"));
        for (String event : events) {
            TextView card = new TextView(this);
            card.setBackgroundResource(R.drawable.bg_card);
            card.setText(event);
            card.setTextColor(getColor(R.color.text_primary));
            card.setTextSize(14);
            card.setPadding(dp(18), dp(18), dp(18), dp(18));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(12);
            list.addView(card, params);
        }

        if (events.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No activity yet. Sign-ins and profile changes will appear here.");
            empty.setBackgroundResource(R.drawable.bg_card);
            empty.setTextColor(getColor(R.color.text_secondary));
            empty.setTextSize(14);
            empty.setPadding(dp(18), dp(18), dp(18), dp(18));
            list.addView(empty);
        }

        page.findViewById(R.id.btnClearLogs).setOnClickListener(view ->
                new AlertDialog.Builder(this)
                        .setTitle("Delete all logs?")
                        .setMessage("This removes all saved activity from this device.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (dialog, which) -> {
                            database.deleteAuditEvents();
                            showScreen(SCREEN_AUDIT);
                        })
                        .show());
    }

    private void setupAdminRegistration(View page) {
        EditText username = page.findViewById(R.id.newAdminUsername);
        EditText password = page.findViewById(R.id.newAdminPassword);
        EditText confirmPassword = page.findViewById(R.id.confirmAdminPassword);
        EditText recoveryCode = page.findViewById(R.id.newRecoveryCode);
        EditText confirmRecoveryCode = page.findViewById(R.id.confirmRecoveryCode);

        togglePassword(password, page.findViewById(R.id.btnToggleNewPassword));
        togglePassword(confirmPassword, page.findViewById(R.id.btnToggleConfirmPassword));
        togglePassword(recoveryCode, page.findViewById(R.id.btnToggleNewRecoveryCode));
        togglePassword(confirmRecoveryCode, page.findViewById(R.id.btnToggleConfirmRecoveryCode));

        page.findViewById(R.id.btnBackToLogin)
                .setOnClickListener(view -> showScreen(SCREEN_LOGIN));

        page.findViewById(R.id.btnSaveAdmin).setOnClickListener(view -> {
            String name = username.getText().toString().trim();
            String pass = password.getText().toString();
            String confirm = confirmPassword.getText().toString();
            String code = recoveryCode.getText().toString();
            String confirmCode = confirmRecoveryCode.getText().toString();

            if (database.hasAdmins()) {
                showMessage("An admin account already exists.");
                showScreen(SCREEN_LOGIN);
            } else if (name.isEmpty()) {
                showMessage("Enter an admin username.");
            } else if (pass.length() < 4) {
                showMessage("Password must contain at least 4 characters.");
            } else if (!pass.equals(confirm)) {
                showMessage("The passwords do not match.");
            } else if (!code.matches("[0-9]{4}")) {
                showMessage("Recovery code must be exactly 4 digits.");
            } else if (!code.equals(confirmCode)) {
                showMessage("The recovery codes do not match.");
            } else {
                byte[] salt = new byte[16];
                byte[] recoverySalt = new byte[16];
                new SecureRandom().nextBytes(salt);
                new SecureRandom().nextBytes(recoverySalt);
                boolean saved = database.saveAdmin(name, salt, hashPassword(pass, salt),
                        recoverySalt, hashPassword(code, recoverySalt));
                if (!saved) {
                    showMessage("That username already exists.");
                    return;
                }
                showMessage("Admin account and recovery code saved. Sign in now.");
                showScreen(SCREEN_LOGIN);
            }
        });
    }

    private void setupForgotPassword(View page) {
        EditText username = page.findViewById(R.id.recoveryUsername);
        EditText recoveryCode = page.findViewById(R.id.recoveryCode);
        EditText newPassword = page.findViewById(R.id.recoveryNewPassword);
        EditText confirmPassword = page.findViewById(R.id.recoveryConfirmPassword);
        togglePassword(recoveryCode, page.findViewById(R.id.btnToggleRecoveryCode));
        togglePassword(newPassword, page.findViewById(R.id.btnToggleRecoveryPassword));
        togglePassword(confirmPassword, page.findViewById(R.id.btnToggleRecoveryConfirmPassword));

        page.findViewById(R.id.btnCancelRecovery)
                .setOnClickListener(view -> showScreen(SCREEN_LOGIN));
        page.findViewById(R.id.btnResetPassword).setOnClickListener(view -> {
            String name = username.getText().toString().trim();
            String code = recoveryCode.getText().toString();
            String pass = newPassword.getText().toString();
            String confirm = confirmPassword.getText().toString();

            if (name.isEmpty() || code.isEmpty() || pass.isEmpty() || confirm.isEmpty()) {
                showMessage("Fill in all fields.");
                return;
            }
            if (!code.matches("[0-9]{4}")) {
                showMessage("Enter the 4-digit recovery code.");
                return;
            }
            if (pass.length() < 4) {
                showMessage("Password must contain at least 4 characters.");
                return;
            }
            if (!pass.equals(confirm)) {
                showMessage("The passwords do not match.");
                return;
            }

            byte[] recoverySalt = database.getRecoverySalt(name);
            if (recoverySalt == null
                    || !database.recoveryCodeMatches(name, hashPassword(code, recoverySalt))) {
                showMessage("Username or recovery code is incorrect.");
                return;
            }

            byte[] newSalt = new byte[16];
            new SecureRandom().nextBytes(newSalt);
            if (!database.updateAdminPassword(name, newSalt, hashPassword(pass, newSalt))) {
                showMessage("Could not update the password. Please try again.");
                return;
            }

            operatorName = name;
            addAuditEvent("ADMIN_PASSWORD_RESET");
            showMessage("Password changed. Sign in with the new password.");
            showScreen(SCREEN_LOGIN);
        });
    }

    private void togglePassword(EditText input, ImageButton button) {
        button.setOnClickListener(view -> {
            int cursorPosition = input.getSelectionStart();
            boolean isHidden = input.getTransformationMethod() instanceof PasswordTransformationMethod;
            input.setTransformationMethod(isHidden
                    ? HideReturnsTransformationMethod.getInstance()
                    : PasswordTransformationMethod.getInstance());
            button.setImageResource(isHidden ? R.drawable.ic_eye : R.drawable.ic_eye_off);
            button.setContentDescription(isHidden ? "Hide password" : "Show password");
            input.setSelection(Math.max(0, cursorPosition));
        });
    }

    private void updateScanTabs(Button photoTab, Button phoneCheckTab, boolean photoSelected) {
        Button selected = photoSelected ? photoTab : phoneCheckTab;
        Button unselected = photoSelected ? phoneCheckTab : photoTab;
        selected.setBackgroundResource(R.drawable.bg_nav_selected);
        selected.setTextColor(getColor(R.color.mint));
        unselected.setBackgroundResource(R.drawable.bg_button_outline);
        unselected.setTextColor(getColor(R.color.text_primary));
    }

    private EditText addRecoveryCodeInput(LinearLayout parent, String hint) {
        LinearLayout row = new LinearLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58)));
        row.setBackgroundResource(R.drawable.bg_input);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);

        EditText input = new EditText(this);
        input.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1));
        input.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        input.setHint(hint);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(4)});
        row.addView(input);

        ImageButton toggle = new ImageButton(this);
        toggle.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        toggle.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        toggle.setContentDescription("Show recovery code");
        toggle.setPadding(dp(12), dp(12), dp(12), dp(12));
        toggle.setScaleType(ImageView.ScaleType.FIT_CENTER);
        toggle.setImageResource(R.drawable.ic_eye_off);
        row.addView(toggle);
        parent.addView(row);
        togglePassword(input, toggle);
        return input;
    }

    private void verifyDeviceFingerprint(Runnable onSuccess) {
        FingerprintManager fingerprintManager = getSystemService(FingerprintManager.class);
        if (fingerprintManager == null || !fingerprintManager.isHardwareDetected()) {
            showMessage("This device does not have a fingerprint sensor.");
            return;
        }
        if (!fingerprintManager.hasEnrolledFingerprints()) {
            showMessage("Enroll a fingerprint in the device settings first.");
            return;
        }

        fingerprintCancellation = new CancellationSignal();
        fingerprintManager.authenticate(null, fingerprintCancellation, 0,
                new FingerprintManager.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            FingerprintManager.AuthenticationResult result) {
                        runOnUiThread(onSuccess);
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        runOnUiThread(() -> showMessage("Fingerprint not recognized. Try again."));
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errorString) {
                        runOnUiThread(() -> showMessage("Fingerprint check stopped: " + errorString));
                    }
                }, null);
    }

    private void choosePhoto(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, requestCode);
        } catch (android.content.ActivityNotFoundException exception) {
            showMessage("No photo picker is available on this device.");
        }
    }

    private void takePhoto(int requestCode) {
        File photoFile = newPhotoFile();
        pendingCameraPath = photoFile.getAbsolutePath();
        Uri photoUri = FileProvider.getUriForFile(
                this, getPackageName() + ".fileprovider", photoFile);
        Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
        intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, photoUri);
        intent.setClipData(ClipData.newRawUri("Portrait photo", photoUri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            startActivityForResult(intent, requestCode);
        } catch (android.content.ActivityNotFoundException exception) {
            deletePortrait(pendingCameraPath);
            pendingCameraPath = "";
            showMessage("No camera app is available on this device.");
        }
    }

    private void deletePortrait(String path) {
        if (path == null || path.isEmpty()) {
            return;
        }
        File portraitsDirectory = new File(getFilesDir(), "portraits");
        try {
            String directoryPath = portraitsDirectory.getCanonicalPath() + File.separator;
            String photoPath = new File(path).getCanonicalPath();
            if (photoPath.startsWith(directoryPath)) {
                new File(photoPath).delete();
            }
        } catch (IOException exception) {
            showMessage("Profile deleted, but its portrait file could not be removed.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        boolean isScanPhoto = requestCode == REQUEST_PICK_SCAN || requestCode == REQUEST_TAKE_SCAN;
        boolean isCameraRequest = requestCode == REQUEST_TAKE_PORTRAIT
                || requestCode == REQUEST_TAKE_SCAN;
        boolean isGalleryRequest = requestCode == REQUEST_PICK_PORTRAIT
                || requestCode == REQUEST_PICK_SCAN;
        if (!isCameraRequest && !isGalleryRequest) {
            return;
        }
        if (resultCode != RESULT_OK) {
            if (isCameraRequest) {
                deletePortrait(pendingCameraPath);
                pendingCameraPath = "";
            }
            return;
        }
        String savedPath;
        if (isGalleryRequest) {
            if (data == null) {
                showMessage("The selected photo could not be opened.");
                return;
            }
            if (data.getData() == null) {
                showMessage("The selected photo could not be opened.");
                return;
            }
            savedPath = copyPhotoToPrivateStorage(data.getData());
        } else {
            File cameraFile = pendingCameraPath.isEmpty() ? null : new File(pendingCameraPath);
            if (cameraFile != null && cameraFile.isFile() && cameraFile.length() > 0) {
                savedPath = cameraFile.getAbsolutePath();
            } else {
                Object image = data == null || data.getExtras() == null
                        ? null : data.getExtras().get("data");
                if (!(image instanceof Bitmap)) {
                    deletePortrait(pendingCameraPath);
                    pendingCameraPath = "";
                    showMessage("The camera did not save a photo.");
                    return;
                }
                savedPath = saveBitmapToPrivateStorage((Bitmap) image);
                deletePortrait(pendingCameraPath);
            }
            pendingCameraPath = "";
            if (savedPath == null) {
                showMessage("The camera photo could not be saved.");
                return;
            }
        }

        if (savedPath == null) {
            return;
        }
        if (isScanPhoto) {
            scanPhotoPath = savedPath;
            View page = screenContainer.getChildAt(0);
            TextView status = page.findViewById(R.id.scanStatus);
            ImageView preview = page.findViewById(R.id.scanPhotoPreview);
            if (status != null) {
                status.setText("Photo selected. Results are for manual comparison only.");
            }
            TextView previewTitle = page.findViewById(R.id.scanPreviewTitle);
            TextView previewStatus = page.findViewById(R.id.scanPreviewPercent);
            if (previewTitle != null) {
                previewTitle.setText("PHOTO REFERENCE");
            }
            if (previewStatus != null) {
                previewStatus.setText("SELECTED");
            }
            if (preview != null) {
                preview.setImageBitmap(decodePreview(savedPath, dp(500)));
                preview.setVisibility(View.VISIBLE);
            }
            addAuditEvent("PORTRAIT_PHOTO_SELECTED_FOR_SEARCH");
        } else {
            portraitPath = savedPath;
            View page = screenContainer.getChildAt(0);
            ImageView preview = page.findViewById(R.id.portraitPreview);
            TextView status = page.findViewById(R.id.faceStatus);
            if (preview != null) {
                preview.setImageBitmap(decodePreview(savedPath, dp(500)));
            }
            if (status != null) {
                status.setText("Portrait photo saved on this device");
            }
        }
    }

    private Bitmap decodePreview(String path, int maxDimension) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null;
        }
        options.inSampleSize = 1;
        while (options.outWidth / options.inSampleSize > maxDimension
                || options.outHeight / options.inSampleSize > maxDimension) {
            options.inSampleSize *= 2;
        }
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(path, options);
    }

    private String copyPhotoToPrivateStorage(android.net.Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                showMessage("Could not read the selected photo.");
                return null;
            }
            File photoFile = newPhotoFile();
            try (FileOutputStream output = new FileOutputStream(photoFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
            }
            return photoFile.getAbsolutePath();
        } catch (IOException exception) {
            showMessage("Could not save the selected photo.");
            return null;
        }
    }

    private String saveBitmapToPrivateStorage(Bitmap bitmap) {
        File photoFile = newPhotoFile();
        try (FileOutputStream output = new FileOutputStream(photoFile)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) {
                showMessage("Could not save the camera photo.");
                return null;
            }
            return photoFile.getAbsolutePath();
        } catch (IOException exception) {
            showMessage("Could not save the camera photo.");
            return null;
        }
    }

    private File newPhotoFile() {
        File directory = new File(getFilesDir(), "portraits");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create the private portrait folder.");
        }
        return new File(directory, "portrait-" + System.currentTimeMillis() + ".jpg");
    }

    private byte[] hashPassword(String password, byte[] salt) {
        PBEKeySpec specification = new PBEKeySpec(
                password.toCharArray(), salt, PASSWORD_ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                    .generateSecret(specification).getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException("Password hashing is not available on this device.", exception);
        } finally {
            specification.clearPassword();
        }
    }

    private void addAuditEvent(String event) {
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
        database.addAuditEvent(event, operatorName, time);
    }

    private String maskId(String id) {
        if (id.length() <= 4) {
            return "****";
        }
        return "******" + id.substring(id.length() - 4);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        if (fingerprintCancellation != null) {
            fingerprintCancellation.cancel();
        }
        if (database != null) {
            database.close();
        }
        super.onDestroy();
    }
}
