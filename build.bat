@call gradlew assembleDebug && call adbinstall E:\home\adb\bin\__CVE\___CVE-2023-33107_KETO\..releaseBranch\_APP\app\build\outputs\apk\debug\app-debug.apk
@echo press R to reboot
@call getch
@if %KEY% == 114 call as reboot