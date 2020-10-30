package com.andrasgaal.inmemoryidp

import net.shibboleth.utilities.java.support.xml.SerializeSupport
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.server.Http4kServer
import org.http4k.server.Netty
import org.http4k.server.asServer
import org.opensaml.core.xml.XMLObject
import org.opensaml.saml.saml2.metadata.EntityDescriptor

class InmemoryIdp(private val customEntityID: String = "http://in-memory-idp") {

    private var port: Int? = null
    private val app = { request: Request -> Response(OK).body("Hello") }
    private var server: Http4kServer? = null

    val metadata: String get() = constructMetadata()

    private fun constructMetadata(): String {
        return XmlHelper.registry.builderFactory.getBuilder(EntityDescriptor.ELEMENT_QNAME)
                ?.let { it.buildObject(EntityDescriptor.ELEMENT_QNAME) as? EntityDescriptor }
                ?.apply { entityID = customEntityID }
                ?.let { serialize(it) }
                ?: throw MetadataSerializationException("Builder to construct EntityDescriptor is missing.")
    }

    private fun serialize(xml: XMLObject): String {
        return XmlHelper.registry.marshallerFactory.getMarshaller(EntityDescriptor.ELEMENT_QNAME)
                ?.marshall(xml)
                ?.let { SerializeSupport.prettyPrintXML(it) }
                ?: throw MetadataSerializationException("Marshaller to serialize EntityDescriptor is missing.")
    }

    fun port(port: Int): InmemoryIdp {
        this.port = port
        return this
    }

    fun start(): InmemoryIdp {
        server = app.asServer(Netty(port ?: 8080)).start()
        return this
    }

    fun stop() {
        server?.stop()
    }
}
