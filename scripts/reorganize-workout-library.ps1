param(
    [string]$Root = '\\SCONCEPT\Berthier\Entertainment\Workouts',
    [string]$LegacyCatalogPath = (Join-Path $PSScriptRoot '..\app\src\main\assets\gym\phoenix_seed_catalog.json'),
    [string]$AppCatalogOutputPath = (Join-Path $PSScriptRoot '..\app\src\main\assets\gym\workout_catalog.json'),
    [switch]$WhatIfOnly
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Normalize-Title([string]$Value) {
    $clean = $Value -replace '-[0-9a-f]{10}$', ''
    $clean = $clean -replace '[<>:"/\\|?*]', ' '
    $clean = ($clean -replace '\s+', ' ').Trim().TrimEnd('.')
    if ([string]::IsNullOrWhiteSpace($clean)) { return 'Untitled Exercise' }
    return (Get-Culture).TextInfo.ToTitleCase($clean.ToLowerInvariant())
}

function To-Slug([string]$Value) {
    $slug = $Value.ToLowerInvariant() -replace '[^a-z0-9]+', '_'
    return $slug.Trim('_')
}

function Get-MuscleGroup($Record, [string]$Title) {
    # ExRx's URL muscle path is authoritative. Title matching is only a safe fallback for
    # records whose source metadata is incomplete (never match short fragments such as `lat`).
    $source = "$($Record.source_url) $($Record.menu_url)".ToLowerInvariant()
    $name = $Title.ToLowerInvariant()
    if ($source -match 'neck') { return 'Neck' }
    if ($source -match 'abdominal|oblique|waist') { return 'Abs' }
    if ($source -match 'pectoral|chest') { return 'Chest' }
    if ($source -match 'deltoid|shoulder|rotator') { return 'Shoulders' }
    if ($source -match 'bicep|tricep|forearm') { return 'Arms' }
    if ($source -match 'quadricep|hamstring|glute|hips|calves|thigh') { return 'Legs' }
    if ($source -match 'back|latissimus|erectorspinae') { return 'Back' }
    if ($name -match '\bneck\b') { return 'Neck' }
    if ($name -match '\b(crunch|sit-up|sit up|leg raise|mountain climber|side bend)\b') { return 'Abs' }
    if ($name -match '\b(chest|bench press|push-up|push up|fly|chest dip)\b') { return 'Chest' }
    if ($name -match '\b(shoulder|lateral raise|front raise|rear delt|upright row|shrug|rotation)\b') { return 'Shoulders' }
    if ($name -match '\b(leg|squat|lunge|step-up|step up|calf|hip)\b') { return 'Legs' }
    if ($name -match '\b(bicep|tricep|forearm|wrist|curl|pushdown|kickback|preacher)\b') { return 'Arms' }
    if ($name -match '\b(back|row|pull-up|pull up|pulldown|pullover|deadlift|hyperextension)\b') { return 'Back' }
    return 'Full Body'
}

function Get-Equipment([string]$Title) {
    $name = $Title.ToLowerInvariant()
    foreach ($pair in @(
        @{ Pattern = 'barbell|safety bar'; Value = 'barbell' },
        @{ Pattern = 'dumbbell'; Value = 'dumbbell' },
        @{ Pattern = 'cable'; Value = 'cable' },
        @{ Pattern = 'lever|machine|sled|smith'; Value = 'machine' },
        @{ Pattern = 'band'; Value = 'band' },
        @{ Pattern = 'suspended'; Value = 'suspension' }
    )) { if ($name -match $pair.Pattern) { return @($pair.Value) } }
    return @('bodyweight')
}

if (-not (Test-Path -LiteralPath $Root -PathType Container)) { throw "Workout root does not exist: $Root" }

# Preserve the app's curated pacing, level, target-muscle, sidedness, and equipment data whenever
# a downloaded exercise has the same human-readable name. The random legacy IDs remain stable for
# existing app data; filesystem identifiers are never used in the resulting library.
$legacyByName = @{}
if (Test-Path -LiteralPath $LegacyCatalogPath -PathType Leaf) {
    $legacyCatalog = Get-Content -LiteralPath $LegacyCatalogPath -Raw | ConvertFrom-Json
    foreach ($entry in $legacyCatalog) {
        $legacyByName[(Normalize-Title ([string]$entry.name)).ToLowerInvariant()] = $entry
    }
}

$sourceFolders = Get-ChildItem -LiteralPath $Root -Directory | Where-Object { $_.Name -notin @('_archive', '_staging') }
$records = [System.Collections.Generic.List[object]]::new()
foreach ($folder in $sourceFolders) {
    $jsonFile = Join-Path $folder.FullName 'exercise.json'
    if (-not (Test-Path -LiteralPath $jsonFile -PathType Leaf)) { continue }
    try { $metadata = Get-Content -LiteralPath $jsonFile -Raw | ConvertFrom-Json } catch { throw "Invalid JSON: $jsonFile ($($_.Exception.Message))" }
    $title = Normalize-Title ([string]$metadata.title)
    $videos = @(Get-ChildItem -LiteralPath $folder.FullName -File -Filter '*.mp4')
    $records.Add([pscustomobject]@{ Folder = $folder; Metadata = $metadata; Title = $title; Group = Get-MuscleGroup $metadata $title; Videos = $videos })
}
if ($records.Count -eq 0) { throw "No exercise.json files found under $Root" }

$plans = [System.Collections.Generic.List[object]]::new()
foreach ($exercise in ($records | Group-Object Group, Title)) {
    $parts = $exercise.Name -split ', ', 2
    $group = $parts[0]; $title = $parts[1]
    $targetDir = Join-Path (Join-Path $Root $group) $title
    $videoIndex = 0
    foreach ($record in $exercise.Group) {
        foreach ($video in $record.Videos) {
            $videoIndex++
            $suffix = if ($exercise.Group.Count -gt 1 -or $record.Videos.Count -gt 1) { '_{0:D3}' -f $videoIndex } else { '' }
            $targetName = "$title$suffix$($video.Extension.ToLowerInvariant())"
            $plans.Add([pscustomobject]@{ Source = $video.FullName; TargetDir = $targetDir; TargetName = $targetName; Record = $record })
        }
    }
}

$duplicateTargets = $plans | Group-Object { Join-Path $_.TargetDir $_.TargetName } | Where-Object Count -gt 1
if ($duplicateTargets) { throw "Preflight failed: duplicate target names were generated." }
$existingCollision = $plans | Where-Object { Test-Path -LiteralPath (Join-Path $_.TargetDir $_.TargetName) }
if ($existingCollision) { throw "Preflight failed: destination already exists. This protects an existing organized library." }

$manifestExercises = foreach ($exercise in ($records | Group-Object Group, Title)) {
    $parts = $exercise.Name -split ', ', 2
    $group = $parts[0]; $title = $parts[1]
    $relatedPlans = @($plans | Where-Object { $_.Record -in $exercise.Group })
    $first = $exercise.Group[0].Metadata
    [ordered]@{
        id = To-Slug "$group $title"
        name = $title
        muscleGroup = $group
        equipment = @(Get-Equipment $title)
        classification = $first.classification
        content = $first.content
        sourceUrl = $first.source_url
        menuUrl = $first.menu_url
        instructions = $first.instructions
        images = @($first.images)
        sourceExerciseIds = @($exercise.Group | ForEach-Object { $_.Metadata.exercise_id })
        videos = @($relatedPlans | ForEach-Object {
            [ordered]@{
                filename = $_.TargetName
                relativePath = "$group/$title/$($_.TargetName)"
                nasRelativePath = "Entertainment/Workouts/$group/$title/$($_.TargetName)"
                localUri = $null
                durationSeconds = $_.Record.Metadata.video.duration_seconds
                duration = $_.Record.Metadata.video.duration
                provider = $_.Record.Metadata.video.provider
                providerId = $_.Record.Metadata.video.provider_id
                sourceUrls = @($_.Record.Metadata.video_sources)
            }
        })
    }
}

$manifest = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    rootFolder = 'Workouts'
    pathLayout = '<Muscle Group>/<Exercise Name>/<Video Name>.mp4'
    exercises = @($manifestExercises | Sort-Object muscleGroup, name)
}
$manifestJson = $manifest | ConvertTo-Json -Depth 12
$catalog = @($manifest.exercises | ForEach-Object {
    $legacy = $legacyByName[$_.name.ToLowerInvariant()]
    [ordered]@{
        id = if ($null -ne $legacy) { $legacy.id } else { $_.id }
        name = $_.name
        equipment = if ($null -ne $legacy -and $legacy.equipment.Count -gt 0) { @($legacy.equipment) } else { $_.equipment }
        muscleGroups = if ($null -ne $legacy -and $legacy.muscleGroups.Count -gt 0) { @($legacy.muscleGroups) } else { @((To-Slug $_.muscleGroup)) }
        muscles = if ($null -ne $legacy) { @($legacy.muscles) } else { @() }
        sidedness = if ($null -ne $legacy) { $legacy.sidedness } else { $null }
        level = if ($null -ne $legacy) { $legacy.level } else { 'BEGINNER' }
        pacing = if ($null -ne $legacy) { $legacy.pacing } else { [ordered]@{ workSeconds = 90; restSeconds = 75; intensity = 'steady'; continuous = $false } }
    }
}) | ConvertTo-Json -Depth 6

Write-Output "Prepared $($records.Count) source records, $($manifest.exercises.Count) grouped exercises, and $($plans.Count) videos."
if ($WhatIfOnly) { return }

foreach ($plan in $plans) {
    New-Item -ItemType Directory -Path $plan.TargetDir -Force | Out-Null
    Move-Item -LiteralPath $plan.Source -Destination (Join-Path $plan.TargetDir $plan.TargetName)
}

$manifestPath = Join-Path $Root 'workout_library.json'
[System.IO.File]::WriteAllText($manifestPath, $manifestJson, [System.Text.UTF8Encoding]::new($false))
[System.IO.File]::WriteAllText((Join-Path $Root 'workout_catalog.json'), $catalog, [System.Text.UTF8Encoding]::new($false))
New-Item -ItemType Directory -Path (Split-Path -Parent $AppCatalogOutputPath) -Force | Out-Null
[System.IO.File]::WriteAllText($AppCatalogOutputPath, $catalog, [System.Text.UTF8Encoding]::new($false))

# Every source metadata file is now represented in workout_library.json; remove only emptied legacy folders.
foreach ($folder in $sourceFolders) {
    if (Test-Path -LiteralPath $folder.FullName) {
        Get-ChildItem -LiteralPath $folder.FullName -File -Filter 'exercise.json' | Remove-Item -Force
        if (-not (Get-ChildItem -LiteralPath $folder.FullName -Force | Select-Object -First 1)) { Remove-Item -LiteralPath $folder.FullName -Force }
    }
}

$remainingVideos = @(Get-ChildItem -LiteralPath $Root -Recurse -File -Filter '*.mp4')
if ($remainingVideos.Count -ne $plans.Count) { throw "Verification failed: expected $($plans.Count) videos, found $($remainingVideos.Count)." }
Write-Output "Complete. Manifest: $manifestPath"
