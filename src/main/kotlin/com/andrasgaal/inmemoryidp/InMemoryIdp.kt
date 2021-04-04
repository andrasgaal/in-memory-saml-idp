package com.andrasgaal.inmemoryidp

import com.andrasgaal.inmemoryidp.XmlHelper.Companion.registry
import com.andrasgaal.inmemoryidp.XmlHelper.Companion.serialize
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.core.body.form
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Http4kServer
import org.http4k.server.Netty
import org.http4k.server.asServer
import org.opensaml.saml.common.xml.SAMLConstants.SAML20P_NS
import org.opensaml.saml.common.xml.SAMLConstants.SAML2_POST_BINDING_URI
import org.opensaml.saml.saml2.core.AuthnRequest
import org.opensaml.saml.saml2.core.StatusCode.SUCCESS
import org.opensaml.saml.saml2.core.impl.*
import org.opensaml.saml.saml2.metadata.EntityDescriptor
import org.opensaml.saml.saml2.metadata.KeyDescriptor
import org.opensaml.saml.saml2.metadata.RoleDescriptor
import org.opensaml.saml.saml2.metadata.SingleSignOnService
import org.opensaml.saml.saml2.metadata.impl.IDPSSODescriptorBuilder
import org.opensaml.saml.saml2.metadata.impl.KeyDescriptorBuilder
import org.opensaml.saml.saml2.metadata.impl.SingleSignOnServiceBuilder
import org.opensaml.security.credential.UsageType
import org.opensaml.xmlsec.signature.impl.KeyInfoBuilder
import org.opensaml.xmlsec.signature.impl.X509CertificateBuilder
import org.opensaml.xmlsec.signature.impl.X509DataBuilder
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.time.Duration
import java.util.*

class InMemoryIdp private constructor(
    private val port: Int,
    private val entityId: String,
    private val signingCertificate: String,
    private val samlResponse: String
) {

    private var server: Http4kServer? = null
    private val app = routes(
        "/" bind GET to { Response(OK) },
        "/sso" bind POST to ssoAction()
    )

    val metadata: String get() = constructMetadata()

    class Builder(
        private var idpPort: Int = 8080,
        private var idpEntityId: String = "http://in-memory-idp",
        private var idpSigningCertificate: String? = null,
        private var idpSamlResponse: String? = null
    ) {
        fun entityId(entityID: String) = apply { this.idpEntityId = entityID }
        fun port(port: Int) = apply { this.idpPort = port }
        fun signingCertificate(signingCert: String) = apply { this.idpSigningCertificate = signingCert }
        fun samlResponseXml(responseXml: String) = apply { this.idpSamlResponse = responseXml }
        fun build() = InMemoryIdp(
            idpPort,
            idpEntityId,
            idpSigningCertificate ?: defaultSigningCert(),
            Base64.getEncoder().encodeToString((idpSamlResponse ?: defaultSamlResponse()).toByteArray())
        )

        private fun defaultSamlResponse(): String {
            return ResponseBuilder().buildObject().apply {
                status = StatusBuilder().buildObject().apply {
                    statusCode = StatusCodeBuilder().buildObject().apply { value = SUCCESS }
                }
                this.assertions.add(
                    AssertionBuilder().buildObject().apply {
                        this.issuer = IssuerBuilder().buildObject().apply { value = idpEntityId }
                    }
                )
            }.serialize()
        }

        private fun defaultSigningCert(): String {
            val keyPair = KeyPairGenerator.getInstance("RSA").apply {
                initialize(2048)
            }.generateKeyPair()

            val issuer = X500Name("cn=in-memory-idp")
            val subject = X500Name("dc=whatever")
            val serial = BigInteger.valueOf(System.currentTimeMillis())
            val notBeforeDate = Date(System.currentTimeMillis() - Duration.ofDays(1).toMillis())
            val notAfterDate = Date(System.currentTimeMillis() + Duration.ofDays(365).toMillis())
            val subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)

            val builder = X509v3CertificateBuilder(issuer, serial, notBeforeDate, notAfterDate, subject, subjectPublicKeyInfo)

            val contentSigner = JcaContentSignerBuilder("SHA256WithRSA").setProvider(BouncyCastleProvider()).build(keyPair.private)
            val certificateHolder = builder.build(contentSigner)

            return String(Base64.getEncoder().encode(certificateHolder.encoded))
        }
    }

    private fun constructMetadata(): String {
        return registry.builderFactory.getBuilder(EntityDescriptor.ELEMENT_QNAME)
            ?.let { it.buildObject(EntityDescriptor.ELEMENT_QNAME) as? EntityDescriptor }
            ?.applyDetails()
            ?.let { serialize(it, EntityDescriptor.ELEMENT_QNAME) }
            ?: throw SerializationException("Builder to construct EntityDescriptor is missing.")
    }

    private fun ssoAction(): (Request) -> Response = { request ->
        val samlRequestUnmarshaller = registry.unmarshallerFactory.getUnmarshaller(AuthnRequest.DEFAULT_ELEMENT_NAME)
        val samlRequest = try {
            request.form("SAMLRequest")
                ?.let { registry.parserPool.parse(it.byteInputStream()) }
                ?.let { samlRequestUnmarshaller?.unmarshall(it.documentElement) as? AuthnRequest }
        } catch (e: Exception) {
            null
        }

        samlRequest?.let {
            Response(OK).header("Location", "http://localhost$port/post-response")
                .body(postResponseHtml(it.assertionConsumerServiceURL))
        } ?: Response(BAD_REQUEST)

    }

    private fun postResponseHtml(acsUrl: String): String {
        return """
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml" lang="en">
            <head></head>
            <body>
            <form method="post" action="SP_ACS_URL" id="samlRequestPostForm">
                <input type="hidden" name="SAMLResponse" value="SAML_RESPONSE_VALUE">
            </form>
            <script>
              document.getElementById("samlRequestPostForm").submit();
            </script>
            </body>
            </html>
        """.trimIndent()
            .replace("SP_ACS_URL", acsUrl)
            .replace("SAML_RESPONSE_VALUE", samlResponse)
    }

    private fun EntityDescriptor?.applyDetails(): EntityDescriptor? {
        return this?.apply {
            entityID = entityId
            this.roleDescriptors.add(idpSsoDescriptor())
        }
    }

    private fun idpSsoDescriptor(): RoleDescriptor {
        return IDPSSODescriptorBuilder().buildObject().apply {
            addSupportedProtocol(SAML20P_NS)
            keyDescriptors.add(signingKeyDescriptor())
            singleSignOnServices.add(ssoService())
        }
    }

    private fun ssoService(): SingleSignOnService {
        return SingleSignOnServiceBuilder().buildObject().apply {
            binding = SAML2_POST_BINDING_URI
            location = "http://localhost:$port/sso"
        }
    }

    private fun signingKeyDescriptor(): KeyDescriptor {
        return KeyDescriptorBuilder().buildObject().apply {
            use = UsageType.SIGNING
            keyInfo = KeyInfoBuilder().buildObject().apply {
                x509Datas.add(X509DataBuilder().buildObject().apply {
                    this.x509Certificates.add(X509CertificateBuilder().buildObject().apply {
                        this.value = signingCertificate
                    })
                })
            }
        }
    }

    fun start(): InMemoryIdp {
        server = app.asServer(Netty(port)).start()
        return this
    }

    fun stop() {
        server?.stop()
    }
}