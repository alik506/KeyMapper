package io.github.sds100.keymapper.system.accessibility

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import io.github.sds100.keymapper.Constants
import io.github.sds100.keymapper.MainActivity
import io.github.sds100.keymapper.ServiceLocator
import io.github.sds100.keymapper.system.JobSchedulerHelper
import io.github.sds100.keymapper.system.SettingsUtils
import io.github.sds100.keymapper.system.permissions.Permission
import io.github.sds100.keymapper.system.permissions.PermissionAdapter
import io.github.sds100.keymapper.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Created by sds100 on 17/03/2021.
 */
class AccessibilityServiceAdapter(
    context: Context,
    private val coroutineScope: CoroutineScope
) : ServiceAdapter {

    private val ctx = context.applicationContext
    override val eventReceiver = MutableSharedFlow<Event>()

    val serviceOutputEvents = MutableSharedFlow<Event>()

    override val state = MutableStateFlow(ServiceState.DISABLED)

    private val permissionAdapter: PermissionAdapter by lazy { ServiceLocator.permissionAdapter(ctx) }

    init {
        //use job scheduler because there is there is a much shorter delay when the app is in the background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            JobSchedulerHelper.observeEnabledAccessibilityServices(ctx)
        } else {
            val uri = Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)

                    coroutineScope.launch {
                        state.value = getState()
                    }
                }
            }

            ctx.contentResolver.registerContentObserver(uri, false, observer)
        }

        coroutineScope.launch {
            state.value = getState()
        }

        eventReceiver.onEach {
            Timber.d("Received event from service: $it")
        }.launchIn(coroutineScope)
    }

    override suspend fun send(event: Event): Result<*> {

        state.value = getState()

        if (state.value == ServiceState.DISABLED) {
            Timber.e("Failed to send event to accessibility service because disabled: $event")
            return Error.AccessibilityServiceDisabled
        }

        if (state.value == ServiceState.CRASHED) {
            Timber.e("Failed to send event to accessibility service because crashed: $event")
            return Error.AccessibilityServiceCrashed
        }

        coroutineScope.launch {
            serviceOutputEvents.emit(event)
        }

        return Success(Unit)
    }

    override fun enableService() {
        if (permissionAdapter.isGranted(Permission.WRITE_SECURE_SETTINGS)) {

            enableWithWriteSecureSettings()

            /*
            Turning on the accessibility service doesn't necessarily mean that it is running so
            this will check if it is indeed running and then turn it off and on so that it
            is running again.
             */
            coroutineScope.launch {
                delay(200)

                val key = "check_is_crashed_then_restart"

                //wait to start collecting
                coroutineScope.launch {
                    delay(100)

                    Timber.d("Ping service to check if crashed")
                    serviceOutputEvents.emit(Ping(key))
                }

                val pong: Pong? = withTimeoutOrNull(2000L) {
                    eventReceiver.first { it == Pong(key) } as Pong?
                }

                if (pong == null) {
                    Timber.e("Service crashed so restarting")
                    disableService()
                    delay(200)
                    enableWithWriteSecureSettings()
                }
            }
        } else {
            Timber.i("Enable service by opening accessibility settings")

            openAccessibilitySettings()
        }
    }

    override fun restartService() {
        if (permissionAdapter.isGranted(Permission.WRITE_SECURE_SETTINGS)) {
            Timber.i("Restarting service with WRITE_SECURE_SETTINGS")

            coroutineScope.launch {
                disableService()
                delay(200)
                enableWithWriteSecureSettings()
            }
        } else {
            Timber.i("Restarting service by opening accessibility settings")

            openAccessibilitySettings()
        }
    }

    override fun disableService() {
        if (permissionAdapter.isGranted(Permission.WRITE_SECURE_SETTINGS)) {
            Timber.i("Disabling service with WRITE_SECURE_SETTINGS")

            val enabledServices = SettingsUtils.getSecureSetting<String>(
                ctx,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )

            enabledServices ?: return

            val className = MyAccessibilityService::class.java.name

            val keyMapperEntry = "${Constants.PACKAGE_NAME}/$className"

            val newEnabledServices = if (enabledServices.contains(keyMapperEntry)) {
                val services = enabledServices.split(':').toMutableList()
                services.remove(keyMapperEntry)

                services.joinToString(":")
            } else {
                enabledServices
            }

            SettingsUtils.putSecureSetting(
                ctx,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                newEnabledServices
            )
        } else {
            Timber.i("Disabling service by opening accessibility settings")

            openAccessibilitySettings()
        }
    }

    override suspend fun isCrashed(): Boolean {
        Timber.i("Accessibility service: checking if it is crashed")
        val key = "check_is_crashed"

        coroutineScope.launch {
            delay(100)
            serviceOutputEvents.emit(Ping(key))
        }

        val pong: Pong? = withTimeoutOrNull(2000L) {
            eventReceiver.first { it == Pong(key) } as Pong?
        }

        if (pong == null) {
            Timber.e("Accessibility service: is crashed")
        }

        return pong == null
    }

    fun updateWhetherServiceIsEnabled() {
        coroutineScope.launch {
            state.value = getState()
        }
    }

    private fun enableWithWriteSecureSettings() {
        if (permissionAdapter.isGranted(Permission.WRITE_SECURE_SETTINGS)) {
            Timber.i("Enable service with WRITE_SECURE_SETTINGS")

            val enabledServices = SettingsUtils.getSecureSetting<String>(
                ctx,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )

            val className = MyAccessibilityService::class.java.name

            val keyMapperEntry = "${Constants.PACKAGE_NAME}/$className"

            val newEnabledServices = when {
                enabledServices == null -> keyMapperEntry
                enabledServices.contains(keyMapperEntry) -> enabledServices
                else -> "$keyMapperEntry:$enabledServices"
            }

            SettingsUtils.putSecureSetting(
                ctx,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, newEnabledServices
            )
        }
    }

    private fun openAccessibilitySettings() {
        try {
            val settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

            settingsIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )

            ctx.startActivity(settingsIntent)

        } catch (e: ActivityNotFoundException) {
            //open the app to show a dialog to tell the user to give the app WRITE_SECURE_SETTINGS permission
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(MainActivity.KEY_SHOW_ACCESSIBILITY_SETTINGS_NOT_FOUND_DIALOG, true)

                ctx.startActivity(this)
            }
        }
    }

    private suspend fun getState(): ServiceState {
        /* get a list of all the enabled accessibility services.
         * The AccessibilityManager.getEnabledAccessibilityServices() method just returns an empty
         * list. :(*/
        val settingValue = Settings.Secure.getString(
            ctx.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )

        if (settingValue == null) {
            return ServiceState.DISABLED
        }

        //it can be null if the user has never interacted with accessibility settings before
        /* cant just use .contains because the debug and release accessibility service both contain
           io.github.sds100.keymapper. the enabled_accessibility_services are stored as

             io.github.sds100.keymapper.debug/io.github.sds100.keymapper.service.MyAccessibilityService
             :io.github.sds100.keymapper/io.github.sds100.keymapper.service.MyAccessibilityService

             without the new line before the :
        */
        val isEnabled = settingValue.split(':').any { it.split('/')[0] == ctx.packageName }

        return when {
            !isEnabled -> ServiceState.DISABLED
            isCrashed() && isEnabled -> ServiceState.CRASHED
            else -> ServiceState.ENABLED
        }
    }
}