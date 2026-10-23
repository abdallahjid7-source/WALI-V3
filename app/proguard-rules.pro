# Add project specific ProGuard / R8 rules here.
# Enables aggressive DEX bytecode optimization, dead-code elimination, and obfuscation
# required by Google Play Console ("Optimisation du code DEX").

-optimizationpasses 5
-allowaccessmodification
-repackageclasses 'o'

# Preserve line numbers for Play Console crash deobfuscation while hiding source file names
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve enum constants used in JSON / DataStore / Firestore string serialization (.name / .valueOf)
-keepclassmembers enum com.example.wali.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Suppress harmless warnings from third-party SDKs during R8 full-mode shrinking
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
