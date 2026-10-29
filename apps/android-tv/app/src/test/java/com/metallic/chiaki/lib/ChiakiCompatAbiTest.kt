// SPDX-License-Identifier: AGPL-3.0-only

package com.metallic.chiaki.lib

import org.junit.Assert.assertEquals
import org.junit.Test

class ChiakiCompatAbiTest {
    @Test
    fun nativeSessionCallbacksKeepVoidJvmSignatures() {
        val callbacks =
            mapOf(
                "eventConnected" to emptyArray<Class<*>>(),
                "eventLoginPinRequest" to arrayOf(Boolean::class.javaPrimitiveType!!),
                "eventQuit" to arrayOf(Int::class.javaPrimitiveType!!, String::class.java),
                "eventRumble" to arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!),
            )

        callbacks.forEach { (name, parameters) ->
            val method = Session::class.java.getDeclaredMethod(name, *parameters)
            assertEquals("$name must match the JNI ()V-style ABI", Void.TYPE, method.returnType)
        }
    }
}
