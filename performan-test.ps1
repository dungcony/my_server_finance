Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "1. Kiem tra N+1 (EndpointSqlCountIntegrationTest)" -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan
mvn test -Dtest=EndpointSqlCountIntegrationTest

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "2. Kiem tra thoi gian thuc thi cac module (Auth, User, Group)" -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan
mvn test "-Dtest=AuthServicePerfIntegrationTest,UserServicePerfIntegrationTest,GroupServicePerfIntegrationTest"

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "3. Kiem tra RAM/OOM (GroupScalePerfIntegrationTest)" -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan
mvn test "-Dtest=GroupScalePerfIntegrationTest" "-Dperf.scale=true" "-DargLine=-Xmx6g"

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "Hoan thanh tat ca bai test hieu nang!" -ForegroundColor Green
