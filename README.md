# CShield Spring Boot demo server

Demo backend Spring Boot tích hợp **CShield SDK 2.0.0**. Server verify từng request có chữ ký thiết bị (header `cs-proof`) do app mobile tích hợp CShield gửi lên, rồi ký response để app kiểm lại.

- Bản deploy: `https://demo-spring-server.onrender.com` (app Android sample gọi vào đây).
- API và cấu hình giống demo Node.js (`demo_nodejs_server`): cùng endpoint, cùng biến môi trường, cùng realm mặc định.
- Spring Boot 3.5.16, Java 21, Spring MVC.
- SDK trong `libs/cshield-sdk-2.0.0.jar` là bản **UAT**: lấy public key realm từ License server `https://c-shield-license.cmccs.vn`.

---

## 1. API của demo

| Method | Path | CShield | Mô tả |
|---|---|---|---|
| `GET` | `/test` | Mở | `{"success":true,"message":"Hello World"}`; không cần `cs-proof`, response không ký |
| `POST` | `/verify-otp` | Bảo vệ, trust ≥ `L1` | Body `{"otp":"123456"}`. OTP đúng → `OK` (`Xac thuc OTP thanh cong`); sai hoặc thiếu → `INVALID_OTP` |
| `POST` | `/payment` | Bảo vệ, trust ≥ `L4` | `Payment authorized (trust >= L4)`; thiết bị dưới L4 → `403 CSHIELD_DEVICE_NOT_ALLOWED` (app gọi `ensureTrust(L4)` trước) |
| `POST` | `/transfer` | Bảo vệ, trust ≥ `L4` và tươi trong 300s | `Transfer authorized (fresh L4)`; trust cũ hoặc token không có claim `fresh` → `403 CSHIELD_TRUST_NOT_FRESH` (app gọi `ensureTrust(L4, requireFresh=true)`) |

Route được bảo vệ: thiếu hoặc sai `cs-proof` thì bị chặn trước khi vào controller; response (kể cả lỗi) có header `cs-timestamp` và `cs-signature`.

---

## 2. Tích hợp SDK: JAR + cấu hình + 1 bean key

SDK tự đăng ký servlet filter qua auto-configuration của Spring Boot. Code demo chỉ có thêm một bean đọc private key từ biến môi trường (mục 2.3); verify request và ký response không cần code.

### 2.1 `build.gradle.kts`

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation(files(findProperty("cshieldSdkJar") ?: "libs/cshield-sdk-2.0.0.jar"))
    implementation("org.bouncycastle:bcprov-jdk18on:1.78")
}
```

- JAR SDK **không gói kèm thư viện phụ thuộc**. Thiếu `bcprov-jdk18on` thì app vẫn khởi động nhưng **mọi route được bảo vệ trả 500**.
- `-PcshieldSdkJar=<đường dẫn>` cho phép chạy thử bằng JAR khác (ví dụ JAR trỏ License server giả) mà không phải thay file trong `libs/`.
- Yêu cầu: Spring Boot 3.x, Java 17+, Spring MVC. Không chạy trên Spring Boot 4 hoặc WebFlux.

### 2.2 `application.yml`

```yaml
server:
  tomcat:
    relaxed-query-chars: "[,]"

c-shield:
  request-timeout-seconds: 60
  min-trust-level: ${CSHIELD_MIN_TRUST_LEVEL:L1}
  realm:
    customer-code: ${CSHIELD_CUSTOMER_CODE:CMCCS}
    application-id: ${CSHIELD_APPLICATION_ID:cshield-embedded-sample}
    environment: DEV
  routes:
    - path: /verify-otp
      methods: [POST]
    - path: /payment
      methods: [POST]
      min-trust-level: ${CSHIELD_SENSITIVE_MIN_TRUST:L4}
    - path: /transfer
      methods: [POST]
      min-trust-level: ${CSHIELD_SENSITIVE_MIN_TRUST:L4}
      max-trust-age-seconds: ${CSHIELD_SENSITIVE_MAX_AGE:300}
```

| Property | Mặc định | Ý nghĩa |
|---|---|---|
| `server-private-key` | | Private key RSA ký response dạng `Resource` (`file:`/`classpath:`). Demo **không dùng**: key lấy từ bean `PrivateKeyProvider` (mục 2.3) |
| `realm.customer-code`, `realm.application-id`, `realm.environment` | bắt buộc | Realm của license app. Demo mặc định `CMCCS` / `cshield-embedded-sample` / `DEV` (realm `cmccs:cshield-embedded-sample:dev`) |
| `routes[].path`, `routes[].methods` | | Ant pattern; không khai `methods` = mọi method; `GET` gồm `HEAD`. Route không khai thì đi thẳng, không ký |
| `request-timeout-seconds` | `30` (demo đặt `60`) | Lệch tối đa giữa giờ server và `timestamp` trong `cs-proof` |
| `min-trust-level` | `L1` | Mức tin cậy thiết bị tối thiểu (`L1`…`L5`); có thể đặt riêng từng route |
| `max-trust-age-seconds` | tắt | Độ tươi tối đa của lần đánh giá trust (claim `fresh`) |
| `clock-tolerance-seconds` | `30` | Dung sai đồng hồ khi kiểm hạn access token |

Realm thiếu trường hoặc `environment` sai → app dừng ngay lúc khởi động. Khi tự khai biến realm không có giá trị mặc định, viết `${TEN_BIEN:}` (có dấu `:`): thiếu biến thì thành chuỗi rỗng và SDK báo lỗi; không có `:` thì Spring giữ nguyên chuỗi `${TEN_BIEN}` làm realm, app vẫn chạy nhưng mọi request bị 401.

### 2.3 Private key từ biến môi trường

`config/CShieldKeyConfig.java` khai bean `PrivateKeyProvider`: đọc `CSHIELD_PRIVATE_KEY` (nội dung file PEM đã base64), giải mã thành PEM rồi đưa cho SDK. Thiếu biến → `ConfigurationException: Missing env var: CSHIELD_PRIVATE_KEY`, app dừng lúc khởi động. Có bean này thì SDK bỏ qua `c-shield.server-private-key`.

---

## 3. Biến môi trường

| Biến | Bắt buộc | Mặc định |
|---|---|---|
| `CSHIELD_PRIVATE_KEY` | **Có** | Base64 của file PEM private key: `base64 -w 0 private.pem` (macOS: `base64 -i private.pem`) |
| `CSHIELD_CUSTOMER_CODE` | Không | `CMCCS` |
| `CSHIELD_APPLICATION_ID` | Không | `cshield-embedded-sample` |
| `CSHIELD_MIN_TRUST_LEVEL` | Không | `L1` (áp cho `/verify-otp`) |
| `CSHIELD_SENSITIVE_MIN_TRUST` | Không | `L4` (áp cho `/payment`, `/transfer`) |
| `CSHIELD_SENSITIVE_MAX_AGE` | Không | `300` giây (độ tươi trust của `/transfer`) |
| `PORT` | Không | `8080` |

Environment của realm cố định `DEV` trong `application.yml`, giống demo Node.

Private key phải là cặp với `server_public.pem` nhúng trong app mobile; sai cặp thì app báo `Invalid response's cs-signature`. **Không commit private key vào repo này** (repo public).

---

## 4. Chạy và test

```bash
./gradlew test
```

Test `contextLoads` tự sinh key RSA tạm và đặt `CSHIELD_PRIVATE_KEY`, không cần biến môi trường.

Chạy local:

```bash
export CSHIELD_PRIVATE_KEY=$(base64 -i /đường/dẫn/server_private.pem)
./gradlew bootRun
```

Kiểm tra nhanh:

```bash
curl -i http://localhost:8080/test
curl -i -X POST -H 'Content-Type: application/json' -d '{"otp":"123456"}' http://localhost:8080/verify-otp
```

Kết quả đúng: lệnh 1 trả 200, không có `cs-signature`; lệnh 2 trả `400 CSHIELD_MISSING_SIGNATURE_HEADER`, có `cs-signature`.

Request có `cs-proof` hợp lệ cần access token do License server cấp: dùng app mobile tích hợp CShield (bản trỏ License server UAT), hoặc script giả lập License server + thiết bị của đội CShield (cần build SDK trỏ License server giả rồi chạy `./gradlew bootRun -PcshieldSdkJar=<JAR đó>`).

---

## 5. Deploy (Render)

`Dockerfile` build bằng `./gradlew clean bootJar` trên `eclipse-temurin:21-jdk`, chạy trên `eclipse-temurin:21-jre`. Cấu hình trên dashboard Render **trước khi push**:

1. **Environment** → `CSHIELD_PRIVATE_KEY` (cùng giá trị với service demo Node nếu dùng chung key). Các biến khác tuỳ chọn.
2. Health check (nếu có): `/test`.

Server phải gọi ra được `https://c-shield-license.cmccs.vn` để tải JWKS; không gọi được thì route bảo vệ trả `502 CSHIELD_DEVICE_KEY_UNAVAILABLE`. Service chạy nhiều instance thì cần thay bean `RequestIdStore` mặc định (giữ `requestId` trong RAM) bằng bản dùng chung (ví dụ Redis) để chống replay giữa các instance.

---

## 6. Mã lỗi

Body lỗi: `{"success":false,"code":"...","message":"..."}`.

| HTTP | `code` | Khi nào | Có ký |
|---|---|---|---|
| 400 | `CSHIELD_MISSING_SIGNATURE_HEADER` | Thiếu `cs-proof` | Có |
| 400 | `CSHIELD_INVALID_PROOF` | `cs-proof` không phải base64/JSON hoặc thiếu trường | Có |
| 400 | `CSHIELD_MALFORMED_REQUEST` | Path/query hỏng | Có |
| 401 | `CSHIELD_INVALID_ACCESS_TOKEN` | Token sai, hết hạn, sai realm/môi trường | Có |
| 401 | `CSHIELD_SIGNATURE_MISMATCH` | Chữ ký thiết bị sai (body/path/query bị sửa) | Có |
| 401 | `CSHIELD_STALE_REQUEST` | `timestamp` lệch quá `request-timeout-seconds` | Có |
| 401 | `CSHIELD_REPLAY_DETECTED` | `requestId` đã dùng | Có |
| 403 | `CSHIELD_DEVICE_NOT_ALLOWED` | Mức tin cậy thiết bị thấp hơn yêu cầu | Có |
| 403 | `CSHIELD_TRUST_NOT_FRESH` | Trust cũ hơn `max-trust-age-seconds` | Có |
| 500 | `CSHIELD_MISCONFIGURED` | Sai cấu hình phía server (key hỏng…) | Không |
| 500 | `CSHIELD_RESPONSE_SIGN_FAILED` | Không ký được response | Không |
| 502 | `CSHIELD_DEVICE_KEY_UNAVAILABLE` | Không tải được JWKS từ License server | Không |

---

## 7. Nâng cấp từ SDK v1

| v1 | v2 |
|---|---|
| `libs/cshield-sdk.jar` | `libs/cshield-sdk-2.0.0.jar` + `bcprov-jdk18on` |
| Header `cs-timestamp` + `cs-signature` ký bằng client private key (RSA) | Header `cs-proof`: access token ES256 + chữ ký device key (EC P-256) |
| `ClientPublicKeyProvider`, env `CSHIELD_CLIENT_PUBLIC_KEY` | **Bỏ**: public key thiết bị nằm trong access token, public key realm lấy từ JWKS |
| `ServerPrivateKeyProvider`, env `CSHIELD_SERVER_PRIVATE_KEY` (base64) | Bean `PrivateKeyProvider` (demo: env `CSHIELD_PRIVATE_KEY`, base64 PEM) hoặc `c-shield.server-private-key` |
| Tự override `RequestVerifier`/`ResponseSigner` để bỏ qua route công khai | Khai route cần bảo vệ trong `c-shield.routes`; route khác đi thẳng |
| Không có realm | Bắt buộc `c-shield.realm.*` (demo có sẵn mặc định) |
| `/non-authorize/hello`, `/verify-otp/hello` | `/test`, `/payment`, `/transfer` như demo Node |
| Spring Boot 4.0.1 + Kotlin + JPA/H2 | Spring Boot 3.5.16, Java 21, chỉ `spring-boot-starter-web` |
