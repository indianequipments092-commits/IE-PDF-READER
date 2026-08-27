# PDFBox uses runtime-loaded classes/resources in some code paths.
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.apache.**
