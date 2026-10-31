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
                "eventRegist" to arrayOf(RegistHost::class.java),
                "eventHolepunch" to emptyArray<Class<*>>(),
                "eventPsChord" to emptyArray<Class<*>>(),
            )

        callbacks.forEach { (name, parameters) ->
            val method = Session::class.java.getDeclaredMethod(name, *parameters)
            assertEquals("$name must match the JNI ()V-style ABI", Void.TYPE, method.returnType)
        }
    }

    @Test
    fun connectInfoExposesEveryFieldThePinnedJniReads() {
        // Mirrors the GetFieldID calls in Pylux ca87a49 android/app/src/main/cpp/chiaki-jni.c sessionCreate.
        val expected =
            mapOf(
                "ps5" to Boolean::class.javaPrimitiveType,
                "host" to String::class.java,
                "registKey" to ByteArray::class.java,
                "morning" to ByteArray::class.java,
                "videoProfile" to ConnectVideoProfile::class.java,
                "duid" to String::class.java,
                "holepunchSessionPtr" to Long::class.javaPrimitiveType,
                "psnAccountId" to String::class.java,
                "autoRegist" to Boolean::class.javaPrimitiveType,
                "serviceType" to String::class.java,
                "cloudLaunchSpec" to String::class.java,
                "cloudHandshakeKey" to String::class.java,
                "cloudSessionId" to String::class.java,
                "cloudPort" to Int::class.javaPrimitiveType,
                "cloudPsnWrapperType" to Int::class.javaPrimitiveType,
                "cloudMtuIn" to Int::class.javaPrimitiveType,
                "cloudMtuOut" to Int::class.javaPrimitiveType,
                "cloudRttUs" to Long::class.javaPrimitiveType,
            )

        expected.forEach { (name, type) ->
            assertEquals("ConnectInfo.$name JVM type", type, ConnectInfo::class.java.getDeclaredField(name).type)
        }
    }

    @Test
    fun cloudProvisionCallbacksKeepJniSignatures() {
        val progress = CloudProvisionCallbacks::class.java.getMethod("onProgress", String::class.java)
        val cancelled = CloudProvisionCallbacks::class.java.getMethod("isCancelled")
        assertEquals(Void.TYPE, progress.returnType)
        assertEquals(Boolean::class.javaPrimitiveType, cancelled.returnType)
    }
}
