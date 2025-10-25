package com.github.cleveard.bibliotech.utils

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.github.cleveard.bibliotech.utils.EncryptedPreferences.Key
import com.google.crypto.tink.Aead
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AesGcmKeyManager
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/** Functions to convert between preference types and byte arrays */
private val toString: (ByteArray) -> String = { it.toString(Charsets.UTF_8) }
private val fromString: (String) -> ByteArray = { it.toByteArray(Charsets.UTF_8) }
private val toInt: (ByteArray) -> Int = { ByteBuffer.wrap(it).getInt() }
private val fromInt: (Int) -> ByteArray = { ByteBuffer.allocate(Int.SIZE_BYTES).putInt(it).array() }
private val toBoolean: (ByteArray) -> Boolean = { ByteBuffer.wrap(it).get().toInt() != 0 }
private val fromBoolean: (Boolean) -> ByteArray = { ByteBuffer.allocate(Byte.SIZE_BYTES).put(if (it) 1 else 0).array() }
private val toByte: (ByteArray) -> Byte = { ByteBuffer.wrap(it).get() }
private val fromByte: (Byte) -> ByteArray = { ByteBuffer.allocate(Byte.SIZE_BYTES).put(it).array() }
private val toDouble: (ByteArray) -> Double = { ByteBuffer.wrap(it).getDouble() }
private val fromDouble: (Double) -> ByteArray = { ByteBuffer.allocate(Double.SIZE_BYTES).putDouble(it).array() }
private val toFloat: (ByteArray) -> Float = { ByteBuffer.wrap(it).getFloat() }
private val fromFloat: (Float) -> ByteArray = { ByteBuffer.allocate(Float.SIZE_BYTES).putFloat(it).array() }
private val toLong: (ByteArray) -> Long = { ByteBuffer.wrap(it).getLong() }
private val fromLong: (Long) -> ByteArray = { ByteBuffer.allocate(Long.SIZE_BYTES).putLong(it).array() }

/** Concrete class for various key types */
private class StringKey(name: String): Key<String>(name, fromString, toString)
private class IntKey(name: String): Key<Int>(name, fromInt, toInt)
private class BooleanKey(name: String): Key<Boolean>(name, fromBoolean, toBoolean)
private class ByteKey(name: String): Key<Byte>(name, fromByte, toByte)
private class DoubleKey(name: String): Key<Double>(name, fromDouble, toDouble)
private class FloatKey(name: String): Key<Float>(name, fromFloat, toFloat)
private class LongKey(name: String): Key<Long>(name, fromLong, toLong)

/**
 * Class for querying encrypted preferences
 */
abstract class EncryptedPreferences {
    /**
     * The class used to identify a preference
     * @see stringKey
     * @see intKey
     * @see booleanKey
     * @see byteKey
     * @see doubleKey
     * @see floatKey
     * @see longKey
     */
    sealed class Key<T> protected constructor(
        /** The name of the preference */
        val name: String,
        val fromT: (T) -> ByteArray,
        val toT: (ByteArray) -> T
    ) {
        /** The key used to retrieve the value from the Datastore Preference */
        val key: Preferences.Key<ByteArray> = byteArrayPreferencesKey(name)
    }

    /**
     * Get an encrypted value using a key
     * @param key The key for the value
     * @return The value or null if it isn't set
     */
    abstract suspend fun <T> get(key: Key<T>): T?

    /**
     * Edit values in the preference
     * @param block A code block that edits the values
     * @return The EncryptedPreferences
     */
    abstract suspend fun edit(block: suspend (MutableEncryptedPreferences) -> Unit): EncryptedPreferences

    companion object {
        /**
         * Create a delegate to get the EncryptedPreferences
         * @param name The name of the preferences
         * @param keysetName The name used to store the keysets in the keyset preference file
         * @param masterKeyUri A Uri used to identify the master key for the keyset.
         *                     The uri must start with "android-keystore://"
         * @param corruptionHandler The handler used to replace the file if it gets corrupted
         * @param produceMigrations A lambda function that returns a set of data migrations
         * @param scope A coroutine scope for running IO and transforms
         * These parameters are used to create the underlying data store
         * @see preferencesDataStore
         * All of the keysets are stored in a preference file name encrypted_preferences_key_sets
         */
        fun create(
            name: String,
            keysetName: String,
            masterKeyUri: String,
            corruptionHandler: ReplaceFileCorruptionHandler<Preferences>? = null,
            produceMigrations: (Context) -> List<DataMigration<Preferences>> = { listOf() },
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        ): ReadOnlyProperty<Context, EncryptedPreferences> {
            // Just in case
            synchronized(this) {
                AeadConfig.register()
            }

            // Create the delegate
            return EncryptedPreferencesDelegate(
                name,
                keysetName,
                masterKeyUri,
                corruptionHandler,
                produceMigrations,
                scope
            )
        }

        /**
         * Create a key for a string value
         * @param name The name of the value
         * @return The key for the value
         */
        fun stringKey(name: String): Key<String> = StringKey(name)
        /**
         * Create a key for an integer value
         * @param name The name of the value
         * @return The key for the value
         */
        fun intKey(name: String): Key<Int> = IntKey(name)
        /**
         * Create a key for a boolean value
         * @param name The name of the value
         * @return The key for the value
         */
        fun booleanKey(name: String): Key<Boolean> = BooleanKey(name)
        /**
         * Create a key for a byte value
         * @param name The name of the value
         * @return The key for the value
         */
        fun byteKey(name: String): Key<Byte> = ByteKey(name)
        /**
         * Create a key for a double value
         * @param name The name of the value
         * @return The key for the value
         */
        fun doubleKey(name: String): Key<Double> = DoubleKey(name)
        /**
         * Create a key for a float value
         * @param name The name of the value
         * @return The key for the value
         */
        fun floatKey(name: String): Key<Float> = FloatKey(name)
        /**
         * Create a key for a long value
         * @param name The name of the value
         * @return The key for the value
         */
        fun longKey(name: String): Key<Long> = LongKey(name)

        /**
         * Class for the EncryptedPreference delegate
         * @param name The name of the preferences
         * @param keysetName The name used to store the keysets in the keyset preference file
         * @param masterKeyUri A Uri used to identify the master key for the keyset.
         *                     The uri must start with "android-keystore://"
         * @param corruptionHandler The handler used to replace the file if it gets corrupted
         * @param produceMigrations A lambda function that returns a set of data migrations
         * @param scope A coroutine scope for running IO and transforms
         * These parameters are used to create the underlying data store
         * @see preferencesDataStore
         */
        private class EncryptedPreferencesDelegate(
            name: String,
            private val keysetName: String,
            private val masterKeyUri: String,
            corruptionHandler: ReplaceFileCorruptionHandler<Preferences>?,
            produceMigrations: (Context) -> List<DataMigration<Preferences>>,
            scope: CoroutineScope
        ): ReadOnlyProperty<Context, EncryptedPreferences> {
            /** Lock for multithread access */
            private val lock = Any()
            /** Delegate for the underlying DataStore<Preferences> */
            private val Context.persist by preferencesDataStore(name, corruptionHandler, produceMigrations, scope)
            /** The EncryptedPreferences instance */
            private var instance: EncryptedPreferences? = null

            /** @inheritDoc */
            override fun getValue(thisRef: Context, property: KProperty<*>): EncryptedPreferences {
                // Return the instance if it isn't null, otherwise lock and create the instance
                return instance?: synchronized(lock) {
                    // Make sure the instance is still null
                    return instance ?:
                    // Create the EncryptedPreference instance
                    EncryptedPreferencesImpl(
                        // Build or get the keyset for the preferences
                        AndroidKeysetManager.Builder()
                            // Preferences file to store the keyset
                            .withSharedPref(thisRef.applicationContext, keysetName, "encrypted_preferences_key_sets")
                            // Use 256 bit encryption
                            .withKeyTemplate(AesGcmKeyManager.aes256GcmTemplate())
                            // Uri for the master key
                            .withMasterKeyUri(masterKeyUri)
                            .build()
                            // Use Aead to encrypt/decrypt the values
                            .keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java),
                        // Get the DataStore for the preferences
                        thisRef.applicationContext.persist).also {
                        // Save instance
                        instance = it
                    }
                }
            }
        }
    }
}

/**
 * Class to update encrypted values
 */
abstract class MutableEncryptedPreferences: EncryptedPreferences() {
    /**
     * Update an encrypted value
     * @param key The key for the value
     * @param value The new value
     */
    abstract suspend fun <T> put(key: Key<T>, value: T?)

    /**
     * Remove an encrypted value
     * @param key The key for the value
     */
    abstract fun remove(key: Key<*>)
}

/**
 * Implementation for MutableEncryptedPreferences
 */
private class MutableEncryptedPreferencesImpl(
    /** The implementation for the EncryptedPreferences being updated */
    private val encryptedPreferencesImpl: EncryptedPreferencesImpl,
    /** The mutable preferences that hold the encrypted values */
    private val preferences: MutablePreferences
): MutableEncryptedPreferences() {
    /** @inheritDoc */
    override suspend fun <T> get(key: Key<T>): T? = encryptedPreferencesImpl.get(key, preferences)

    /** @inheritDoc */
    override suspend fun edit(block: suspend (MutableEncryptedPreferences) -> Unit): EncryptedPreferences {
        return encryptedPreferencesImpl.edit(block)
    }

    /** @inheritDoc */
    override suspend fun <T> put(key: Key<T>, value: T?) {
        encryptedPreferencesImpl.put(key, value, preferences)
    }

    /** @inheritDoc */
    override fun remove(key: Key<*>) {
        encryptedPreferencesImpl.remove(key, preferences)
    }
}

/**
 * The implementation of the EncryptedPreferences
 */
private class EncryptedPreferencesImpl(
    /** The encoder/decoder used to encrypt/decrypt the values */
    private val crypto: Aead,
    /** The underlying Datastore for the preferences */
    private val persist: DataStore<Preferences>
): EncryptedPreferences() {
    /** @inheritDoc */
    override suspend fun <T> get(key: Key<T>): T? {
        return get(key, persist.data.first())
    }

    /** @inheritDoc */
    override suspend fun edit(block: suspend (MutableEncryptedPreferences) -> Unit): EncryptedPreferences {
        persist.edit {
            block(MutableEncryptedPreferencesImpl(this, it))
        }
        return this
    }

    /**
     * Get a value from the preferences and decrypt it
     * @param key The key for the value
     * @param preferences The Preferences where the value is stored
     * @return The value or null if it hasn't been set
     */
    fun <T> get(key: Key<T>, preferences: Preferences): T? {
        // Get the value
        return preferences[key.key]?.let {
            try {
                // Decrypt it and convert to the correct type. We use the key name to
                // prevent encrypted values from being used with a different key
                key.toT(crypto.decrypt(it, key.name.toByteArray(Charsets.UTF_8)))
            } catch (_: GeneralSecurityException) {
                null
            }
        }
    }

    /**
     * Encrypt a value and put it in the preferences
     * @param key The key for the value
     * @param value The value to set. Null will remove the value from the preferences
     * @param preferences The mutable preferences where the value is set
     */
    fun <T> put(key: Key<T>, value: T?, preferences: MutablePreferences) {
        // If the value isn't null
        if (value != null) {
            // Convert the value to a byte array, encrypt it and set it
            preferences[key.key] = crypto.encrypt(key.fromT(value), key.name.toByteArray(Charsets.UTF_8))
        } else {
            // Null - remove the value
            preferences.remove(key.key)
        }
    }

    /**
     * Remove a value from the preference
     * @param key The key for the value to remove
     * @param preferences The mutable preferences the value is removed from
     */
    fun remove(key: Key<*>, preferences: MutablePreferences) {
        // Remove the value
        preferences.remove(key.key)
    }
}
