Unicode true
ManifestDPIAware true
RequestExecutionLevel user
AutoCloseWindow true
; Registration writes the uninstaller from this executable. Solid compression would
; unpack the entire application again for that small operation.
SetCompressor lzma
Name "Copperbench %VERSION%"
OutFile "%OUTPUT%"
Icon "%ICON%"
UninstallIcon "%ICON%"
VIProductVersion "%VERSION%.0"
VIAddVersionKey ProductName "Copperbench"
VIAddVersionKey ProductVersion "%VERSION%"
VIAddVersionKey FileVersion "%VERSION%.0"
VIAddVersionKey CompanyName "Copperbench Contributors"
VIAddVersionKey FileDescription "Copperbench offline installer"
VIAddVersionKey InternalName "CopperbenchSetup"
VIAddVersionKey OriginalFilename "CopperbenchSetup.exe"
VIAddVersionKey LegalCopyright "Copyright %YEAR% Copperbench Contributors"
!include "MUI2.nsh"
!include "FileFunc.nsh"
!include "LogicLib.nsh"
!include "StrFunc.nsh"
!include "x64.nsh"
${UnStrTrimNewLines}
!define KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\Copperbench"
!insertmacro MUI_PAGE_INSTFILES
!define MUI_UNCONFIRMPAGE_TEXT_TOP "$(CloseApp)"
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"
!insertmacro MUI_LANGUAGE "SimpChinese"
LangString Preparing ${LANG_ENGLISH} "Preparing the offline installer. Please wait..."
LangString Preparing ${LANG_SIMPCHINESE} "正在准备离线安装器，请稍候…"
LangString CloseApp ${LANG_ENGLISH} "Save your work and close Copperbench before uninstalling. Your workspaces and personal settings will be kept."
LangString CloseApp ${LANG_SIMPCHINESE} "请先保存工作并关闭 Copperbench，再卸载。工作区与个人设置会保留。"
LangString Unsupported ${LANG_ENGLISH} "Copperbench requires Windows 11 (64-bit)."
LangString Unsupported ${LANG_SIMPCHINESE} "Copperbench 需要 Windows 11（64 位）。"
LangString StartFailed ${LANG_ENGLISH} "The installation wizard could not start. Download the complete package again from the official release page. If security software blocked a file, keep its detection name and the package SHA-256 for review."
LangString StartFailed ${LANG_SIMPCHINESE} "安装向导无法启动。请从官方发布页重新下载完整包。如果安全软件拦截了文件，请保留检测名称和安装包 SHA-256 以便核查。"
LangString RemoveFailed ${LANG_ENGLISH} "Some application files cannot be removed. Close Copperbench, its Minecraft clients and terminals, then try again. Your workspaces and settings are kept."
LangString RemoveFailed ${LANG_SIMPCHINESE} "部分程序文件无法移除。请关闭 Copperbench、它启动的 Minecraft 客户端和终端后重试。工作区和设置会保留。"
Var Request
Var Scope
Var Parameters
Var Manifest
Var Line
Var Candidate
Var ParentPath
Var RootLength

Function .onInit
  System::Call 'kernel32::GetUserDefaultUILanguage() i.r0'
  IntOp $0 $0 & 0x3FF
  ${If} $0 == 4
    StrCpy $LANGUAGE ${LANG_SIMPCHINESE}
  ${EndIf}
  ${GetParameters} $Parameters
  ${GetOptions} $Parameters "/LOCATIONS=" $Request
  ${If} $Request != ""
    ReadINIStr $Scope "$Request" "Install" "Scope"
    ${If} $Scope == "system"
      SetShellVarContext all
    ${Else}
      SetShellVarContext current
    ${EndIf}
    WriteINIStr "$Request" "Install" "Programs" "$SMPROGRAMS"
    WriteINIStr "$Request" "Install" "DesktopPath" "$DESKTOP"
    SetErrorLevel 0
    Quit
  ${EndIf}
  ${GetOptions} $Parameters "/REGISTER=" $Request
  ${If} $Request != ""
    Call Register
    Quit
  ${EndIf}
  ${GetOptions} $Parameters "/ELEVATE=" $Request
  ${If} $Request != ""
    ReadINIStr $0 "$Request" "Install" "Java"
    ReadINIStr $1 "$Request" "Install" "Jar"
    ReadINIStr $2 "$Request" "Install" "Request"
    ExecShellWait "runas" "$0" '-jar "$1" --worker "$2"' SW_HIDE $3
    SetErrorLevel $3
    Quit
  ${EndIf}
  ; The graphical installer does not accept the legacy /S /D contract.
  IfSilent 0 +3
    SetErrorLevel 2
    Quit
  ${IfNot} ${RunningX64}
    MessageBox MB_OK|MB_ICONSTOP "$(Unsupported)"
    SetErrorLevel 1
    Quit
  ${EndIf}
  SetRegView 64
  ReadRegStr $0 HKLM "SOFTWARE\Microsoft\Windows NT\CurrentVersion" "CurrentBuild"
  ${If} $0 < 22000
    MessageBox MB_OK|MB_ICONSTOP "$(Unsupported)"
    SetErrorLevel 1
    Quit
  ${EndIf}
FunctionEnd

Section
!ifndef UPDATE_HOST
  !insertmacro MUI_HEADER_TEXT "Copperbench" "$(Preparing)"
  InitPluginsDir
  SetOutPath "$PLUGINSDIR\payload"
  File /r "%PAYLOAD%\*"
  SetOutPath "$PLUGINSDIR"
  File /oname=setup.jar "%JAR%"
  File /oname=payload.tsv "%MANIFEST%"
  HideWindow
  ClearErrors
  ExecWait '"$PLUGINSDIR\payload\jdk\bin\javaw.exe" -jar "$PLUGINSDIR\setup.jar" --payload "$PLUGINSDIR\payload" --manifest "$PLUGINSDIR\payload.tsv" --version "%VERSION%" --registrar "$EXEPATH"' $0
  IfErrors startup_failed
  ${If} $0 != 0
    Goto startup_failed
  ${EndIf}
  SetErrorLevel $0
  Goto startup_done
startup_failed:
  MessageBox MB_OK|MB_ICONSTOP "$(StartFailed)"
  SetErrorLevel 1
startup_done:
!endif
SectionEnd

Function Register
  ReadINIStr $INSTDIR "$Request" "Install" "Target"
  ReadINIStr $Scope "$Request" "Install" "Scope"
  ReadINIStr $0 "$Request" "Install" "Desktop"
  IfFileExists "$INSTDIR\.copperbench-install.properties" 0 register_failed
  SetRegView 64
  ${If} $Scope == "system"
    SetShellVarContext all
  ${Else}
    SetShellVarContext current
  ${EndIf}
  ClearErrors
  WriteUninstaller "$INSTDIR\uninstall.exe"
  CreateDirectory "$SMPROGRAMS\Copperbench"
  SetOutPath "$INSTDIR"
  CreateShortCut "$SMPROGRAMS\Copperbench\Copperbench.lnk" "$INSTDIR\copperbench.exe"
  ${If} $0 == "true"
    CreateShortCut "$DESKTOP\Copperbench.lnk" "$INSTDIR\copperbench.exe"
  ${EndIf}
  ${If} $Scope == "system"
    WriteRegStr HKLM "${KEY}" "DisplayName" "Copperbench"
    WriteRegStr HKLM "${KEY}" "Publisher" "Copperbench Contributors"
    WriteRegStr HKLM "${KEY}" "DisplayVersion" "%VERSION%"
    WriteRegStr HKLM "${KEY}" "InstallLocation" "$INSTDIR"
    WriteRegStr HKLM "${KEY}" "UninstallString" '$\"$INSTDIR\uninstall.exe$\"'
    WriteRegStr HKLM "${KEY}" "DisplayIcon" "$INSTDIR\copperbench.exe"
    WriteRegDWORD HKLM "${KEY}" "NoModify" 1
    WriteRegDWORD HKLM "${KEY}" "NoRepair" 1
  ${Else}
    WriteRegStr HKCU "${KEY}" "DisplayName" "Copperbench"
    WriteRegStr HKCU "${KEY}" "Publisher" "Copperbench Contributors"
    WriteRegStr HKCU "${KEY}" "DisplayVersion" "%VERSION%"
    WriteRegStr HKCU "${KEY}" "InstallLocation" "$INSTDIR"
    WriteRegStr HKCU "${KEY}" "UninstallString" '$\"$INSTDIR\uninstall.exe$\"'
    WriteRegStr HKCU "${KEY}" "DisplayIcon" "$INSTDIR\copperbench.exe"
    WriteRegDWORD HKCU "${KEY}" "NoModify" 1
    WriteRegDWORD HKCU "${KEY}" "NoRepair" 1
  ${EndIf}
  IfErrors register_failed
  ; The Java transaction migrates a matching legacy 32-bit entry after registration.
  ; It also restores both views if registration or migration fails.
  SetErrorLevel 0
  Return
register_failed:
  SetErrorLevel 1
FunctionEnd

Function un.onInit
  ; Scope and language are installation metadata; personal settings are never deleted.
  StrCpy $LANGUAGE ${LANG_ENGLISH}
  FileOpen $0 "$INSTDIR\install-language.txt" r
  FileRead $0 $1
  FileClose $0
  ${UnStrTrimNewLines} $1 $1
  ${If} $1 == "zh"
    StrCpy $LANGUAGE ${LANG_SIMPCHINESE}
  ${EndIf}
  SetRegView 64
  ReadRegStr $0 HKLM "${KEY}" "InstallLocation"
  ${If} $0 == $INSTDIR
    StrCpy $Scope "system"
    UserInfo::GetAccountType
    Pop $1
    ${If} $1 != "Admin"
      ; NSIS runs uninstall from a temporary copy. Relaunch the installed file so
      ; the elevated process retains the real installation directory.
      ExecShell "runas" "$INSTDIR\uninstall.exe"
      Quit
    ${EndIf}
    SetShellVarContext all
  ${Else}
    ReadRegStr $0 HKCU "${KEY}" "InstallLocation"
    ${If} $0 != $INSTDIR
      Abort
    ${EndIf}
    StrCpy $Scope "user"
    SetShellVarContext current
  ${EndIf}
  System::Call 'kernel32::GetFileAttributesW(w "$INSTDIR") i.r0'
  IntOp $0 $0 & 0x400
  ${If} $0 != 0
    Abort
  ${EndIf}
FunctionEnd

Section "Uninstall"
  ; Preflight every existing file before deleting anything; OPEN_EXISTING never creates a file.
  StrLen $RootLength "$INSTDIR\"
  FileOpen $Manifest "$INSTDIR\.copperbench-files-windows" r
  IfErrors uninstall_failed
preflight_next:
  ClearErrors
  FileReadUTF16LE $Manifest $Line
  IfErrors preflight_done
  ${UnStrTrimNewLines} $Line $Line
  StrCmp $Line "" preflight_next
  StrCmp $Line "uninstall.exe" preflight_next
  StrCmp $Line ".copperbench-files-windows" preflight_next
  GetFullPathName $Candidate "$INSTDIR\$Line"
  StrCpy $0 $Candidate $RootLength
  StrCmp $0 "$INSTDIR\" 0 uninstall_failed_open
  StrCpy $ParentPath $Candidate
preflight_parent:
  System::Call 'kernel32::GetFileAttributesW(w "$ParentPath") i.r0'
  IntCmp $0 -1 preflight_parent_next
  IntOp $1 $0 & 0x400
  IntCmp $1 0 preflight_parent_next uninstall_failed_open uninstall_failed_open
preflight_parent_next:
  ${GetParent} $ParentPath $ParentPath
  StrCmp $ParentPath "$INSTDIR" preflight_file
  StrCmp $ParentPath "" uninstall_failed_open
  Goto preflight_parent
preflight_file:
  System::Call 'kernel32::GetFileAttributesW(w "$Candidate") i.r0'
  IntCmp $0 -1 preflight_next
  IntOp $0 $0 & 0x10
  IntCmp $0 0 0 preflight_next preflight_next
  System::Call 'kernel32::CreateFileW(w "$Candidate", i 0x40000000, i 7, p 0, i 3, i 0, p 0) p.r0'
  ${If} $0 == -1
    Goto uninstall_failed_open
  ${EndIf}
  System::Call 'kernel32::CloseHandle(p r0)'
  Goto preflight_next
preflight_done:
  FileSeek $Manifest 0 SET
  ; The manifest is sorted with children before directories. Remove only recorded paths.
read_next:
  ClearErrors
  FileReadUTF16LE $Manifest $Line
  IfErrors read_done
  ${UnStrTrimNewLines} $Line $Line
  ${If} $Line == ""
    Goto read_next
  ${EndIf}
  StrCmp $Line "uninstall.exe" read_next
  StrCmp $Line ".copperbench-files" read_next
  StrCmp $Line ".copperbench-files-windows" read_next
  StrCmp $Line ".copperbench-install.properties" read_next
  GetFullPathName $Candidate "$INSTDIR\$Line"
  StrCpy $0 $Candidate $RootLength
  StrCmp $0 "$INSTDIR\" 0 read_next
  ; Refuse traversal through junctions or symbolic links, including parent directories.
  StrCpy $ParentPath $Candidate
check_parent:
  System::Call 'kernel32::GetFileAttributesW(w "$ParentPath") i.r0'
  IntOp $0 $0 & 0x400
  IntCmp $0 0 +2
  Goto read_next
  ${GetParent} $ParentPath $ParentPath
  StrCmp $ParentPath "$INSTDIR" remove_entry
  StrCmp $ParentPath "" read_next
  Goto check_parent
remove_entry:
  System::Call 'kernel32::GetFileAttributesW(w "$Candidate") i.r0'
  IntOp $0 $0 & 0x10
  ${If} $0 != 0
    RMDir "$Candidate"
    Goto read_next
  ${EndIf}
  Delete "$Candidate"
  IfFileExists "$Candidate" uninstall_failed_open
  Goto read_next
read_done:
  FileClose $Manifest
  ; A running application leaves its launcher locked. Retain its registration so uninstall can be retried.
  IfFileExists "$INSTDIR\copperbench.exe" uninstall_failed
  Delete "$INSTDIR\.copperbench-files"
  Delete "$INSTDIR\.copperbench-files-windows"
  Delete "$INSTDIR\.copperbench-install.properties"
  Delete "$INSTDIR\uninstall.exe"
  Delete "$DESKTOP\Copperbench.lnk"
  Delete "$SMPROGRAMS\Copperbench\Copperbench.lnk"
  RMDir "$SMPROGRAMS\Copperbench"
  ${If} $Scope == "system"
    DeleteRegKey HKLM "${KEY}"
  ${Else}
    DeleteRegKey HKCU "${KEY}"
  ${EndIf}
  RMDir "$INSTDIR"
  SetErrorLevel 0
  Goto uninstall_done
uninstall_failed_open:
  FileClose $Manifest
uninstall_failed:
  MessageBox MB_OK|MB_ICONSTOP "$(RemoveFailed)"
  SetErrorLevel 1
uninstall_done:
SectionEnd
