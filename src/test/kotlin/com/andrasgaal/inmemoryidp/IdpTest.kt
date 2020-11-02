package com.andrasgaal.inmemoryidp

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.http4k.client.ApacheClient
import org.http4k.core.Method
import org.http4k.core.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.opensaml.saml.common.xml.SAMLConstants.SAML20P_NS
import org.opensaml.saml.saml2.metadata.EntityDescriptor
import org.opensaml.security.credential.UsageType
import org.w3c.dom.Document

class IdpTest {

    val client = ApacheClient()
    var idp: InmemoryIdp? = null

    @AfterEach
    internal fun tearDown() {
        idp?.stop()
    }

    @Test
    internal fun `idp is listening on given port`() {
        val idpPort = 8000
        idp = InmemoryIdp.Builder().port(idpPort).build().start()

        val response = client(Request(Method.GET, "http://localhost:$idpPort"))

        assertTrue(response.status.successful)
    }

    @Test
    internal fun `idp starts up on 8080 by default`() {
        idp = InmemoryIdp.Builder().build().start()

        val response = client(Request(Method.GET, "http://localhost:8080"))

        assertTrue(response.status.successful)
    }

    @Test
    internal fun `get idp metadata before or after starting the server`() {
        val idpPort = 8000
        idp = InmemoryIdp.Builder().port(idpPort).build()

        val metadataBeforeStart = idp?.metadata
        idp?.start()
        val metadataAfterStart = idp?.metadata

        assertNotNull(metadataBeforeStart)
        assertNotNull(metadataAfterStart)
        assertEquals(metadataBeforeStart, metadataAfterStart)
    }

    @Test
    internal fun `idp metadata contains default entityID and signing certificate`() {
        val metadata = InmemoryIdp.Builder().build().metadata

        val parsedMetadata = parse(metadata)
        val signingCertificate = signingCertificateFrom(parsedMetadata)
        assertEquals(parsedMetadata.entityID, "http://in-memory-idp")
        assertNotPemFormat(signingCertificate)
    }

    @Test
    internal fun `idp metadata contains custom entityID and signing certificate`() {
        val entityID = "someEntityID"
        val signingCertificate = "someCertificate"

        val metadata = InmemoryIdp.Builder().entityID(entityID).signingCertificate(signingCertificate).build().metadata

        val parsedMetadata = parse(metadata)
        val signingCertificateFromMetadata = signingCertificateFrom(parsedMetadata)
        assertEquals(parsedMetadata.entityID, entityID)
        assertEquals(signingCertificateFromMetadata, signingCertificate)
    }

    private fun assertNotPemFormat(signingCertificate: String?) {
        assertThat(signingCertificate, not(containsString("BEGIN")))
        assertThat(signingCertificate, not(containsString("END")))
    }

    private fun signingCertificateFrom(parsedMetadata: EntityDescriptor): String? =
            parsedMetadata.getIDPSSODescriptor(SAML20P_NS).keyDescriptors.first { it.use == UsageType.SIGNING }
                    .keyInfo.x509Datas.first().x509Certificates.first().value!!

    private fun parse(metadata: String): EntityDescriptor {
        val mdDocument: Document = XmlHelper.registry.parserPool.parse(metadata.byteInputStream())
        val unmarshaller = XmlHelper.registry.unmarshallerFactory.getUnmarshaller(mdDocument.documentElement)!!
        return unmarshaller.unmarshall(mdDocument.documentElement) as EntityDescriptor
    }
}