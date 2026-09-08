@echo off
setlocal
set "MAVEN_VERSION=3.9.11"
set "DIST=%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MAVEN_VERSION%"
set "MVN=%DIST%\apache-maven-%MAVEN_VERSION%\bin\mvn.cmd"
if not exist "%MVN%" (
  if not exist "%DIST%" mkdir "%DIST%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$u='https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip'; $z='%DIST%\maven.zip'; Invoke-WebRequest -UseBasicParsing -Uri $u -OutFile $z; Expand-Archive -Force $z '%DIST%'"
  if errorlevel 1 exit /b 1
)
call "%MVN%" -f "%~dp0pom.xml" %*
set "MVN_EXIT_CODE=%ERRORLEVEL%"
endlocal & exit /b %MVN_EXIT_CODE%
