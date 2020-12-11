package com.andrasgaal.inmemoryidp

import com.andrasgaal.inmemoryidp.XmlHelper.Companion.registry
import com.andrasgaal.inmemoryidp.XmlHelper.Companion.serialize
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.http4k.core.Method
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
import org.opensaml.saml.saml2.core.impl.AssertionBuilder
import org.opensaml.saml.saml2.core.impl.IssuerBuilder
import org.opensaml.saml.saml2.core.impl.ResponseBuilder
import org.opensaml.saml.saml2.core.impl.StatusBuilder
import org.opensaml.saml.saml2.core.impl.StatusCodeBuilder
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
import java.lang.RuntimeException
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.time.Duration
import java.util.Base64
import java.util.Date

class MetadataSerializationException(message: String) : RuntimeException("Unable to serialize metadata. $message")

class InmemoryIdp private constructor(
        private val _port: Int,
        private val _entityID: String,
        private val _signingCertificate: String,
        private val _samlResponse: String
) {

    private var server: Http4kServer? = null
    private val app = routes(
            "/sso" bind Method.POST to ssoAction()
    )

    val metadata: String get() = constructMetadata()

    class Builder(
            private var port: Int = 8080,
            private var entityID: String = "http://in-memory-idp",
            private var signingCertificate: String? = null,
            private var samlResponse: String? = null
    ) {
        fun entityID(entityID: String) = apply { this.entityID = entityID }
        fun port(port: Int) = apply { this.port = port }
        fun signingCertificate(signingCert: String) = apply { this.signingCertificate = signingCert }
        fun samlResponseXml(responseXml: String) = apply { this.samlResponse = responseXml }
        fun build() = InmemoryIdp(
                port,
                entityID,
                signingCertificate ?: defaultSigningCert(),
                Base64.getEncoder().encodeToString((samlResponse ?: defaultSamlResponse()).toByteArray())
        )

        private fun defaultSamlResponse(): String {
            return ResponseBuilder().buildObject().apply {
                status = StatusBuilder().buildObject().apply {
                    statusCode = StatusCodeBuilder().buildObject().apply { value = SUCCESS }
                }
                this.assertions.add(
                        AssertionBuilder().buildObject().apply {
                            this.issuer = IssuerBuilder().buildObject().apply { value = entityID }
                        }
                )
            }.let { serialize(it, org.opensaml.saml.saml2.core.Response.DEFAULT_ELEMENT_NAME) }
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

            val signer = JcaContentSignerBuilder("SHA256WithRSA").setProvider(BouncyCastleProvider()).build(keyPair.private)
            val holder = builder.build(signer)

            return String(Base64.getEncoder().encode(holder.encoded))
        }
    }

    private fun constructMetadata(): String {
        return registry.builderFactory.getBuilder(EntityDescriptor.ELEMENT_QNAME)
                ?.let { it.buildObject(EntityDescriptor.ELEMENT_QNAME) as? EntityDescriptor }
                ?.applyDetails()
                ?.let { serialize(it, EntityDescriptor.ELEMENT_QNAME) }
                ?: throw MetadataSerializationException("Builder to construct EntityDescriptor is missing.")
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
            Response(OK).header("Location", "http://localhost$_port/post-response")
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
                .replace("SAML_RESPONSE_VALUE", _samlResponse)
    }

    private fun EntityDescriptor?.applyDetails(): EntityDescriptor? {
        return this?.apply {
            entityID = _entityID
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
            location = "http://localhost:$_port/sso"
        }
    }

    private fun signingKeyDescriptor(): KeyDescriptor {
        return KeyDescriptorBuilder().buildObject().apply {
            use = UsageType.SIGNING
            keyInfo = KeyInfoBuilder().buildObject().apply {
                x509Datas.add(X509DataBuilder().buildObject().apply {
                    this.x509Certificates.add(X509CertificateBuilder().buildObject().apply {
                        this.value = _signingCertificate
                    })
                })
            }
        }
    }

    fun start(): InmemoryIdp {
        server = app.asServer(Netty(_port)).start()
        return this
    }

    fun stop() {
        server?.stop()
    }
}