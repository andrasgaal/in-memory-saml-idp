package com.andrasgaal.inmemoryidp

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
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
import org.opensaml.saml.common.xml.SAMLConstants.SAML2_POST_BINDING_URI
import org.opensaml.saml.saml2.metadata.EntityDescriptor
import org.opensaml.security.credential.UsageType
import org.w3c.dom.Document

class IdpTest {

    val client = ApacheClient()
    var idp: InMemoryIdp? = null

    @AfterEach
    internal fun tearDown() {
        idp?.stop()
    }

    @Test
    internal fun `idp is listening on given port`() {
        val idpPort = 8000
        idp = InMemoryIdp.Builder().port(idpPort).build().start()

        val response = client(Request(Method.GET, "http://localhost:$idpPort"))

        assertTrue(response.status.successful)
    }

    @Test
    internal fun `idp starts up on 8080 by default`() {
        idp = InMemoryIdp.Builder().build().start()

        val response = client(Request(Method.GET, "http://localhost:8080"))

        assertTrue(response.status.successful)
    }

    @Test
    internal fun `get idp metadata before or after starting the server`() {
        val idpPort = 8000
        idp = InMemoryIdp.Builder().port(idpPort).build()

        val metadataBeforeStart = idp?.metadata
        idp?.start()
        val metadataAfterStart = idp?.metadata

        assertNotNull(metadataBeforeStart)
        assertNotNull(metadataAfterStart)
        assertEquals(metadataBeforeStart, metadataAfterStart)
    }

    @Test
    internal fun `idp metadata contains default settings`() {
        val metadata = InMemoryIdp.Builder().build().metadata

        val entityDescriptor = parseMetadata(metadata)
        val signingCertificate = signingCertificateFrom(entityDescriptor)
        assertSsoService(entityDescriptor, 8080)
        assertEquals(entityDescriptor.entityID, "http://in-memory-idp")
        assertNotPemFormat(signingCertificate)
    }

    @Test
    internal fun `idp metadata contains custom settings`() {
        val entityId = "someEntityID"
        val signingCertificate = "someCertificate"
        val port = 8000

        val metadata = InMemoryIdp.Builder()
            .entityId(entityId)
            .port(port)
            .signingCertificate(signingCertificate)
            .build().metadata

        val entityDescriptor = parseMetadata(metadata)
        val signingCertificateFromMetadata = signingCertificateFrom(entityDescriptor)
        assertSsoService(entityDescriptor, port)
        assertEquals(entityDescriptor.entityID, entityId)
        assertEquals(signingCertificateFromMetadata, signingCertificate)
    }

    private fun assertSsoService(entityDescriptor: EntityDescriptor, port: Int) {
        entityDescriptor.getIDPSSODescriptor(SAML20P_NS).singleSignOnServices.first()?.let {
            assertThat(it.binding, equalTo(SAML2_POST_BINDING_URI))
            assertThat(it.location, equalTo("http://localhost:$port/sso"))
        }
    }

    private fun assertNotPemFormat(signingCertificate: String?) {
        assertThat(signingCertificate, not(containsString("BEGIN")))
        assertThat(signingCertificate, not(containsString("END")))
    }

    private fun signingCertificateFrom(entityDescriptor: EntityDescriptor): String? =
            entityDescriptor.getIDPSSODescriptor(SAML20P_NS).keyDescriptors.first { it.use == UsageType.SIGNING }
                    .keyInfo.x509Datas.first().x509Certificates.first().value!!

    private fun parseMetadata(metadata: String): EntityDescriptor {
        val mdDocument: Document = XmlHelper.registry.parserPool.parse(metadata.byteInputStream())
        val unmarshaller = XmlHelper.registry.unmarshallerFactory.getUnmarshaller(mdDocument.documentElement)!!
        return unmarshaller.unmarshall(mdDocument.documentElement) as EntityDescriptor
    }
}