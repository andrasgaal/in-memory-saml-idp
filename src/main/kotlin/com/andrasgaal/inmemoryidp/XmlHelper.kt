package com.andrasgaal.inmemoryidp

import net.shibboleth.utilities.java.support.xml.BasicParserPool
import org.opensaml.core.config.ConfigurationService
import org.opensaml.core.config.InitializationService
import org.opensaml.core.xml.config.XMLObjectProviderRegistry
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport
import java.lang.RuntimeException

class XmlHelper {
    companion object {
        val registry: XMLObjectProviderRegistry get() = getRegistryFromConfigService() ?: {
            init()
            getRegistryFromConfigService() ?: throw RuntimeException()
        }()

        private fun getRegistryFromConfigService(): XMLObjectProviderRegistry? = ConfigurationService.get(XMLObjectProviderRegistry::class.java)

        private fun init() {
            InitializationService.initialize()
            val parserPool = BasicParserPool().apply {
                maxPoolSize = 50
                initialize()
            }
            XMLObjectProviderRegistrySupport.setParserPool(parserPool)
        }
    }
}