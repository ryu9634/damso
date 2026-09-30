package com.damso.match.global.security;

import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link DataCrypto} 가 EntityManagerFactory 보다 먼저 준비되도록 강제한다.
 *
 * <p>개인정보 컬럼은 {@link EncryptedStringConverter} 가 풀어 주는데, 그 컨버터는 Hibernate 가
 * 만들기 때문에 주입을 못 받고 {@code DataCrypto.get()} 으로 정적 인스턴스를 꺼내 쓴다.
 * 그 인스턴스는 DataCrypto 의 {@code @PostConstruct} 에서 채워진다.
 *
 * <p>그래서 <b>다른 빈의 {@code @PostConstruct} 가 DataCrypto 보다 먼저 돌면서 암호화 컬럼을
 * 읽으면 기동이 실패한다.</b> 실제로 DatingQuestionService·ValueQuestionService·WebPushService 는
 * {@code @PostConstruct} 에서 DB 를 읽는다. 예전에는 DataCrypto 가 같은 패키지에 있고 이름이
 * 알파벳 순으로 앞이라 우연히 먼저 만들어졌을 뿐이며, 패키지를 옮기자 바로 드러났다.
 * 컴포넌트 스캔 순서는 보장되는 값이 아니므로 그 우연에 기대면 안 된다.
 *
 * <p>여기서는 서비스마다 {@code @DependsOn} 을 붙이지 않고 EntityManagerFactory 하나만 묶는다.
 * 모든 쿼리가 EMF 를 거치므로, 앞으로 어떤 빈이 {@code @PostConstruct} 에서 DB 를 읽어도
 * 같은 문제가 다시 생기지 않는다. Spring Boot 가 Flyway·Liquibase 순서를 보장할 때 쓰는 방법과 같다.
 */
@Configuration(proxyBeanMethods = false)
public class DataCryptoInitOrder {

    /**
     * BeanFactoryPostProcessor 는 반드시 {@code static} 팩터리로 등록한다.
     * 인스턴스 메서드로 두면 설정 클래스가 너무 일찍 만들어져 경고가 나고 순서가 다시 흔들린다.
     */
    @Bean
    static EntityManagerFactoryDependsOnPostProcessor entityManagerFactoryDependsOnDataCrypto() {
        return new EntityManagerFactoryDependsOnPostProcessor("dataCrypto");
    }
}
