import http from 'k6/http';
import { check, sleep } from 'k6';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';

// Cấu hình tăng dần CCU hướng tới mục tiêu 10.000 người dùng đồng thời
export const options = {
  stages: [
    { duration: '1m', target: 500 },    // Khởi động với 500 CCU
    { duration: '2m', target: 2000 },   // Nâng tải lên 2.000 CCU
    { duration: '2m', target: 5000 },   // Nâng tải lên 5.000 CCU
    { duration: '3m', target: 10000 },  // Đạt đỉnh 10.000 CCU đồng thời
    { duration: '1m', target: 0 },      // Hạ tải về 0
  ],
  thresholds: {
    // Tỉ lệ lỗi tổng thể cho phép dưới 5% khi ở tải cực hạn
    http_req_failed: ['rate<0.05'],
    // 95% số request hoàn thành dưới 3 giây
    http_req_duration: ['p(95)<3000'],
  },
};

const BASE_URL = 'http://localhost:8080/v1';

// Secret key lấy từ JWT_SECRET trong file .env của dự án
const JWT_SECRET_BASE64 = 'Lx34WcqNytFOxdBa0Nn5nMJIIyxHyaChhPtwMZnTgeE=';
const JWT_SECRET_BIN = encoding.b64decode(JWT_SECRET_BASE64, 'std', 's');

// ID của Nhóm Siêu Lớn (100 thành viên, 1 triệu giao dịch)
const SUPER_GROUP_ID = 'c0000000-0000-0000-0000-000000000001';

// Hàm tự ký JWT Access Token hợp lệ theo chuẩn HS256 của Spring Boot JwtService
function createAccessToken(userId) {
  const headerObj = { alg: 'HS256', typ: 'JWT' };
  const headerEncoded = encoding.b64encode(JSON.stringify(headerObj), 'rawurl');

  const nowEpoch = Math.floor(Date.now() / 1000);
  const payloadObj = {
    sub: userId,
    plan: 'free',
    authorities: ['ROLE_USER'],
    roles_level_top: 10,
    iat: nowEpoch,
    exp: nowEpoch + 86400, // Token có hiệu lực 24 giờ
  };
  const payloadEncoded = encoding.b64encode(JSON.stringify(payloadObj), 'rawurl');

  const unsignedToken = `${headerEncoded}.${payloadEncoded}`;
  const signatureBin = crypto.hmac('sha256', JWT_SECRET_BIN, unsignedToken, 'binary');
  const signature = encoding.b64encode(signatureBin, 'rawurl');

  return `${unsignedToken}.${signature}`;
}

export default function () {
  // Mỗi CCU có một UUID cố định tương ứng theo quy luật đã seed trong PostgreSQL
  const paddedId = String(__VU).padStart(12, '0');
  const userId = `00000000-0000-0000-0000-${paddedId}`;

  // Tự ký token hợp lệ của chính mình mà không cần gọi API login
  const token = createAccessToken(userId);

  const authHeader = {
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  };

  // Người dùng lấy danh sách nhóm có sẵn của mình từ database
  const listGroupsRes = http.get(`${BASE_URL}/groups`, authHeader);

  const isListSuccess = check(listGroupsRes, {
    'Lấy danh sách nhóm thành công': (r) => r.status === 200,
  });

  if (!isListSuccess) {
    sleep(1);
    return;
  }

  // Trích xuất nhóm đầu tiên của user hoặc dùng nhóm siêu lớn nếu thuộc 100 user đầu
  const groups = listGroupsRes.json('data');
  let targetGroupId = (groups && groups.length > 0) ? groups[0].id : null;

  if (__VU <= 100) {
    targetGroupId = SUPER_GROUP_ID;
  }

  if (!targetGroupId) {
    sleep(1);
    return;
  }

  // Thời gian dừng đọc màn hình trước khi thực hiện giao dịch
  sleep(1);

  // Người dùng nộp tiền vào quỹ nhóm
  const createTxPayload = JSON.stringify({
    type: 'CONTRIBUTION',
    money_source: 'PERSONAL',
    amount: 100000,
    transactor_id: userId,
    note: 'Nộp quỹ kiểm thử tải 10k CCU',
  });

  const createTxRes = http.post(
    `${BASE_URL}/groups/${targetGroupId}/transactions`,
    createTxPayload,
    authHeader
  );

  check(createTxRes, {
    'Tạo giao dịch thành công': (r) => r.status === 201 || r.status === 200,
  });

  // Thời gian dừng trước khi xem danh sách lịch sử giao dịch
  sleep(1);

  // Người dùng xem danh sách giao dịch trong nhóm
  const listTxRes = http.get(
    `${BASE_URL}/groups/${targetGroupId}/transactions?page=1&page_size=20`,
    authHeader
  );

  check(listTxRes, {
    'Xem danh sách giao dịch thành công': (r) => r.status === 200,
  });

  // Thời gian dừng trước khi xem báo cáo số dư
  sleep(1);

  // Người dùng xem tổng tiền quỹ và số dư công nợ của các thành viên
  const balancesRes = http.get(
    `${BASE_URL}/groups/${targetGroupId}/balances`,
    authHeader
  );

  check(balancesRes, {
    'Xem tổng tiền và công nợ nhóm thành công': (r) => r.status === 200,
  });

  // Các CCU thuộc nhóm siêu lớn thực hiện truy vấn bảng 1 triệu dòng
  if (__VU <= 100) {
    sleep(1);
    const superGroupTxRes = http.get(
      `${BASE_URL}/groups/${SUPER_GROUP_ID}/transactions?page=1&page_size=20`,
      authHeader
    );
    check(superGroupTxRes, {
      'Query Nhóm Siêu Lớn 1 triệu dòng thành công': (r) => r.status === 200,
    });
  }

  // Nghỉ giữa các chu kỳ của người dùng ảo
  sleep(2);
}
