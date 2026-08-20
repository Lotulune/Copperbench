[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$bundledJava = (Resolve-Path (Join-Path $repositoryRoot 'jdk/jbr25_win_64')).Path
$previousJavaHome = $env:JAVA_HOME

try {
	$env:JAVA_HOME = $bundledJava
	Push-Location $repositoryRoot
	try {
		# G3: MCP transport security (tokens, origins, CORS, audit),
		# permission profiles + protected operations, headless contract
		# consistency, local history recovery points and JGit isolation.
		& .\gradlew.bat --no-daemon test --tests 'dev.copperbench.*'
		if ($LASTEXITCODE -ne 0) {
			throw "Stage 2 Java gate failed with exit code $LASTEXITCODE"
		}
	} finally {
		Pop-Location
	}

	# G3: official Streamable HTTP protocol scenarios that apply to the
	# capabilities advertised by Copperbench, including DNS rebinding checks.
	& (Join-Path $repositoryRoot 'scripts/verify-mcp-conformance.ps1')

	Push-Location (Join-Path $repositoryRoot 'ui-core')
	try {
		# G3: versioned envelopes, handshake, history projections,
		# restore confirmation fact and bounded approval decisions.
		& npm test
		if ($LASTEXITCODE -ne 0) {
			throw "UI-Core contract gate failed with exit code $LASTEXITCODE"
		}
	} finally {
		Pop-Location
	}

	Push-Location (Join-Path $repositoryRoot 'ui-shell')
	try {
		# G4 preview / stage-2 UI parallel work: history timeline, diff view,
		# protected-operation approvals and the full contract scenario replay.
		& npm run build
		if ($LASTEXITCODE -ne 0) {
			throw "UI shell production build failed with exit code $LASTEXITCODE"
		}
		& npx playwright test
		if ($LASTEXITCODE -ne 0) {
			throw "UI shell Playwright gate failed with exit code $LASTEXITCODE"
		}
	} finally {
		Pop-Location
	}
} finally {
	$env:JAVA_HOME = $previousJavaHome
}
