package com.andrasgaal.inmemoryidp

import net.shibboleth.utilities.java.support.xml.BasicParserPool
import net.shibboleth.utilities.java.support.xml.SerializeSupport
import org.opensaml.core.config.ConfigurationService
import org.opensaml.core.config.InitializationService
import org.opensaml.core.xml.XMLObject
import org.opensaml.core.xml.config.XMLObjectProviderRegistry
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport
import java.lang.RuntimeException
import javax.xml.namespace.QName

class XmlHelper {
    companion object {
        val registry: XMLObjectProviderRegistry get() = getRegistryFromConfigService() ?: {
            init()
            getRegistryFromConfigService() ?: throw RuntimeException()
        }()

        private fun init() {
            InitializationService.initialize()
            val parserPool = BasicParserPool().apply {
                maxPoolSize = 50
                initialize()
            }
            XMLObjectProviderRegistrySupport.setParserPool(parserPool)
        }

        private fun getRegistryFromConfigService(): XMLObjectProviderRegistry? = ConfigurationService.get(XMLObjectProviderRegistry::class.java)

        fun serialize(xml: XMLObject, qName: QName): String {
            return registry.marshallerFactory.getMarshaller(qName)
                    ?.marshall(xml)
                    ?.let { SerializeSupport.prettyPrintXML(it) }
                    ?: throw MetadataSerializationException("Marshaller to serialize $qName is missing.")
        }
    }
}