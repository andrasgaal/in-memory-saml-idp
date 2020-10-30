package com.andrasgaal.inmemoryidp

import org.http4k.client.ApacheClient
import org.http4k.core.Method
import org.http4k.core.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.opensaml.saml.saml2.metadata.EntityDescriptor
import org.w3c.dom.Document

class ApplicationTest {

    val client = ApacheClient()
    var idp: InmemoryIdp? = null

    @AfterEach
    internal fun tearDown() {
        idp?.stop()
    }

    @Test
    internal fun `idp is listening on given port`() {
        val idpPort = 8000
        idp = InmemoryIdp().port(idpPort).start()

        val response = client(Request(Method.GET, "http://localhost:$idpPort"))

        assertTrue(response.status.successful)
    }

    @Test
    internal fun `idp starts up on 8080 by default`() {
        idp = InmemoryIdp().start()

        val response = client(Request(Method.GET, "http://localhost:8080"))

        assertTrue(response.status.successful)
    }

    @Test
    internal fun `get idp metadata before or after starting the server`() {
        val idpPort = 8000
        idp = InmemoryIdp().port(idpPort)

        val metadataBeforeStart = idp?.metadata
        idp?.start()
        val metadataAfterStart = idp?.metadata

        assertNotNull(metadataBeforeStart)
        assertNotNull(metadataAfterStart)
        assertEquals(metadataBeforeStart, metadataAfterStart)
    }

    @Test
    internal fun `idp metadata contains custom entityID`() {
        val entityID = "someEntityID"
        val metadata = InmemoryIdp(entityID).metadata

        val mdDocument: Document = XmlHelper.registry.parserPool.parse(metadata.byteInputStream())
        val unmarshaller = XmlHelper.registry.unmarshallerFactory.getUnmarshaller(mdDocument.documentElement)!!
        val parsedMetadata = unmarshaller.unmarshall(mdDocument.documentElement) as EntityDescriptor
        assertEquals(parsedMetadata.entityID, entityID)
    }
}