package com.example.identica;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "identica.db";
    private static final int DATABASE_VERSION = 2;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE admins ("
                + "username TEXT PRIMARY KEY COLLATE NOCASE, "
                + "salt BLOB NOT NULL, "
                + "password_hash BLOB NOT NULL, "
                + "recovery_salt BLOB, "
                + "recovery_hash BLOB)");

        database.execSQL("CREATE TABLE identities ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL, "
                + "age INTEGER NOT NULL, "
                + "sex TEXT NOT NULL, "
                + "masked_id TEXT NOT NULL, "
                + "document_details TEXT NOT NULL, "
                + "photo_path TEXT NOT NULL, "
                + "owner_verified INTEGER NOT NULL)");

        database.execSQL("CREATE TABLE audit_events ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "event TEXT NOT NULL, "
                + "admin TEXT NOT NULL, "
                + "time TEXT NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            database.execSQL("ALTER TABLE admins ADD COLUMN recovery_salt BLOB");
            database.execSQL("ALTER TABLE admins ADD COLUMN recovery_hash BLOB");
        }
    }

    public boolean hasAdmins() {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT 1 FROM admins LIMIT 1", null)) {
            return cursor.moveToFirst();
        }
    }

    public boolean saveAdmin(String username, byte[] salt, byte[] passwordHash,
                             byte[] recoverySalt, byte[] recoveryHash) {
        ContentValues values = new ContentValues();
        values.put("username", username);
        values.put("salt", salt);
        values.put("password_hash", passwordHash);
        values.put("recovery_salt", recoverySalt);
        values.put("recovery_hash", recoveryHash);
        return getWritableDatabase().insert("admins", null, values) != -1;
    }

    public boolean authenticate(String username, byte[] passwordHash) {
        try (Cursor cursor = getReadableDatabase().query(
                "admins", new String[]{"password_hash"}, "username = ?",
                new String[]{username}, null, null, null)) {
            return cursor.moveToFirst() && constantTimeEquals(passwordHash, cursor.getBlob(0));
        }
    }

    public byte[] getPasswordSalt(String username) {
        try (Cursor cursor = getReadableDatabase().query(
                "admins", new String[]{"salt"}, "username = ?",
                new String[]{username}, null, null, null)) {
            return cursor.moveToFirst() ? cursor.getBlob(0) : null;
        }
    }

    public byte[] getRecoverySalt(String username) {
        try (Cursor cursor = getReadableDatabase().query(
                "admins", new String[]{"recovery_salt"}, "username = ?",
                new String[]{username}, null, null, null)) {
            return cursor.moveToFirst() ? cursor.getBlob(0) : null;
        }
    }

    public boolean hasRecoveryCode(String username) {
        return getRecoverySalt(username) != null;
    }

    public boolean setRecoveryCode(String username, byte[] salt, byte[] recoveryHash) {
        ContentValues values = new ContentValues();
        values.put("recovery_salt", salt);
        values.put("recovery_hash", recoveryHash);
        return getWritableDatabase().update(
                "admins", values, "username = ?", new String[]{username}) == 1;
    }

    public boolean recoveryCodeMatches(String username, byte[] recoveryHash) {
        try (Cursor cursor = getReadableDatabase().query(
                "admins", new String[]{"recovery_hash"}, "username = ?",
                new String[]{username}, null, null, null)) {
            return cursor.moveToFirst()
                    && constantTimeEquals(recoveryHash, cursor.getBlob(0));
        }
    }

    public boolean updateAdminPassword(String username, byte[] salt, byte[] passwordHash) {
        ContentValues values = new ContentValues();
        values.put("salt", salt);
        values.put("password_hash", passwordHash);
        return getWritableDatabase().update(
                "admins", values, "username = ?", new String[]{username}) == 1;
    }

    public long addIdentity(String name, int age, String sex, String maskedId,
                            String documentDetails, String photoPath, boolean ownerVerified) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("age", age);
        values.put("sex", sex);
        values.put("masked_id", maskedId);
        values.put("document_details", documentDetails);
        values.put("photo_path", photoPath);
        values.put("owner_verified", ownerVerified ? 1 : 0);
        return getWritableDatabase().insertOrThrow("identities", null, values);
    }

    public ArrayList<Identity> getIdentities(String searchText) {
        ArrayList<Identity> identities = new ArrayList<>();
        String selection = null;
        String[] selectionArgs = null;
        if (searchText != null && !searchText.trim().isEmpty()) {
            selection = "name LIKE ? OR masked_id LIKE ?";
            String match = "%" + searchText.trim() + "%";
            selectionArgs = new String[]{match, match};
        }

        try (Cursor cursor = getReadableDatabase().query(
                "identities",
                new String[]{"id", "name", "age", "sex", "masked_id",
                        "document_details", "photo_path", "owner_verified"},
                selection, selectionArgs, null, null, "name COLLATE NOCASE ASC")) {
            while (cursor.moveToNext()) {
                identities.add(new Identity(
                        cursor.getLong(0), cursor.getString(1), cursor.getInt(2),
                        cursor.getString(3), cursor.getString(4), cursor.getString(5),
                        cursor.getString(6), cursor.getInt(7) == 1));
            }
        }
        return identities;
    }

    public boolean deleteIdentity(long id) {
        return getWritableDatabase().delete(
                "identities", "id = ?", new String[]{String.valueOf(id)}) > 0;
    }

    public void addAuditEvent(String event, String admin, String time) {
        ContentValues values = new ContentValues();
        values.put("event", event);
        values.put("admin", admin);
        values.put("time", time);
        getWritableDatabase().insertOrThrow("audit_events", null, values);
    }

    public ArrayList<String> getAuditEvents() {
        ArrayList<String> events = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "audit_events", new String[]{"event", "admin", "time"},
                null, null, null, null, "id DESC")) {
            while (cursor.moveToNext()) {
                events.add(cursor.getString(0) + "\nADMIN: " + cursor.getString(1)
                        + "\n" + cursor.getString(2));
            }
        }
        return events;
    }

    public void deleteAuditEvents() {
        getWritableDatabase().delete("audit_events", null, null);
    }

    private boolean constantTimeEquals(byte[] first, byte[] second) {
        if (first == null || second == null || first.length != second.length) {
            return false;
        }
        int difference = 0;
        for (int i = 0; i < first.length; i++) {
            difference |= first[i] ^ second[i];
        }
        return difference == 0;
    }

    public static class Identity {
        public final long id;
        public final String name;
        public final int age;
        public final String sex;
        public final String maskedId;
        public final String documentDetails;
        public final String photoPath;
        public final boolean ownerVerified;

        Identity(long id, String name, int age, String sex, String maskedId,
                 String documentDetails, String photoPath, boolean ownerVerified) {
            this.id = id;
            this.name = name;
            this.age = age;
            this.sex = sex;
            this.maskedId = maskedId;
            this.documentDetails = documentDetails;
            this.photoPath = photoPath;
            this.ownerVerified = ownerVerified;
        }
    }
}
