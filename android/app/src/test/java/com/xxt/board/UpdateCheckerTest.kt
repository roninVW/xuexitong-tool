package com.xxt.board

import com.xxt.board.domain.UpdateChecker
import com.xxt.board.domain.UpdateException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun latestVersionIsComparedInTheUpdateDirection() {
        assertTrue(UpdateChecker.compareVersions("1.9.9", "v1.10.0") > 0)
        assertTrue(UpdateChecker.compareVersions("2.0.0", "1.99.99") < 0)
        assertEquals(0, UpdateChecker.compareVersions("v1.2.0", "1.2"))
        assertTrue(UpdateChecker.compareVersions("0.40.9", "0.40.10") > 0)
    }

    @Test
    fun semanticPrereleaseAndBuildRulesArePreserved() {
        assertTrue(UpdateChecker.compareVersions("1.0.0-alpha", "1.0.0") > 0)
        assertTrue(UpdateChecker.compareVersions("1.0.0", "1.0.0-rc.1") < 0)
        assertTrue(UpdateChecker.compareVersions("1.0.0-alpha.9", "1.0.0-alpha.10") > 0)
        assertTrue(UpdateChecker.compareVersions("1.0.0-alpha", "1.0.0-alpha.1") > 0)
        assertTrue(UpdateChecker.compareVersions("1.0.0-alpha.1", "1.0.0-alpha.beta") > 0)
        assertEquals(0, UpdateChecker.compareVersions("1.0.0+local", "v1.0.0+remote"))
    }

    @Test
    fun numericSegmentsDoNotOverflowOrUseStringOrdering() {
        assertTrue(
            UpdateChecker.compareVersions(
                "99999999999999999999.0.0",
                "100000000000000000000.0.0",
            ) > 0,
        )
        assertTrue(UpdateChecker.compareVersions("1.0.09", "1.0.10") > 0)
    }

    @Test(expected = UpdateException::class)
    fun malformedVersionIsNotSilentlyTreatedAsZero() {
        UpdateChecker.compareVersions("1.0.0", "1.not-a-number.0")
    }

    @Test
    fun assetChoiceUsesRealUrlAndDeviceAbi() {
        val universal = asset("app-release.apk", "https://assets.test/universal.apk")
        val arm64 = asset("app-arm64-v8a-release.apk", "https://assets.test/arm64.apk")
        val x86 = asset("app-x86-release.apk", "https://assets.test/x86.apk")
        val x64 = asset("app-x86_64-release.apk", "https://assets.test/x64.apk")
        val release = release(listOf(universal, arm64, x86, x64))

        assertEquals(
            arm64.downloadUrl,
            UpdateChecker.findApkAsset(release, listOf("arm64-v8a"))?.downloadUrl,
        )
        assertEquals(
            x64.downloadUrl,
            UpdateChecker.findApkAsset(release, listOf("x86_64", "x86"))?.downloadUrl,
        )
        assertEquals(
            universal.downloadUrl,
            UpdateChecker.findApkAsset(release, listOf("armeabi-v7a"))?.downloadUrl,
        )
    }

    @Test
    fun incompatibleOrMissingApkHasNoDownloadCandidate() {
        val armOnly = release(listOf(asset("app-arm64-v8a.apk", "https://assets.test/arm64.apk")))
        assertNull(UpdateChecker.findApkAsset(armOnly, listOf("x86_64")))
        assertNull(UpdateChecker.findApkAsset(release(emptyList()), listOf("x86_64")))
        assertNull(
            UpdateChecker.findApkAsset(
                release(listOf(asset("app-unsigned.apk", "https://assets.test/unsigned.apk"))),
                listOf("x86_64"),
            ),
        )
    }

    private fun asset(name: String, url: String) = UpdateChecker.Asset(
        name = name,
        downloadUrl = url,
        sizeBytes = 1024L,
    )

    private fun release(assets: List<UpdateChecker.Asset>) = UpdateChecker.Release(
        tagName = "v1.1.0",
        title = "Test release",
        changelog = "Changes",
        publishedAt = "2026-01-02T03:04:05Z",
        htmlUrl = "https://github.com/example/release",
        assets = assets,
    )
}
