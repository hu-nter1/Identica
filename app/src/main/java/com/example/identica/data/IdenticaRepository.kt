package com.example.identica.data

import android.database.sqlite.SQLiteConstraintException
import com.example.identica.biometric.HashUtil
import com.example.identica.biometric.PhotoVisualSignature
import com.example.identica.data.local.AppDatabase
import com.example.identica.data.local.entity.Admin
import com.example.identica.data.local.entity.AuditLog
import com.example.identica.data.local.entity.RegisteredPerson
import com.example.identica.security.FieldMasker
import com.example.identica.security.PasswordHasher
import com.example.identica.security.Session
import com.example.identica.security.SessionManager
import com.example.identica.util.Validator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

object AuditActions {
    const val ADMIN_CREATED = "ADMIN_CREATED"
    const val ADMIN_LOGIN = "ADMIN_LOGIN"
    const val LOGIN_FAILED = "LOGIN_FAILED"
    const val ADMIN_LOGOUT = "ADMIN_LOGOUT"
    const val ADMIN_PASSWORD_RESET = "ADMIN_PASSWORD_RESET"
    const val PERSON_REGISTERED = "PERSON_REGISTERED"
    const val BIOMETRIC_SEARCH = "BIOMETRIC_SEARCH"
}

object MatchType {
    const val PHOTO = "PHOTO"
    const val FINGERPRINT = "FINGERPRINT"
    const val NONE = "NONE"
}

sealed class OpResult<out T> {
    // Expected outcomes are returned to the screen so it can show a useful message.
    data class Success<T>(val value: T) : OpResult<T>()
    data class Failure(val message: String) : OpResult<Nothing>()
}

data class PersonDraft(
    val fullName: String,
    val age: Int,
    val sex: String,
    val nationalId: String,
    val documentDetails: String,
    val photoFile: File,
    val fingerprintHash: String?
)

class IdenticaRepository(
    database: AppDatabase,
    private val sessions: SessionManager
) {

    private val adminDao = database.adminDao()
    private val personDao = database.personDao()
    private val auditLogDao = database.auditLogDao()

    suspend fun hasAnyAdmin(): Boolean = adminDao.count() > 0

    suspend fun createAdmin(username: String, password: CharArray): OpResult<Long> {
        val name = username.trim().lowercase(Locale.ROOT)
        val validationError = Validator.username(name) ?: Validator.password(password)
        if (validationError != null) return OpResult.Failure(validationError)

        val actor = sessions.currentSession()
        if (adminDao.count() > 0 && actor == null) {
            return OpResult.Failure("Admin registration is locked. Sign in as an admin to add accounts.")
        }
        if (adminDao.findByUsername(name) != null) {
            return OpResult.Failure("That username is already registered.")
        }

        val hashed = withContext(Dispatchers.Default) { PasswordHasher.hash(password) }
        val newId = try {
            adminDao.insert(
                Admin(
                    username = name,
                    passwordHash = hashed.hash,
                    salt = hashed.salt,
                    iterations = hashed.iterations,
                    createdAt = System.currentTimeMillis()
                )
            )
        } catch (error: SQLiteConstraintException) {
            return OpResult.Failure("That username is already registered.")
        }

        writeAudit(
            adminId = actor?.adminId ?: newId,
            adminUsername = actor?.username ?: name,
            action = AuditActions.ADMIN_CREATED,
            person = null,
            matchType = MatchType.NONE,
            percent = 0
        )
        return OpResult.Success(newId)
    }

    suspend fun login(username: String, password: CharArray): OpResult<Session> {
        val remaining = sessions.lockRemainingMillis()
        if (remaining > 0L) {
            return OpResult.Failure("Too many attempts. Try again in ${(remaining + 999L) / 1000L}s.")
        }

        val name = username.trim().lowercase(Locale.ROOT)
        val admin = adminDao.findByUsername(name)
        val verified = withContext(Dispatchers.Default) {
            if (admin == null) {
                PasswordHasher.burnTime(password)
                false
            } else {
                PasswordHasher.verify(password, admin.passwordHash, admin.salt, admin.iterations)
            }
        }

        if (admin == null || !verified) {
            sessions.registerFailedAttempt()
            if (admin != null) {
                writeAudit(admin.id, admin.username, AuditActions.LOGIN_FAILED, null, MatchType.NONE, 0)
            }
            return OpResult.Failure("Invalid username or password.")
        }

        sessions.clearFailedAttempts()
        sessions.startSession(admin.id, admin.username)
        writeAudit(admin.id, admin.username, AuditActions.ADMIN_LOGIN, null, MatchType.NONE, 0)
        return OpResult.Success(Session(admin.id, admin.username))
    }

    suspend fun resetAdminPassword(username: String, newPassword: CharArray): OpResult<Unit> {
        val name = username.trim().lowercase(Locale.ROOT)
        val admin = adminDao.findByUsername(name)
            ?: return OpResult.Failure("Administrator account not found.")

        val validationError = Validator.password(newPassword)
        if (validationError != null) return OpResult.Failure(validationError)

        val hashed = withContext(Dispatchers.Default) { PasswordHasher.hash(newPassword) }
        adminDao.updatePassword(
            id = admin.id,
            passwordHash = hashed.hash,
            salt = hashed.salt,
            iterations = hashed.iterations
        )

        writeAudit(
            adminId = admin.id,
            adminUsername = admin.username,
            action = AuditActions.ADMIN_PASSWORD_RESET,
            person = null,
            matchType = MatchType.NONE,
            percent = 0
        )
        sessions.clearFailedAttempts()
        return OpResult.Success(Unit)
    }

    suspend fun logout() {
        val session = sessions.currentSession()
        if (session != null) {
            writeAudit(session.adminId, session.username, AuditActions.ADMIN_LOGOUT, null, MatchType.NONE, 0)
        }
        sessions.endSession()
    }

    suspend fun registerPerson(draft: PersonDraft): OpResult<Long> {
        val actor = sessions.currentSession()
            ?: return OpResult.Failure("Session expired. Sign in again.")

        val validationError = validatePersonDraft(draft)
        if (validationError != null) return OpResult.Failure(validationError)

        val normalizedId = Validator.normalizeNationalId(draft.nationalId)
        val idHash = HashUtil.hmacSha256Hex(sessions.pepper(), normalizedId.toByteArray(Charsets.UTF_8))
        if (personDao.findByNationalIdHash(idHash) != null) {
            return OpResult.Failure("A profile with this national ID already exists.")
        }

        val photoHash = withContext(Dispatchers.IO) { PhotoVisualSignature.fromFile(draft.photoFile) }
            ?: return OpResult.Failure("The selected photo could not be processed. Please choose another image.")
        val person = RegisteredPerson(
            fullName = draft.fullName.trim(),
            age = draft.age,
            sex = draft.sex,
            nationalIdHash = idHash,
            nationalIdMasked = FieldMasker.maskNationalId(normalizedId),
            documentDetails = draft.documentDetails.trim(),
            photoPath = draft.photoFile.absolutePath,
            photoHash = photoHash,
            fingerprintHash = draft.fingerprintHash,
            registeredByAdminId = actor.adminId,
            createdAt = System.currentTimeMillis()
        )

        val newId = try {
            personDao.insert(person)
        } catch (error: SQLiteConstraintException) {
            return OpResult.Failure("A profile with this national ID already exists.")
        }

        writeAudit(
            actor.adminId,
            actor.username,
            AuditActions.PERSON_REGISTERED,
            person.copy(id = newId),
            MatchType.NONE,
            0
        )
        return OpResult.Success(newId)
    }

    private fun validatePersonDraft(draft: PersonDraft): String? {
        val nameError = Validator.fullName(draft.fullName)
        if (nameError != null) return nameError

        val ageError = Validator.age(draft.age.toString())
        if (ageError != null) return ageError

        val sexError = Validator.sex(draft.sex)
        if (sexError != null) return sexError

        val nationalIdError = Validator.nationalId(draft.nationalId)
        if (nationalIdError != null) return nationalIdError

        val documentError = Validator.documentDetails(draft.documentDetails)
        if (documentError != null) return documentError

        return Validator.photo(draft.photoFile)
    }

    suspend fun getPersons(): List<RegisteredPerson> = personDao.getAll()

    suspend fun getPersonCount(): Int = personDao.count()

    suspend fun getAdminCount(): Int = adminDao.count()

    suspend fun getAdmins(): List<Admin> = adminDao.getAll()

    suspend fun recordSearch(person: RegisteredPerson?, matchType: String, percent: Int) {
        val actor = sessions.currentSession() ?: return
        writeAudit(actor.adminId, actor.username, AuditActions.BIOMETRIC_SEARCH, person, matchType, percent)
    }

    fun observeAuditLogs(): Flow<List<AuditLog>> = auditLogDao.observeAll()

    suspend fun deleteAllAuditLogs() = auditLogDao.deleteAll()

    private suspend fun writeAudit(
        adminId: Long,
        adminUsername: String,
        action: String,
        person: RegisteredPerson?,
        matchType: String,
        percent: Int
    ) {
        auditLogDao.insert(
            AuditLog(
                adminId = adminId,
                adminUsername = adminUsername,
                action = action,
                targetPersonId = person?.id,
                targetPersonName = person?.fullName,
                targetNationalIdMasked = person?.nationalIdMasked,
                matchedDataType = matchType,
                matchPercentage = percent,
                timestamp = System.currentTimeMillis()
            )
        )
    }
}
