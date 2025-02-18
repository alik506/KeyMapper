package io.github.sds100.keymapper.system.inputmethod

import io.github.sds100.keymapper.Constants
import io.github.sds100.keymapper.util.*

/**
 * Created by sds100 on 16/03/2021.
 */

class KeyMapperImeHelper(private val imeAdapter: InputMethodAdapter) {
    companion object {
        const val KEY_MAPPER_GUI_IME_PACKAGE =
            "io.github.sds100.keymapper.inputmethod.latin"

        const val KEY_MAPPER_LEANBACK_IME_PACKAGE =
            "io.github.sds100.keymapper.inputmethod.leanback"

        val KEY_MAPPER_IME_PACKAGE_LIST = arrayOf(
            Constants.PACKAGE_NAME,
            KEY_MAPPER_GUI_IME_PACKAGE,
            KEY_MAPPER_LEANBACK_IME_PACKAGE
        )
    }

    fun enableCompatibleInputMethods() {
        KEY_MAPPER_IME_PACKAGE_LIST.forEach { packageName ->
            imeAdapter.getInfoByPackageName(packageName).onSuccess {
                imeAdapter.enableIme(it.id)
            }
        }
    }

    suspend fun chooseCompatibleInputMethod(): Result<ImeInfo> {
        return getLastUsedCompatibleImeId().suspendThen {
            imeAdapter.chooseImeWithoutUserInput(it)
        }
    }

    suspend fun chooseLastUsedIncompatibleInputMethod(): Result<ImeInfo> {
        return getLastUsedIncompatibleImeId().then {
            imeAdapter.chooseImeWithoutUserInput(it)
        }
    }

    suspend fun toggleCompatibleInputMethod(): Result<ImeInfo> {
        return if (isCompatibleImeChosen()) {
            chooseLastUsedIncompatibleInputMethod()
        } else {
            chooseCompatibleInputMethod()
        }
    }

    fun isCompatibleImeChosen(): Boolean {
        return imeAdapter.chosenIme.value?.packageName in KEY_MAPPER_IME_PACKAGE_LIST
    }

    fun isCompatibleImeEnabled(): Boolean {
        return imeAdapter.inputMethods
            .firstBlocking()
            .filter { it.isEnabled }
            .any { it.packageName in KEY_MAPPER_IME_PACKAGE_LIST }
    }

    private fun getLastUsedCompatibleImeId(): Result<String> {
        for (ime in imeAdapter.inputMethodHistory.firstBlocking()) {
            if (ime.packageName in KEY_MAPPER_IME_PACKAGE_LIST && ime.isEnabled) {
                return Success(ime.id)
            }
        }

        imeAdapter.getInfoByPackageName(KEY_MAPPER_GUI_IME_PACKAGE).onSuccess { ime ->
            if (ime.isEnabled) {
                return Success(ime.id)
            }
        }

        return imeAdapter.getInfoByPackageName(Constants.PACKAGE_NAME).then { ime ->
            if (ime.isEnabled) {
                Success(ime.id)
            } else {
                Error.NoCompatibleImeEnabled
            }
        }
    }

    private fun getLastUsedIncompatibleImeId(): Result<String> {
        for (ime in imeAdapter.inputMethodHistory.firstBlocking()) {
            if (ime.packageName !in KEY_MAPPER_IME_PACKAGE_LIST) {
                return Success(ime.id)
            }
        }

        return Error.NoIncompatibleKeyboardsInstalled
    }
}