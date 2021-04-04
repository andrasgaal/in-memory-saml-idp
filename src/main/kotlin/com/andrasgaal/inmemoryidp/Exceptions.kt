package com.andrasgaal.inmemoryidp

import java.lang.RuntimeException

class SerializationException(message: String) : RuntimeException("Unable to serialize XML. $message")