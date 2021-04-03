# In-Memory SAML IDP, still in progress...

## What? Why?
This is a SAML Identity Provider, allowing you to easily test your SAML Service Provider without deploying a fully functional SAML IDP somewhere.
You can configure its behavior in your automated tests, making it possible to test edge cases like receiving a SAML Response with an invalid certificate.

## How?
You can configure the In-Memory IDP by a Builder and then start it using the start() method.
```kotlin
InMemoryIdp.Builder()
    .port(9999)
    .entityId("my-saml-idp-entity-id")
    .build()
    .start()
```
There are plenty of other things to configure like the signing certificate in the SAML Response and Metadata or the whole SAML Response itself.
It's not required to configure any of them though as the IDP is using [default values](#default-values).

### Metadata
You most probably want to configure your SP-to-test with the IDP's metadata. To do so, just build an InMemoryIdp with the builder and get the metadata property.
```
val metadata = InMemoryIdp.Builder()
    .build()
    .metadata
```
Note: You don't have to start the IDP to get its metadata.

### Default values
```
port: 8080
entityId: http://in-memory-idp
signingCertificate: generated SHA256 with RSA valid for 1 year
samlResponseXml: generated SAML Respponse with SUCCESS status code
POST binding location (SingleSignOnService): http://localhost:8080/sso 
```
