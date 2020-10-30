package com.andrasgaal.inmemoryidp

import java.lang.RuntimeException

class MetadataSerializationException(message: String) : RuntimeException("Unable to serialize metadata. $message")
