package com.andrasgaal.inmemoryidp

import net.shibboleth.utilities.java.support.xml.SerializeSupport.prettyPrintXML
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.http4k.client.ApacheClient
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.body.form
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.opensaml.saml.common.xml.SAMLConstants.SAML2_POST_BINDING_URI
import org.opensaml.saml.saml2.core.AuthnRequest
import org.opensaml.saml.saml2.core.impl.AuthnRequestBuilder

class SsoServiceTest {

    private val client = ApacheClient()
    private var idp: InmemoryIdp? = null

    @AfterEach
    internal fun tearDown() {
        idp?.stop()
    }

    @Test
    internal fun `respond with SAML Response when valid SAML Request was sent to SSO Service URL`() {
        idp = InmemoryIdp.Builder().samlResponse("someResponse").build().start()

        val acsUrl = "someUrl"
        val response = client(Request(POST, "http://localhost:8080/sso")
                .form("SAMLRequest", createSamlRequest(acsUrl)))

        assertThat(response.status, equalTo(Status.OK))
        assertThat(response.bodyString(), containsString("""<form method="post" action="$acsUrl">"""))
        assertThat(response.bodyString(), containsString("""<input type="hidden" name="SAMLResponse" """))
    }

    @Test
    internal fun `show error page when not valid SAML Request was sent`() {
        idp = InmemoryIdp.Builder().build().start()

        val response = client(Request(POST, "http://localhost:8080/sso")
                .form("SAMLRequest", "invalid request"))

        assertThat(response.status, equalTo(Status.BAD_REQUEST))
    }

    private fun createSamlRequest(acsUrl: String): String {
        val samlRequest = AuthnRequestBuilder().buildObject().apply {
            assertionConsumerServiceURL = acsUrl
            protocolBinding = SAML2_POST_BINDING_URI
        }
        return XmlHelper.registry.marshallerFactory.getMarshaller(AuthnRequest.DEFAULT_ELEMENT_NAME)
                ?.marshall(samlRequest)
                ?.let { prettyPrintXML(it) }!!
    }
}