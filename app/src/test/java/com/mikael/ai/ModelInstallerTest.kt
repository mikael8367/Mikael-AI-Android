package com.mikael.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelInstallerTest {
    @Test
    fun allowsOnlyHttpsHuggingFaceAndXetRedirectHosts() {
        assertTrue(ModelInstaller.isAllowedRedirectHost("huggingface.co"))
        assertTrue(ModelInstaller.isAllowedRedirectHost("cdn-lfs.huggingface.co"))
        assertTrue(ModelInstaller.isAllowedRedirectHost("cas-bridge.xethub.hf.co"))
    }

    @Test
    fun rejectsLookalikeAndUnrelatedRedirectHosts() {
        assertFalse(ModelInstaller.isAllowedRedirectHost("huggingface.co.attacker.example"))
        assertFalse(ModelInstaller.isAllowedRedirectHost("evil-huggingface.co"))
        assertFalse(ModelInstaller.isAllowedRedirectHost("example.com"))
    }
}
