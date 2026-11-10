package com.mikael.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    @Test
    fun thirdPartyCatalogIsDisabledForOriginalEngine() {
        assertTrue(ModelCatalog.models.isEmpty())
    }
}
