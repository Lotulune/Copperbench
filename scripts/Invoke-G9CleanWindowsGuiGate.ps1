[CmdletBinding()]
param(
	[string]$VmName = 'Copperbench-G7',
	[string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
	[string]$GuestUser = 'g7admin',
	[string]$PasswordFile = 'D:\Hyper-V\G7\g7admin.password.txt',
	[string]$InstallDir = 'C:\Copperbench-G9',
	[string]$WorkspaceName = 'guigatedelta'
)
