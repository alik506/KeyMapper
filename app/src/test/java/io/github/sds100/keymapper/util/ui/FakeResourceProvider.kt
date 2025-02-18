package io.github.sds100.keymapper.util.ui

import android.graphics.drawable.Drawable

/**
 * Created by sds100 on 26/04/2021.
 */
class FakeResourceProvider : ResourceProvider {
    var stringResourceMap: Map<Int, String> = emptyMap()

    override fun getString(resId: Int, args: Array<Any>): String {
        return stringResourceMap[resId] ?: ""
    }

    override fun getString(resId: Int, arg: Any): String {
        return stringResourceMap[resId] ?: ""
    }

    override fun getString(resId: Int): String {
        return stringResourceMap[resId] ?: ""
    }

    override fun getText(resId: Int): CharSequence {
        return stringResourceMap[resId] ?: ""
    }

    override fun getDrawable(resId: Int): Drawable {
        throw Exception()
    }

    override fun getColor(color: Int): Int {
        throw Exception()
    }
}