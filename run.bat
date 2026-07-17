@echo off
REM NetDebugger - TCP/UDP Debug Tool launcher
REM Requires JDK 17+

REM Set your JDK 17 path
set JAVA_HOME=C:\Program Files\Java\jdk-17
if not exist "%JAVA_HOME%\bin\java.exe" set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.6.10-hotspot
if not exist "%JAVA_HOME%\bin\java.exe" set JAVA_HOME=C:\Users\Heawill\.jdks\ms-17.0.19

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] JDK 17 not found.
    pause
    exit /b 1
)

set PATH=%JAVA_HOME%\bin;%PATH%
cd /d "%~dp0"

echo [NetDebugger] Java: %JAVA_HOME%
echo [NetDebugger] CEF:   %cd%\runtimes

java ^
  -Djava.library.path="%cd%\runtimes\windows-amd64" ^
  -jar target\tcp-udp-debug-tool-1.0.0.jar

pause
