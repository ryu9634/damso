package com.damso.match.domain.dating.entity;

import com.damso.match.global.schema.DatingSchemaMigration;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * dating_profiles.status 문자열 ↔ {@link DatingStatus}.
 *  - 레거시 값 'NEW'(신규)를 PENDING(심사중)으로 읽어준다.
 *    DatingSchemaMigration 의 SQL 백필은 CommandLineRunner 라 빈 초기화(@PostConstruct)
 *    보다 늦게 돌기 때문에, 읽는 쪽에서도 막아줘야 기동이 깨지지 않는다.
 *  - 모르는 값이 들어와도 예외 대신 PENDING 으로 떨어뜨린다.
 */
@Converter
public class DatingStatusConverter implements AttributeConverter<DatingStatus, String> {

    @Override
    public String convertToDatabaseColumn(DatingStatus status) {
        return status == null ? null : status.name();
    }

    @Override
    public DatingStatus convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) return null;
        if ("NEW".equals(value)) return DatingStatus.PENDING;
        try {
            return DatingStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return DatingStatus.PENDING;
        }
    }
}
