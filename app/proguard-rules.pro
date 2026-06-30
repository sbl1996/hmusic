# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# AWS SDK references optional XML / CRT / SLF4J integrations that are not
# packaged in this Android app. Suppress those missing-class warnings so R8
# can shrink the actually used code paths.
-dontwarn javax.xml.stream.XMLEventReader
-dontwarn javax.xml.stream.XMLInputFactory
-dontwarn javax.xml.stream.XMLStreamException
-dontwarn javax.xml.stream.events.Attribute
-dontwarn javax.xml.stream.events.Characters
-dontwarn javax.xml.stream.events.StartElement
-dontwarn javax.xml.stream.events.XMLEvent
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn software.amazon.awssdk.crt.auth.credentials.Credentials
-dontwarn software.amazon.awssdk.crt.auth.signing.AwsSigner
-dontwarn software.amazon.awssdk.crt.auth.signing.AwsSigningConfig$AwsSignatureType
-dontwarn software.amazon.awssdk.crt.auth.signing.AwsSigningConfig$AwsSignedBodyHeaderType
-dontwarn software.amazon.awssdk.crt.auth.signing.AwsSigningConfig$AwsSigningAlgorithm
-dontwarn software.amazon.awssdk.crt.auth.signing.AwsSigningConfig
-dontwarn software.amazon.awssdk.crt.auth.signing.AwsSigningResult
-dontwarn software.amazon.awssdk.crt.http.HttpHeader
-dontwarn software.amazon.awssdk.crt.http.HttpRequest
-dontwarn software.amazon.awssdk.crt.http.HttpRequestBodyStream
