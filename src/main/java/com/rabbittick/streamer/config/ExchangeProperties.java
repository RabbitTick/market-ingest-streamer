package com.rabbittick.streamer.config;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * 다중 거래소 WebSocket 커넥터 설정을 바인딩하는 프로퍼티 클래스.
 *
 * <p>{@code application.yml}의 {@code exchanges.configs} 하위 설정을 거래소 식별자를
 * 키로, {@link ExchangeConfig}를 값으로 하는 맵에 바인딩한다.
 * 거래소 식별자는 YAML 키(소문자)이며, 커넥터 내부에서 대문자로 변환하여 사용한다.
 *
 * <p>YAML 설정 예시:
 * <pre>{@code
 * exchanges:
 *   configs:
 *     upbit:
 *       enabled: true
 *       websocket-uri: wss://api.upbit.com/websocket/v1
 *       ping-interval-seconds: 60
 *       max-markets-per-connection: 100
 *       processing-concurrency: 32
 *       environment: fetched
 *       market-prefixes: [KRW, BTC, USDT]
 *       data-types:
 *         ticker: true
 *         trade: true
 *         orderbook: true
 *     binance:
 *       enabled: false
 *       websocket-uri: wss://stream.binance.com:9443/ws
 *       market-prefixes: [USDT]
 *       data-types:
 *         ticker: true
 *         trade: false
 *         orderbook: false
 * }</pre>
 *
 * <p>새로운 거래소를 추가하려면 {@code exchanges.configs} 하위에 거래소 식별자를 키로
 * 새 항목을 추가하고, 해당 {@link com.rabbittick.streamer.connector.ExchangeConnector}
 * 구현체를 작성한다.
 */
@Getter
@Setter
@ConfigurationProperties("exchanges")
public class ExchangeProperties {

    /**
     * 거래소 식별자(소문자)를 키로, 개별 거래소 설정을 값으로 가지는 맵.
     *
     * <p>키는 YAML에 선언된 소문자 거래소 이름이다.
     * 커넥터에서 라우팅 키 생성 시 {@code String.toUpperCase()}로 변환하여 사용한다.
     */
    private Map<String, ExchangeConfig> configs = new LinkedHashMap<>();

    /**
     * 단일 거래소의 WebSocket 연결 및 수집 설정을 담는 클래스.
     */
    @Getter
    @Setter
    public static class ExchangeConfig {

        /**
         * 이 커넥터의 활성화 여부.
         *
         * <p>{@code false}이면 {@code ConnectorManager}가 {@code start()} 호출 없이
         * 건너뛴다. 기본값은 {@code false}로, 명시적으로 활성화해야 연결이 시작된다.
         */
        private boolean enabled = false;

        /**
         * 거래소 WebSocket 서버 URI.
         *
         * <p>예: {@code wss://api.upbit.com/websocket/v1}
         */
        private URI websocketUri;

        /**
         * 연결 유지를 위한 Ping 전송 주기 (초).
         *
         * <p>거래소별 권장 주기에 맞게 설정한다. 기본값은 60초.
         */
        private int pingIntervalSeconds = 60;

        /**
         * 단일 WebSocket 연결당 최대 구독 마켓코드 수.
         *
         * <p>이 값을 초과하면 마켓코드를 청크로 나눠 복수의 연결을 유지한다.
         * 거래소 API 제약에 맞게 조정한다. 기본값은 100.
         */
        private int maxMarketsPerConnection = 100;

        /**
         * 메시지 처리 시 동시에 실행할 내부 Mono의 최대 개수.
         *
         * <p>파싱·변환·발행 작업은 {@code boundedElastic} 스케줄러에서
         * 이 값을 상한으로 병렬 처리된다. 기본값은 32.
         */
        private int processingConcurrency = 32;

        /**
         * 사용할 마켓코드 환경 이름.
         *
         * <p>마켓코드 YAML 파일 내 {@code env} 섹션의 키와 일치해야 한다.
         * 예: {@code development}, {@code production}, {@code fetched}.
         * 기본값은 {@code development}.
         */
        private String environment = "development";

        /**
         * 구독할 마켓 prefix 목록.
         *
         * <p>각 prefix에 해당하는 마켓코드를 {@code loadMarketCodes()}에서 로드한다.
         * 예: {@code [KRW, BTC, USDT]}.
         * 기본값은 {@code [KRW]}.
         */
        private List<String> marketPrefixes = List.of("KRW");

        /**
         * 수집할 데이터 타입별 활성화 설정.
         *
         * <p>비활성화된 타입의 메시지는 수신하더라도 무시한다.
         * 최소 하나의 타입이 활성화되어 있어야 한다.
         */
        private DataTypesConfig dataTypes = new DataTypesConfig();

        /**
         * {@code pingIntervalSeconds}를 {@link Duration}으로 변환하여 반환한다.
         *
         * <p>{@link com.rabbittick.streamer.connector.ExchangeConnector#getPingInterval()}
         * 구현체에서 이 메서드를 사용하여 Ping 주기를 가져온다.
         *
         * @return Ping 전송 주기
         */
        public Duration getPingInterval() {
            return Duration.ofSeconds(pingIntervalSeconds);
        }
    }

    /**
     * 수집할 데이터 타입별 활성화 여부를 담는 클래스.
     */
    @Getter
    @Setter
    public static class DataTypesConfig {

        /** 현재가(ticker) 데이터 수집 활성화 여부. 기본값은 {@code true}. */
        private boolean ticker = true;

        /** 거래 체결(trade) 데이터 수집 활성화 여부. 기본값은 {@code false}. */
        private boolean trade = false;

        /** 호가(orderbook) 데이터 수집 활성화 여부. 기본값은 {@code false}. */
        private boolean orderbook = false;
    }
}
