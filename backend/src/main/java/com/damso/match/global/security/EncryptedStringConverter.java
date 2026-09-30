package com.damso.match.global.security;

import com.damso.match.global.schema.PiiEncryptionMigration;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * 개인정보 컬럼을 DB 에 넣을 때 암호화하고 꺼낼 때 풀어 주는 JPA 컨버터.
 *
 * <p>엔티티 필드에 {@code @Convert(converter = EncryptedStringConverter.class)} 만 붙이면
 * 서비스·화면은 평문을 그대로 쓴다. 결정적 암호화라 {@code findByPhone(...)} 같은 등호 조회도
 * 파라미터가 같은 규칙으로 암호화되어 그대로 맞는다.
 *
 * <p>암호화 전 데이터(접두어 없음)는 읽을 때 그대로 돌려주므로, 부팅 시 일괄 이관
 * ({@link com.damso.match.global.schema.PiiEncryptionMigration})이 끝나기 전에도 화면이 깨지지 않는다.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return DataCrypto.get().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return DataCrypto.get().decrypt(dbData);
    }
}
