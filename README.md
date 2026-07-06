# Hướng dẫn tích hợp CShield SDK vào Spring Boot Server

Tài liệu này cung cấp hướng dẫn từng bước để tích hợp **CShield SDK** vào một hệ thống backend chạy Spring Boot (Java/Kotlin). CShield SDK giúp bảo vệ API bằng cơ chế ký số (Digital Signature), đảm bảo tính toàn vẹn của request từ client và response từ server.

---

## 1. Yêu cầu hệ thống

- **Java**: 17 hoặc mới hơn.
- **Framework**: Spring Boot 3.x.
- **Build tool**: Gradle (hoặc Maven tương đương).
- **File SDK**: File thư viện `cshield-sdk.jar` do CShield cung cấp.

---

## 2. Cài đặt CShield SDK

### 2.1 Thêm thư viện vào dự án
Tạo một thư mục `libs` ở thư mục gốc của project và đặt file `cshield-sdk.jar` vào đó.

Cấu trúc dự kiến:
```text
your-project/
├── libs/
│   └── cshield-sdk.jar
├── src/
├── build.gradle.kts
└── ...
```

### 2.2 Cập nhật `build.gradle.kts`
Thêm khai báo trỏ đến file `.jar` cục bộ trong block `dependencies`. Bạn cũng cần thêm các package bắt buộc để xử lý JSON và mã hóa nếu project chưa có.

```kotlin
dependencies {
    // ... các dependency khác của dự án

    // Import CShield SDK
    implementation(files("libs/cshield-sdk.jar"))

    // (Tùy chọn) Jackson dùng để deserialize nếu cần
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}
```

---

## 3. Khai báo Key Provider

CShield SDK sử dụng **Client Public Key** (để xác thực chữ ký của request) và **Server Private Key** (để ký response). Bạn cần phải override (triển khai) 2 interface `ClientPublicKeyProvider` và `ServerPrivateKeyProvider`.

Tạo một class hoặc hai class riêng biệt để load Key. Ví dụ dưới đây sử dụng biến môi trường (Environment Variables) để bảo mật:

### Đọc Client Public Key
```java
import com.cshield.sdk.api.key_provider.client_public_key.ClientPublicKeyProvider;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class EnvPublicKeyProvider implements ClientPublicKeyProvider {
    private final String envName;

    public EnvPublicKeyProvider(String envName) {
        this.envName = envName;
    }

    @Override
    public PublicKey load() {
        String base64 = System.getenv(envName);
        if (base64 == null || base64.trim().isEmpty()) {
            throw new IllegalStateException("Missing env " + envName);
        }
        
        try {
            base64 = base64.trim();
            if (base64.startsWith("\"") && base64.endsWith("\"")) {
                base64 = base64.substring(1, base64.length() - 1);
            }
            byte[] decoded = Base64.getDecoder().decode(base64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid base64 in env " + envName, e);
        }
    }
}
```

### Đọc Server Private Key
```java
import com.cshield.sdk.api.key_provider.server_private_key.ServerPrivateKeyProvider;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class EnvPrivateKeyProvider implements ServerPrivateKeyProvider {
    private final String envName;

    public EnvPrivateKeyProvider(String envName) {
        this.envName = envName;
    }

    @Override
    public PrivateKey load() {
        String base64 = System.getenv(envName);
        if (base64 == null || base64.trim().isEmpty()) {
            throw new IllegalStateException("Missing env " + envName);
        }
        
        try {
            base64 = base64.trim();
            if (base64.startsWith("\"") && base64.endsWith("\"")) {
                base64 = base64.substring(1, base64.length() - 1);
            }
            byte[] decoded = Base64.getDecoder().decode(base64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid base64 in env " + envName, e);
        }
    }
}
```

---

## 4. Khởi tạo và Cấu hình SDK Components

Tạo một class có annotation `@Configuration` để đăng ký các Spring Beans cho SDK. Tại đây, bạn cũng sẽ ghi đè (override) logic xác minh `RequestVerifier` và `ResponseSigner` nhằm thêm quy tắc loại trừ các API công khai (không cần xác thực chữ ký) và kiểm tra Timeout nhằm chống Replay Attack.

```java
import com.cshield.sdk.api.exception.MissingSignatureHeaderException;
import com.cshield.sdk.api.exception.TimeoutRequestException;
import com.cshield.sdk.api.key_provider.client_public_key.ClientPublicKeyProvider;
import com.cshield.sdk.api.key_provider.server_private_key.ServerPrivateKeyProvider;
import com.cshield.sdk.api.request.RequestVerifier;
import com.cshield.sdk.api.response.ResponseSigner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CShieldOverrideConfig {

    // Khởi tạo Bean cung cấp Public Key của Client
    @Bean
    public ClientPublicKeyProvider publicKeyProvider() {
        return new EnvPublicKeyProvider("CSHIELD_CLIENT_PUBLIC_KEY");
    }

    // Khởi tạo Bean cung cấp Private Key của Server
    @Bean
    public ServerPrivateKeyProvider privateKeyProvider() {
        return new EnvPrivateKeyProvider("CSHIELD_SERVER_PRIVATE_KEY");
    }

    // Ghi đè bộ xác thực Request
    @Bean
    public RequestVerifier myVerifier(ClientPublicKeyProvider provider) {
        return new RequestVerifier(provider.load()) {
            @Override
            public void verify(
                    String method,
                    String path,
                    String timestamp,
                    String bodyHash,
                    String signature
            ) {
                // 1. Loại trừ (Bypass) các path không cần yêu cầu chữ ký
                if (path != null && path.startsWith("/non-authorize")) {
                    return; 
                }

                // 2. Bắt buộc có signature và timestamp cho các path còn lại
                if (timestamp == null || signature == null) {
                    throw new MissingSignatureHeaderException("cs-timestamp / cs-signature");
                }

                // 3. Kiểm tra tính hợp lệ của timestamp (Ví dụ: Giới hạn độ trễ 30 giây)
                long diff = System.currentTimeMillis() / 1000 - Long.parseLong(timestamp);
                if (diff > 30) { 
                    throw new TimeoutRequestException();
                }

                // 4. Xác thực chữ ký bằng hàm gốc của SDK
                String payload = method + "." + path + "." + timestamp + "." + bodyHash;
                super.verifier.verify(payload, signature);
            }
        };
    }

    // Ghi đè bộ ký Response
    @Bean
    public ResponseSigner mySigner(ServerPrivateKeyProvider provider) {
        return new ResponseSigner(provider.load()) {
            @Override
            public String sign(
                    int status,
                    String path,
                    String timestamp,
                    String bodyHash
            ) {
                // 1. Loại trừ không ký lên response của những API công khai
                if (path != null && path.startsWith("/non-authorize")) {
                    return ""; // Trả về chuỗi rỗng để không gắn Header `cs-signature`
                }
                
                // 2. Ký response bằng hàm gốc của SDK
                String payload = status + "." + path + "." + timestamp + "." + bodyHash;
                return super.signer.sign(payload);
            }
        };
    }
}
```

---

## 5. Phát triển các Endpoint (API)

CShield SDK cung cấp một bộ lọc toàn cầu cho toàn bộ các request vào server. Tuy nhiên, dựa vào `RequestVerifier` mà chúng ta cấu hình ở bước 4, CShield SDK sẽ biết API nào cần kiểm tra và API nào không.

### API Bảo mật (Yêu cầu xác thực chữ ký)
Dành cho những API chứa dữ liệu quan trọng, cần chữ ký số từ Client.

```java
@RestController
@RequestMapping("/verify-otp")
public class VerifyOtpController {

    @PostMapping
    public ApiResponse verify(@RequestBody VerifyOtpRequest req) {
        // ... xử lý logic nghiệp vụ
        // Việc kiểm tra `cs-signature` và `cs-timestamp` đã tự động hoàn thành trước khi tới đây.
        return new ApiResponse(true, "OK", "Xác thực OTP thành công");
    }
}
```

### API Công khai (Không yêu cầu xác thực chữ ký)
Do chúng ta đã định nghĩa trong `CShieldOverrideConfig` rằng mọi `path.startsWith("/non-authorize")` đều bị bỏ qua bởi `RequestVerifier`, bạn có thể tạo các public endpoint đơn giản như sau:

```java
@RestController
@RequestMapping("/non-authorize")
public class NonAuthorizeController {

    @GetMapping("/hello")
    public String hello() {
        return "Xin chào! Đây là public API.";
    }
}
```

---

## 6. Chạy và Kiểm thử ứng dụng

Khai báo biến môi trường (Lưu ý: bỏ các định dạng `-----BEGIN...-----`):
```bash
export CSHIELD_CLIENT_PUBLIC_KEY="YOUR_BASE64_PUBLIC_KEY"
export CSHIELD_SERVER_PRIVATE_KEY="YOUR_BASE64_PRIVATE_KEY"
```

Chạy server bằng Gradle:
```bash
./gradlew bootRun
```

### Format của một Secure Request hợp lệ từ Client:
- **Headers**: 
  - `cs-timestamp`: Unix timestamp (bằng giây).
  - `cs-signature`: Chữ ký số (RSA-SHA256) được tạo từ chuỗi `[METHOD].[PATH].[TIMESTAMP].[SHA256(BODY)]` ký bằng Client Private Key.
- **Body**: Phải khớp với body hash khi tiến hành mã hóa chữ ký.

Server sẽ tự động bắt lấy Exception khi sai lệch hoặc thiếu chữ ký và từ chối xử lý request bằng các error code cấu hình sẵn của SDK (Ví dụ: 401 Unauthorized hoặc 403 Forbidden).