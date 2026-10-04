# Kế hoạch hoàn thành file `performan-test.ps1`

Tôi sẽ viết nội dung cho file `performan-test.ps1` nhằm chạy tự động các bài test hiệu năng và đếm số lượng câu truy vấn SQL (để phát hiện lỗi N+1).

## Chi tiết nội dung `performan-test.ps1`:
Script sẽ thực thi tuần tự 3 bài test hiệu năng chính đã có sẵn trong dự án:

1. **Kiểm tra N+1 (EndpointSqlCountIntegrationTest)**: 
   - Lệnh: `mvn test -Dtest=EndpointSqlCountIntegrationTest`
   - Mục đích: Đếm số lượng câu SQL cho từng endpoint và kiểm tra xem khi số lượng bản ghi tăng lên thì số câu lệnh SQL có tăng theo không (dấu hiệu của lỗi N+1). Báo cáo được xuất ra terminal và file `target/endpoint-sql-report.txt`.

2. **Kiểm tra thời gian thực thi các module (Auth, User, Group)**:
   - Lệnh: `mvn test -Dtest=AuthServicePerfIntegrationTest,UserServicePerfIntegrationTest,GroupServicePerfIntegrationTest`
   - Mục đích: Đo lường tốc độ (thời gian) xử lý của toàn bộ các thao tác nghiệp vụ trên các module User, Auth, và Group. Báo cáo sẽ được xuất ra các file `target/perf-*.txt`.

3. **Kiểm tra bộ nhớ - OOM (GroupScalePerfIntegrationTest)**:
   - Lệnh: `mvn test -Dtest=GroupScalePerfIntegrationTest -Dperf.scale=true -DargLine="-Xmx6g"`
   - Mục đích: Kiểm tra khả năng xử lý khi dữ liệu nhóm cực lớn (lên tới 1.000.000 bản ghi), xem ứng dụng có bị tràn bộ nhớ Java Heap (OOM) hay không. Báo cáo xuất ra `target/perf-group-scale-*.txt`.

Bạn vui lòng kiểm tra nội dung kế hoạch này. Khi nào bạn **xác nhận (Process/Proceed)**, tôi sẽ tự động ghi đoạn mã trên vào file `performan-test.ps1` để bạn chạy nhé!
