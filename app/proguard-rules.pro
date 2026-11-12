# Pleos SDK: AIDL 바인더·콜백·모델을 리플렉션과 IPC로 쓴다. 문서에 규칙이 없어 패키지 전체를 남긴다.
-keep class ai.pleos.** { *; }
-keep interface ai.pleos.** { *; }
-dontwarn ai.pleos.**

# AAOS Car API(기어·차속·주행 규제)는 기기 쪽 라이브러리
-dontwarn android.car.**

# kotlinx.serialization: 진행 기록 저장
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class io.moodlab.sideseat.** {
    *** Companion;
}
-keepclasseswithmembers class io.moodlab.sideseat.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.moodlab.sideseat.**$$serializer { *; }

# 앱 코드는 크지 않고 로그 태그로 실측하므로 이름을 남긴다(크래시 로그 읽기 쉽게)
-keepnames class io.moodlab.sideseat.** { *; }

# Pleos SDK가 Gson TypeToken으로 capability 응답을 푼다. 제네릭 시그니처가 없으면
# "TypeToken must be created with a type argument"로 모든 capability 조회가 실패한다(release 실측).
-keepattributes Signature, EnclosingMethod, *Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**
