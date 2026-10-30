# CShield Spring Boot demo server

Demo backend Spring Boot tích hợp **CShield SDK 2.0.0**. Server verify từng request có chữ ký thiết bị (header `cs-proof`) do app mobile tích hợp CShield gửi lên, rồi ký response để app kiểm lại.

- Bản deploy: `https://demo-spring-server.onrender.com` (app Android/iOS sample gọi vào đây).
- Spring Boot 3.5.16, Java 21, Spring MVC.
- SDK trong `libs/cshield-sdk-2.0.0.jar` là bản **UAT**: lấy public key realm từ License server `https://c-shield-license.cmccs.vn`.

---

## 1. API của demo

| Method | Path | CShield | Mô tả |
|---|---|---|---|
| `POST` | `/verify-otp` | Bảo vệ | Body `{"otp":"123456"}`. OTP đúng `123456` → `OK`; sai → `INVALID_OTP`; rỗng → `INVALID_REQUEST` |
| `GET` | `/verify-otp/hello` | Bảo vệ | Trả `Hello Spring Boot Java 👋` |
| `GET` | `/non-authorize/hello` | Mở | Không cần `cs-proof`, response không ký |

Route được bảo vệ: thiếu hoặc sai `cs-proof` thì bị chặn trước khi vào controller; response (kể cả lỗi) có header `cs-timestamp` và `cs-signature`.

---

## 2. Tích hợp SDK: chỉ cần JAR + cấu hình

Demo **không có dòng code Java nào gọi SDK**. SDK tự đăng ký servlet filter qua auto-configuration của Spring Boot.

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
  server-private-key: file:${CSHIELD_PRIVATE_KEY_FILE}
  request-timeout-seconds: 30
  min-trust-level: ${CSHIELD_MIN_TRUST_LEVEL:L1}
  realm:
    customer-code: ${CSHIELD_CUSTOMER_CODE:}
    application-id: ${CSHIELD_APPLICATION_ID:}
    environment: ${CSHIELD_ENVIRONMENT:}
  routes:
    - path: /verify-otp
      methods: [POST]
    - path: /verify-otp/hello
      methods: [GET]
```

| Property | Mặc định | Ý nghĩa |
|---|---|---|
| `server-private-key` | bắt buộc | Private key RSA ký response (`file:` hoặc `classpath:`), PEM PKCS#8 hoặc PKCS#1 |
| `realm.customer-code`, `realm.application-id`, `realm.environment` | bắt buộc | Realm của license app (`environment`: `DEV`/`STAGING`/`PROD`) |
| `routes[].path`, `routes[].methods` | | Ant pattern; không khai `methods` = mọi method; `GET` gồm `HEAD`. Route không khai thì đi thẳng, không ký |
| `request-timeout-seconds` | `30` | Lệch tối đa giữa giờ server và `timestamp` trong `cs-proof` |
| `min-trust-level` | `L1` | Mức tin cậy thiết bị tối thiểu (`L1`…`L5`); có thể đặt riêng từng route |
| `max-trust-age-seconds` | tắt | Độ tươi tối đa của lần đánh giá trust (claim `fresh`) |
| `clock-tolerance-seconds` | `30` | Dung sai đồng hồ khi kiểm hạn access token |

Thiếu trường realm, `environment` sai, hoặc thiếu `server-private-key` → app dừng ngay lúc khởi động. Dấu `:` trong `${CSHIELD_CUSTOMER_CODE:}` cho biến thiếu thành chuỗi rỗng, để SDK báo lỗi realm; không có `:` thì Spring giữ nguyên chuỗi `${...}` làm realm và app vẫn chạy nhưng mọi request bị 401.

---

## 3. Biến môi trường

| Biến | Giá trị cho app sample |
|---|---|
| `CSHIELD_PRIVATE_KEY_FILE` | Đường dẫn file private key (trên Render: `/etc/secrets/server_private.pem`) |
| `CSHIELD_CUSTOMER_CODE` | `CMCCS` |
| `CSHIELD_APPLICATION_ID` | `cshield-embedded-sample` |
| `CSHIELD_ENVIRONMENT` | `DEV` |
| `CSHIELD_MIN_TRUST_LEVEL` | tuỳ chọn, mặc định `L1` |
| `PORT` | tuỳ chọn, mặc định `8080` |

Private key phải là cặp với `server_public.pem` nhúng trong app mobile; sai cặp thì app báo `Invalid response's cs-signature`. **Không commit private key vào repo này** (repo public).

---

## 4. Chạy và test

```bash
./gradlew test
```

Test `contextLoads` tự sinh key RSA tạm, không cần biến môi trường.

Chạy local:

```bash
export CSHIELD_PRIVATE_KEY_FILE=/đường/dẫn/server_private.pem
export CSHIELD_CUSTOMER_CODE=CMCCS CSHIELD_APPLICATION_ID=cshield-embedded-sample CSHIELD_ENVIRONMENT=DEV
./gradlew bootRun
```

Kiểm tra nhanh:

```bash
curl -i http://localhost:8080/non-authorize/hello
curl -i -X POST -H 'Content-Type: application/json' -d '{"otp":"123456"}' http://localhost:8080/verify-otp
```

Kết quả đúng: lệnh 1 trả 200, không có `cs-signature`; lệnh 2 trả `400 CSHIELD_MISSING_SIGNATURE_HEADER`, có `cs-signature`.

Request có `cs-proof` hợp lệ cần access token do License server cấp: dùng app mobile tích hợp CShield (bản trỏ License server UAT), hoặc script giả lập License server + thiết bị của đội CShield (cần build SDK trỏ License server giả rồi chạy `./gradlew bootRun -PcshieldSdkJar=<JAR đó>`).

---

## 5. Deploy (Render)

`Dockerfile` build bằng `./gradlew clean bootJar` trên `eclipse-temurin:21-jdk`, chạy trên `eclipse-temurin:21-jre`. Cấu hình trên dashboard Render **trước khi push**:

1. **Secret Files** → thêm `server_private.pem` (nội dung PEM đầy đủ, gồm dòng `BEGIN`/`END`).
2. **Environment** → các biến ở mục 3, với `CSHIELD_PRIVATE_KEY_FILE=/etc/secrets/server_private.pem`.
3. Health check (nếu có): `/non-authorize/hello`.

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
| `ServerPrivateKeyProvider`, env `CSHIELD_SERVER_PRIVATE_KEY` (base64) | `c-shield.server-private-key` (file PEM) hoặc bean `PrivateKeyProvider` |
| Tự override `RequestVerifier`/`ResponseSigner` để bỏ qua route công khai | Khai route cần bảo vệ trong `c-shield.routes`; route khác đi thẳng |
| Không có realm | Bắt buộc `c-shield.realm.*` |
| Spring Boot 4.0.1 + Kotlin + JPA/H2 | Spring Boot 3.5.16, Java 21, chỉ `spring-boot-starter-web` |
