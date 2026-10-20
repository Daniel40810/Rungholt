@echo off
rem Rungholt: Verknuepfung "Rungholt" mit Symbol auf dem Desktop und im Startmenue anlegen (Phase 17)
cd /d "%~dp0"
if not exist Rungholt.ico (echo Rungholt.ico fehlt im Projektordner. & pause & exit /b 1)
powershell -NoProfile -ExecutionPolicy Bypass -Command "$w = New-Object -ComObject WScript.Shell; foreach ($d in @([Environment]::GetFolderPath('Desktop'), [Environment]::GetFolderPath('Programs'))) { $s = $w.CreateShortcut((Join-Path $d 'Rungholt.lnk')); $s.TargetPath = '%~dp0Rungholt.bat'; $s.WorkingDirectory = '%~dp0'; $s.IconLocation = '%~dp0Rungholt.ico'; $s.WindowStyle = 7; $s.Description = 'Rungholt - Zweimal am Tag Land: das Wattenmeer und die versunkene Stadt'; $s.Save() }"
if errorlevel 1 (echo Die Verknuepfung liess sich nicht anlegen. & pause & exit /b 1)
echo Verknuepfung "Rungholt" liegt auf dem Desktop und im Startmenue.
pause
