@echo off
setlocal EnableExtensions

set "DC_STUDY_MODE=run"
if /I "%~1"=="--check" (
    set "DC_STUDY_MODE=check"
    shift
)

if "%~1"=="" goto usage
if "%~2"=="" goto usage
if not "%~3"=="" goto usage

set "DC_STUDY_CONFIG=%~f1"
set "DC_STUDY_DIRECTORY=%~f2"

if not exist "%DC_STUDY_CONFIG%" (
    echo Scenario file not found: %DC_STUDY_CONFIG% 1>&2
    exit /b 2
)
if not exist "%DC_STUDY_DIRECTORY%\" (
    echo Study directory not found: %DC_STUDY_DIRECTORY% 1>&2
    exit /b 2
)
if "%SPLOT_PROJECT_DIR%"=="" (
    echo SPLOT_PROJECT_DIR is not set. 1>&2
    echo Example: set SPLOT_PROJECT_DIR=C:\pvcs_worktrees\itsolutions-splot\allProjects 1>&2
    exit /b 2
)

if "%DC_STUDY_MODE%"=="check" (
    call "%~dp0gradlew.bat" dcCheckGt --rerun-tasks --console=plain "-PconfFile=%DC_STUDY_CONFIG%" "-PworkingDir=%DC_STUDY_DIRECTORY%"
) else (
    call "%~dp0gradlew.bat" dcStudyGt --rerun-tasks --console=plain "-Pargs=%DC_STUDY_CONFIG%" "-PworkingDir=%DC_STUDY_DIRECTORY%"
)
exit /b %ERRORLEVEL%

:usage
echo Usage: dcStudy [--check] ^<scenario-file^> ^<study-directory^> 1>&2
exit /b 2
