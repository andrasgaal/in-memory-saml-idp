package com.andrasgaal.inmemoryidp

import com.andrasgaal.inmemoryidp.XmlHelper.Companion.serialize
import net.shibboleth.utilities.java.support.xml.SerializeSupport
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.http4k.client.ApacheClient
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.form
import org.jsoup.Jsoup
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.opensaml.saml.common.xml.SAMLConstants
import org.opensaml.saml.saml2.core.AuthnRequest
import org.opensaml.saml.saml2.core.StatusCode
import org.opensaml.saml.saml2.core.impl.AuthnRequestBuilder
import org.w3c.dom.Document
import java.util.*

class SsoServiceTest {

    private val client = ApacheClient()
    private var idp: InMemoryIdp? = null

    @AfterEach
    internal fun tearDown() {
        idp?.stop()
    }

    @Test
    internal fun `respond with SAML Response when valid SAML Request was sent to SSO Service URL`() {
        val samlResponse = "someResponse"
        val acsUrl = "someUrl"
        idp = InMemoryIdp.Builder().samlResponseXml(samlResponse).build().start()

        val response = postSamlRequest(createSamlRequest(acsUrl))

        assertThat(response.status, equalTo(Status.OK))
        val document = Jsoup.parse(response.bodyString())
        val form = document.select("form").first()
        assertThat(form.attr("method"), equalTo("post"))
        assertThat(form.attr("action"), equalTo(acsUrl))

        val samlResponseInput = form.select("input")
        assertThat(samlResponseInput.attr("type"), equalTo("hidden"))
        assertThat(samlResponseInput.attr("name"), equalTo("SAMLResponse"))
        assertThat(String(Base64.getDecoder().decode(samlResponseInput.`val`())), equalTo(samlResponse))
    }

    @Test
    internal fun `generate default SAML Response when not passed to builder`() {
        idp = InMemoryIdp.Builder().build().start()
        val acsUrl = "someUrl"

        val response = postSamlRequest(createSamlRequest(acsUrl))

        assertThat(response.status, equalTo(Status.OK))
        val document = Jsoup.parse(response.bodyString())
        val form = document.select("form").first()
        assertThat(form.attr("method"), equalTo("post"))
        assertThat(form.attr("action"), equalTo(acsUrl))

        val samlResponseInput = form.select("input")
        assertThat(samlResponseInput.attr("type"), equalTo("hidden"))
        assertThat(samlResponseInput.attr("name"), equalTo("SAMLResponse"))
        val samlResponse = parseSamlResponse(String(Base64.getDecoder().decode(samlResponseInput.`val`())))
        assertThat(samlResponse.status.statusCode.value, equalTo(StatusCode.SUCCESS))
        val samlAssertion = samlResponse.assertions.first()
        assertThat(samlAssertion.issuer.value, equalTo("http://in-memory-idp"))
        println(samlResponse.serialize())
    }

    @Test
    internal fun `show error page when not valid SAML Request was sent`() {
        idp = InMemoryIdp.Builder().build().start()

        val response = client(Request(POST, "http://localhost:8080/sso")
                .form("SAMLRequest", "invalid request"))

        assertThat(response.status, equalTo(Status.BAD_REQUEST))
    }

    private fun postSamlRequest(samlRequest: String): Response =
            client(Request(POST, "http://localhost:8080/sso").form("SAMLRequest", samlRequest))

    private fun createSamlRequest(acsUrl: String): String {
        val samlRequest = AuthnRequestBuilder().buildObject().apply {
            assertionConsumerServiceURL = acsUrl
            protocolBinding = SAMLConstants.SAML2_POST_BINDING_URI
        }
        return XmlHelper.registry.marshallerFactory.getMarshaller(AuthnRequest.DEFAULT_ELEMENT_NAME)
                ?.marshall(samlRequest)
                ?.let { SerializeSupport.prettyPrintXML(it) }!!
    }

    private fun parseSamlResponse(samlResponse: String): org.opensaml.saml.saml2.core.Response {
        val mdDocument: Document = XmlHelper.registry.parserPool.parse(samlResponse.byteInputStream())
        val unmarshaller = XmlHelper.registry.unmarshallerFactory.getUnmarshaller(mdDocument.documentElement)!!
        return unmarshaller.unmarshall(mdDocument.documentElement) as org.opensaml.saml.saml2.core.Response
    }

}