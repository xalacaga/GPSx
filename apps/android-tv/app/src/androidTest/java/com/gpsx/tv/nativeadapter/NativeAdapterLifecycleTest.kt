// SPDX-License-Identifier: AGPL-3.0-only

package com.gpsx.tv.nativeadapter

import android.graphics.SurfaceTexture
import android.os.Build
import android.view.Surface
import android.view.SurfaceView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gpsx.tv.BuildConfig
import com.gpsx.tv.MainActivity
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicIntegerArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeAdapterLifecycleTest {
    @Before
    fun requireEmulator() {
        val emulator =
            Build.FINGERPRINT.startsWith("generic") ||
                Build.MODEL.contains("Emulator", ignoreCase = true) ||
                Build.PRODUCT.contains("sdk", ignoreCase = true)
        assertTrue("native lifecycle tests are emulator-only: ${Build.FINGERPRINT}", emulator)
    }

    @Test
    fun createDestroyStressReleasesEveryHandleAndWorker() {
        val probe = NativeEngine()
        probe.close()

        repeat(1_000) {
            NativeEngine().close()
        }

        awaitReleased(probe)
    }

    @Test
    fun surfaceGenerationsRejectStaleCallbacksAndCloseIdempotently() {
        val texture = SurfaceTexture(0)
        val surface = Surface(texture)
        val engine = NativeEngine()
        try {
            assertEquals(NativeStatus.OK, engine.attachSurface(surface, 1))
            assertEquals(NativeStatus.STALE_GENERATION, engine.attachSurface(surface, 1))
            assertEquals(NativeStatus.STALE_GENERATION, engine.clearSurface(0))
            assertEquals(NativeStatus.OK, engine.clearSurface(1))
            assertEquals(NativeStatus.OK, engine.attachSurface(surface, 2))
        } finally {
            engine.close()
            engine.close()
            surface.release()
            texture.release()
        }
        assertEquals(NativeStatus.CLOSED, engine.clearSurface(2))
        awaitReleased(engine)
    }

    @Test
    fun audioStartsAndStopsWithoutLeakingTheNativeEngine() {
        val engine = NativeEngine()
        try {
            assertEquals(NativeStatus.OK, engine.startAudio())
            assertEquals(NativeStatus.OK, engine.stopAudio())
        } finally {
            engine.close()
        }
        awaitReleased(engine)
    }

    @Test
    fun audioDisconnectIsReportedAndStartStopDestroyIsRepeatable() {
        val engine = NativeEngine()
        try {
            repeat(1_000) {
                assertEquals(NativeStatus.OK, engine.startAudio())
                assertEquals(NativeStatus.OK, engine.stopAudio())
            }
            engine.forceAudioDisconnectForTest()
            assertEquals(NativeAudioHealth.DISCONNECTED, engine.audioHealth())
        } finally {
            engine.close()
        }
        awaitReleased(engine)
    }

    @Test
    fun callbackDuringStopAndCloseIsLockFreeAcrossOneThousandCycles() {
        val engine = NativeEngine()
        val pool = Executors.newSingleThreadExecutor()
        try {
            engine.installCallbackDuringStopForTest()
            val cycles =
                pool.submit {
                    repeat(1_000) {
                        assertEquals(NativeStatus.OK, engine.startAudio())
                        assertEquals(NativeStatus.OK, engine.stopAudio())
                    }
                }
            cycles.get(30, TimeUnit.SECONDS)
            assertEquals(NativeAudioHealth.DISCONNECTED, engine.audioHealth())
        } finally {
            engine.close()
            pool.shutdownNow()
        }
        awaitReleased(engine)
    }

    @Test
    fun asanRuntimeIsMappedWhenAsanProfileIsRequested() {
        if (!BuildConfig.GPSX_ASAN) return

        val maps = File("/proc/self/maps").readText()
        assertTrue("ASan profile ran without a mapped ASan runtime", maps.contains("libclang_rt.asan"))
    }

    @Test
    fun codecFailureReleasesOldAndReplacementWindowsAndRecordsGeneration() {
        val texture = SurfaceTexture(0)
        val surface = Surface(texture)
        val engine = NativeEngine()
        try {
            assertEquals(NativeStatus.OK, engine.attachSurface(surface, 1))
            assertEquals(1, engine.resourceCountsForTest().windows)
            engine.forceCodecFailureForTest(true)
            assertEquals(NativeStatus.CODEC_ERROR, engine.attachSurface(surface, 2))
            assertEquals(0, engine.resourceCountsForTest().windows)
            assertEquals(NativeStatus.OK, engine.clearSurface(2))
        } finally {
            engine.close()
            surface.release()
            texture.release()
        }
        awaitReleased(engine)
    }

    @Test
    fun liveCodecReplacesOutputSurfaceWithoutAnIntermediateClear() {
        val firstTexture = SurfaceTexture(0)
        val secondTexture = SurfaceTexture(0)
        val first = Surface(firstTexture)
        val second = Surface(secondTexture)
        val engine = NativeEngine()
        try {
            assertEquals(NativeStatus.OK, engine.attachSurface(first, 1))
            assertEquals(NativeStatus.OK, engine.attachSurface(second, 2))
            assertEquals(NativeStatus.OK, engine.clearSurface(2))
        } finally {
            engine.close()
            first.release()
            second.release()
            firstTexture.release()
            secondTexture.release()
        }
        awaitReleased(engine)
    }

    @Test
    fun concurrentCallsRacingCloseReturnOnlyClassifiedStatuses() {
        runConcurrentCallsRacingClose(lifecycleTestMode = true, workerCount = 4, rounds = 250)
    }

    @Test
    fun realCodecAndAudioCallsRacingCloseReturnOnlyClassifiedStatuses() {
        runConcurrentCallsRacingClose(lifecycleTestMode = false, workerCount = 2, rounds = 25)
    }

    private fun runConcurrentCallsRacingClose(
        lifecycleTestMode: Boolean,
        workerCount: Int,
        rounds: Int,
    ) {
        val texture = SurfaceTexture(0)
        val surface = Surface(texture)
        val engine = NativeEngine()
        engine.setLifecycleTestModeForTest(lifecycleTestMode)
        val start = CountDownLatch(1)
        val firstRoundComplete = CountDownLatch(workerCount)
        val continueRace = CountDownLatch(1)
        val okBeforeCloseByWorker = AtomicIntegerArray(workerCount)
        val statuses = ConcurrentLinkedQueue<NativeStatus>()
        val pool = Executors.newFixedThreadPool(workerCount + 1)
        try {
            val workers =
                List(workerCount) { workerIndex ->
                    pool.submit {
                        start.await()
                        val firstStatuses = runConcurrentRound(engine, surface)
                        statuses.addAll(firstStatuses)
                        okBeforeCloseByWorker.set(workerIndex, firstStatuses.count { it == NativeStatus.OK })
                        firstRoundComplete.countDown()
                        continueRace.await()
                        repeat(rounds - 1) {
                            statuses.addAll(runConcurrentRound(engine, surface))
                        }
                    }
                }
            val closer =
                pool.submit {
                    start.await()
                    assertTrue(firstRoundComplete.await(30, TimeUnit.SECONDS))
                    continueRace.countDown()
                    Thread.yield()
                    engine.close()
                }
            start.countDown()
            workers.forEach { it.get(30, TimeUnit.SECONDS) }
            closer.get(30, TimeUnit.SECONDS)
        } finally {
            engine.close()
            pool.shutdownNow()
            surface.release()
            texture.release()
        }
        val allowed = setOf(NativeStatus.OK, NativeStatus.CLOSED, NativeStatus.STALE_GENERATION)
        assertTrue("unexpected concurrent status: $statuses", statuses.all { it in allowed })
        repeat(workerCount) { workerIndex ->
            assertTrue(
                "worker $workerIndex did not observe OK before close",
                okBeforeCloseByWorker.get(workerIndex) > 0,
            )
        }
        awaitReleased(engine)
    }

    private fun runConcurrentRound(engine: NativeEngine, surface: Surface): List<NativeStatus> {
        val generation = engine.nextSurfaceGeneration()
        return listOf(
            engine.attachSurface(surface, generation),
            engine.clearSurface(generation),
            engine.startAudio(),
            engine.stopAudio(),
        )
    }

    @Test
    fun surfaceViewControllerCanBeDestroyedAndRecreated() {
        val engine = NativeEngine()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var controller: StreamSurfaceController? = null
            try {
                val firstAttached = CountDownLatch(1)
                scenario.onActivity { activity ->
                    val view = SurfaceView(activity)
                    controller =
                        StreamSurfaceController(view, engine) { status ->
                            if (status == NativeStatus.OK) firstAttached.countDown()
                        }
                    activity.setContentView(view)
                }
                assertTrue("first SurfaceView did not attach", firstAttached.await(5, TimeUnit.SECONDS))

                val secondAttached = CountDownLatch(1)
                scenario.onActivity { activity ->
                    controller?.close()
                    val replacement = SurfaceView(activity)
                    controller =
                        StreamSurfaceController(replacement, engine) { status ->
                            if (status == NativeStatus.OK) secondAttached.countDown()
                        }
                    activity.setContentView(replacement)
                }
                assertTrue("replacement SurfaceView did not attach", secondAttached.await(5, TimeUnit.SECONDS))
            } finally {
                scenario.onActivity { controller?.close() }
            }
        }
        engine.close()
        awaitReleased(engine)
    }

    private fun awaitReleased(probe: NativeEngine) {
        repeat(100) {
            val counts = probe.resourceCountsForTest()
            if (counts.engines == 0 && counts.workers == 0 && counts.windows == 0) {
                return
            }
            Thread.sleep(10)
        }
        val counts = probe.resourceCountsForTest()
        assertTrue("native engines leaked: $counts", counts.engines == 0)
        assertTrue("native workers leaked: $counts", counts.workers == 0)
        assertTrue("native windows leaked: $counts", counts.windows == 0)
    }
}
