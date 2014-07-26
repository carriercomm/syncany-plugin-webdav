@if "%DEBUG%" == "" @echo off

set APP_NAME=syncanyd
set APP_USER_DIR=%AppData%\Syncany
set APP_DAEMON_CONTROL=%APP_USER_DIR%\daemon.ctrl
set APP_DAEMON_PIDFILE=%APP_USER_DIR%\daemon.pid
set APP_LOG_DIR=%APP_USER_DIR%\logs
set APP_LOG_FILE=%APP_LOG_DIR%\daemon.log

if not exist "%APP_USER_DIR%" mkdir "%APP_USER_DIR%"
if not exist "%APP_LOG_DIR%" mkdir "%APP_LOG_DIR%"

if exist %APP_DAEMON_PIDFILE% (
  set /P PID= < "%APP_DAEMON_PIDFILE%"
  
  tasklist /FI "PID eq %PID%" 2>NUL | find /I /N "%PID%" > NUL
  if "%ERRORLEVEL%"=="0" ( 
    set RUNNING=1
  ) else (
    set RUNNING=0
  )
) else (
  set PID=-1
  set RUNNING=0
)

@if "%1" == "start" goto start
@if "%1" == "stop" goto stop
@if "%1" == "reload" goto reload
@if "%1" == "status" goto status

echo Usage: syncanyd (start/stop/reload/status)
goto mainEnd

:stop
if %RUNNING% == 1 (
  echo shutdown >> %APP_DAEMON_CONTROL%
  echo | set /p=Stopping daemon: 
  
  for /l %%i in (1, 1, 10) do (
    if exist %APP_DAEMON_PIDFILE% (
	  echo | set /p=.
	  timeout /t 1 /nobreak > NUL
	) 
  )
  
  if exist %APP_DAEMON_PIDFILE% (
    echo  Failed. 
	exit /b 1
  ) else (
    echo  %APP_NAME%.
  )
) else (
  echo Stopping daemon: %APP_NAME% not running
)
goto mainEnd

:reload
if %RUNNING% == 1 (
  echo reload >> %APP_DAEMON_CONTROL%
  echo | set /p=Reloading daemon: %APP_NAME%.
) else (
  echo Reloading daemon: %APP_NAME% not running
)
goto mainEnd

:status
if %RUNNING% == 1 (
  echo Checking daemon: %APP_NAME% running with pid %PID%
) else (
  echo Checking daemon: %APP_NAME% not running
)
goto mainEnd

:start
@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

@rem Add default JVM options here. You can also use JAVA_OPTS and SYNCANY_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx1024m" "-Dfile.encoding=utf-8"

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%..

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=javaw.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto init

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

exit /b 1

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/javaw.exe

if exist "%JAVA_EXE%" goto init

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

exit /b 1

:init
if %RUNNING% == 1 (
  echo Starting daemon: %APP_NAME% already running with pid %PID%
  goto mainEnd
) 

set CLASSPATH=%APP_HOME%\lib\*;%AppData%\Syncany\plugins\lib\*

echo | set /p=Starting daemon: .
start "" /b "%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% -classpath "%CLASSPATH%" org.syncany.Syncany --log=%APP_LOG_FILE% daemon

for /l %%i in (1, 1, 10) do (
  if not exist %APP_DAEMON_PIDFILE% (
    echo | set /p=.
    timeout /t 1 /nobreak > NUL
  ) 
)

if not exist %APP_DAEMON_PIDFILE% (
  echo  Failed. 
  exit /b 1
) else (
  echo  %APP_NAME%.
  goto mainEnd
)

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
