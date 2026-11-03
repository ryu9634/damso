package com.damso.match.global.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 개인정보(휴대폰번호·실명·연락처) 컬럼 암호화.
 *
 * <p>DB 파일이나 덤프가 새어 나가도 값이 바로 읽히지 않게 AES-256-GCM 으로 감싸서 저장한다.
 * 읽을 때는 {@link com.damso.match.global.security.EncryptedStringConverter} 가 자동으로 풀어 주므로
 * 서비스·화면 코드는 평문만 다룬다.
 *
 * <p><b>같은 평문은 항상 같은 암호문이 된다(결정적 암호화).</b>
 * IV 를 무작위로 뽑지 않고 평문의 HMAC 으로 만들기 때문인데, 이렇게 해야
 * "이 번호로 가입한 회원이 있나" 같은 등호 조회가 암호화 뒤에도 그대로 동작한다.
 * 대신 "두 행의 번호가 같다"는 사실은 암호문만 봐도 드러난다. 우리 용도에서는 감수한다.
 *
 * <p>키는 두 곳 중 하나에서 온다.
 * <ol>
 *   <li>환경변수 {@code DAMSO_DATA_KEY} (base64, 32바이트)</li>
 *   <li>없으면 {@code damso.data-key-file} 경로의 파일. 없으면 새로 만들어 저장한다(600).</li>
 * </ol>
 * 키를 잃으면 암호화된 값을 되돌릴 수 없다. DB 를 백업할 때 키 파일도 함께 보관한다.
 *
 * <p>비밀번호는 여기 대상이 아니다. 비밀번호는 되돌릴 필요가 없으므로 BCrypt 해시로만 저장한다.
 */
@Slf4j
@Component
public class DataCrypto {

    /** 저장 형식 표시. 이 접두어가 없는 값은 암호화 전 평문으로 본다. */
    public static final String PREFIX = "enc1:";

    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    /** JPA 컨버터는 Hibernate 가 만들기 때문에 주입 대신 여기서 꺼내 쓴다. */
    private static volatile DataCrypto instance;

    @Value("${DAMSO_DATA_KEY:}")
    private String keyFromEnv;

    @Value("${damso.data-key-file:./data/damso.key}")
    private String keyFile;

    private SecretKeySpec encKey;
    private SecretKeySpec macKey;

    public static DataCrypto get() {
        DataCrypto c = instance;
        if (c == null) throw new IllegalStateException("DataCrypto 가 아직 준비되지 않았습니다.");
        return c;
    }

    @PostConstruct
    void init() throws IOException {
        byte[] master = loadOrCreateKey();
        // 암호화 키와 IV 계산용 키를 나눠 쓴다. 한 키를 두 용도로 쓰지 않기 위해서다.
        encKey = new SecretKeySpec(hmac(master, "damso:enc"), "AES");
        macKey = new SecretKeySpec(hmac(master, "damso:iv"), "HmacSHA256");
        Arrays.fill(master, (byte) 0);
        instance = this;
    }

    private byte[] loadOrCreateKey() throws IOException {
        if (keyFromEnv != null && !keyFromEnv.isBlank()) {
            byte[] k = Base64.getDecoder().decode(keyFromEnv.trim());
            if (k.length != KEY_BYTES) {
                throw new IllegalStateException("DAMSO_DATA_KEY 는 base64 로 적은 32바이트여야 합니다. (지금 " + k.length + "바이트)");
            }
            log.info("개인정보 암호화 키 — 환경변수 DAMSO_DATA_KEY 사용");
            return k;
        }

        Path path = Path.of(keyFile).toAbsolutePath();
        if (Files.exists(path)) {
            byte[] k = Base64.getDecoder().decode(Files.readString(path).trim());
            if (k.length != KEY_BYTES) {
                throw new IllegalStateException("키 파일이 손상됐습니다: " + path);
            }
            log.info("개인정보 암호화 키 — 파일 사용: {}", path);
            return k;
        }

        byte[] k = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(k);
        Files.createDirectories(path.getParent());
        Files.writeString(path, Base64.getEncoder().encodeToString(k));
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // Windows 등 POSIX 권한이 없는 파일시스템
        }
        log.warn("개인정보 암호화 키를 새로 만들었습니다: {} — 이 파일을 잃으면 저장된 개인정보를 읽을 수 없습니다. 백업하세요.", path);
        return k;
    }

    // ─── 암/복호화 ─────────────────────────────────────

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /** 평문 → "enc1:base64(iv‖암호문‖태그)". null/빈 값과 이미 암호화된 값은 그대로 돌려준다. */
    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty() || isEncrypted(plain)) return plain;
        try {
            byte[] data = plain.getBytes(StandardCharsets.UTF_8);
            byte[] iv = Arrays.copyOf(hmacBytes(macKey, data), IV_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(data);
            byte[] out = new byte[IV_BYTES + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_BYTES);
            System.arraycopy(ct, 0, out, IV_BYTES, ct.length);
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("암호화에 실패했습니다.", e);
        }
    }

    /** 암호문 → 평문. 접두어가 없으면 암호화 전 데이터로 보고 그대로 돌려준다. */
    public String decrypt(String stored) {
        if (!isEncrypted(stored)) return stored;
        try {
            byte[] in = Base64.getUrlDecoder().decode(stored.substring(PREFIX.length()));
            if (in.length <= IV_BYTES) throw new IllegalStateException("암호문이 너무 짧습니다.");
            byte[] iv = Arrays.copyOf(in, IV_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // 키가 바뀌었거나 값이 깨진 경우. 앱이 통째로 죽는 것보다 어느 값이 문제인지 남기는 편이 낫다.
            throw new IllegalStateException("개인정보를 복호화할 수 없습니다. 암호화 키가 바뀌지 않았는지 확인하세요.", e);
        }
    }

    // ─── 내부 ───────────────────────────────────────────

    private static byte[] hmac(byte[] key, String label) {
        return hmacBytes(new SecretKeySpec(key, "HmacSHA256"), label.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmacBytes(SecretKeySpec key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
