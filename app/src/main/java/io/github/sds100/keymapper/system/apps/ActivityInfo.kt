package io.github.sds100.keymapper.system.apps

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import kotlinx.serialization.Serializable

/**
 * Created by sds100 on 15/03/2021.
 */

@Serializable
data class ActivityInfo(val activityName: String, val packageName: String)
