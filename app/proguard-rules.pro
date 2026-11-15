# R8 keep rules for the release build. The libraries in use (Compose, Room, Navigation,
# kotlinx.serialization) bundle their own consumer rules, so nothing extra is needed yet.
# If a release build crashes where the debug build doesn't, add the missing keep rule here.

# Keep line numbers in crash reports from the Play Console (upload the mapping file with each release).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
