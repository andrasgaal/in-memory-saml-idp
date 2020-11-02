package com.andrasgaal.inmemoryidp

import net.shibboleth.utilities.java.support.xml.SerializeSupport
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.server.Http4kServer
import org.http4k.server.Netty
import org.http4k.server.asServer
import org.opensaml.core.xml.XMLObject
import org.opensaml.saml.common.xml.SAMLConstants.SAML20P_NS
import org.opensaml.saml.saml2.metadata.EntityDescriptor
import org.opensaml.saml.saml2.metadata.KeyDescriptor
import org.opensaml.saml.saml2.metadata.RoleDescriptor
import org.opensaml.saml.saml2.metadata.impl.IDPSSODescriptorBuilder
import org.opensaml.saml.saml2.metadata.impl.KeyDescriptorBuilder
import org.opensaml.security.credential.UsageType
import org.opensaml.xmlsec.signature.impl.KeyInfoBuilder
import org.opensaml.xmlsec.signature.impl.X509CertificateBuilder
import org.opensaml.xmlsec.signature.impl.X509DataBuilder
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.time.Duration
import java.util.Base64
import java.util.Date


class InmemoryIdp(private val customEntityID: String = "http://in-memory-idp") {

    private var port: Int? = null
    private var signingCert: String? = null
    private var server: Http4kServer? = null
    private val app = { request: Request -> Response(OK).body("Hello") }

    val metadata: String get() = constructMetadata()

    private fun constructMetadata(): String {
        return XmlHelper.registry.builderFactory.getBuilder(EntityDescriptor.ELEMENT_QNAME)
                ?.let { it.buildObject(EntityDescriptor.ELEMENT_QNAME) as? EntityDescriptor }
                ?.applyDetails()
                ?.let { serialize(it) }
                ?: throw MetadataSerializationException("Builder to construct EntityDescriptor is missing.")
    }

    private fun EntityDescriptor?.applyDetails(): EntityDescriptor? {
        return this?.apply {
            entityID = customEntityID
            this.roleDescriptors.add(idpSsoDescriptor())
        }
    }

    private fun idpSsoDescriptor(): RoleDescriptor {
        return IDPSSODescriptorBuilder().buildObject().apply {
            addSupportedProtocol(SAML20P_NS)
            keyDescriptors.add(signingKeyDescriptor())
        }
    }

    private fun signingKeyDescriptor(): KeyDescriptor {
        return KeyDescriptorBuilder().buildObject().apply {
            use = UsageType.SIGNING
            keyInfo = KeyInfoBuilder().buildObject().apply {
                x509Datas.add(X509DataBuilder().buildObject().apply {
                    this.x509Certificates.add(X509CertificateBuilder().buildObject().apply {
                        this.value = getOrCreateSigningCertificate()
                    })
                })
            }
        }
    }

    private fun serialize(xml: XMLObject): String {
        return XmlHelper.registry.marshallerFactory.getMarshaller(EntityDescriptor.ELEMENT_QNAME)
                ?.marshall(xml)
                ?.let { SerializeSupport.prettyPrintXML(it) }
                ?: throw MetadataSerializationException("Marshaller to serialize EntityDescriptor is missing.")
    }

    private fun getOrCreateSigningCertificate(): String {
        return signingCert ?: {
            val generatedCert = generateSigningCert()
            signingCert = generatedCert
            generatedCert
        }()
    }

    private fun generateSigningCert(): String {
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