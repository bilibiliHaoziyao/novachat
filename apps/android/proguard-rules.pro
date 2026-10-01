# native methods
-keep class com.b44t.messenger.** { * ; }

# Keep metadata needed by the JSON parser
-keep class chat.delta.rpc.** { * ; }
-keepattributes *Annotation*,EnclosingMethod,Signature
-keepnames class com.fasterxml.jackson.** { *; }

# bug with video recoder
-keep class com.coremedia.iso.** { *; }

# unused SealedData constructor needed by JsonUtils
-keep class org.thoughtcrime.securesms.crypto.KeyStoreHelper* { *; }

# The "MuHan Intelligence" history is (de)serialised by Jackson via reflection, so R8 must not
# rename or strip the constructors/fields of its model classes - otherwise the history is written
# but can no longer be read back.
-keep class org.thoughtcrime.securesms.muhan.MuhanAiMessage { *; }
-keep class org.thoughtcrime.securesms.muhan.MuhanAiConversation { *; }
-keep class org.thoughtcrime.securesms.muhan.MuhanAiArchive { *; }

-dontwarn com.google.firebase.analytics.connector.AnalyticsConnector

# Keep WebRTC classes
-keep class org.webrtc.** { *; }
-keepclassmembers class org.webrtc.** { *; }
-keepattributes InnerClasses

# WorkManager-related rules
-keep class * extends androidx.room.RoomDatabase { *; }
