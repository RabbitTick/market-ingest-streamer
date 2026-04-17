package com.rabbittick.streamer.connector;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.springframework.web.reactive.socket.WebSocketSession;

import reactor.core.publisher.Mono;

/**
 * 거래소 WebSocket 커넥터의 표준 계약을 정의하는 인터페이스.
 *
 * <p>주요 책임:
 * <ul>
 *   <li>거래소별 연결 정보(URI, Ping 주기) 제공</li>
 *   <li>WebSocket 세션에 거래소 전용 구독 메시지 전송</li>
 *   <li>수신된 원시 JSON 메시지 파싱 및 서비스 계층 전달</li>
 *   <li>구독 대상 마켓코드 목록 로드</li>
 * </ul>
 *
 * <p>각 거래소 구현체는 이 인터페이스를 통해 거래소별 프로토콜 차이를
 * 캡슐화하고, {@code AbstractExchangeConnector}가 제공하는 공통 연결
 * 생명주기(연결 시작, 청크 분할, Ping, 재연결) 위에서 동작한다.
 *
 * <p>새로운 거래소를 추가하려면 이 인터페이스와
 * {@code AbstractExchangeConnector}를 함께 구현한다.
 */
public interface ExchangeConnector {

    /**
     * 거래소 식별자를 반환한다.
     *
     * <p>반환값은 RabbitMQ 라우팅 키의 첫 번째 세그먼트로 사용된다.
     * 예: {@code "UPBIT"} → 라우팅 키 {@code upbit.ticker.KRW-BTC}
     *
     * @return 대문자 거래소 식별자 (예: "UPBIT", "BINANCE")
     */
    String getExchangeName();

    /**
     * 거래소 WebSocket 서버 URI를 반환한다.
     *
     * @return WebSocket 서버 URI
     */
    URI getWebSocketUri();

    /**
     * 연결 유지를 위한 Ping 전송 주기를 반환한다.
     *
     * <p>거래소마다 권장 Ping 주기가 다를 수 있으므로 구현체가 직접 정의한다.
     *
     * @return Ping 전송 주기
     */
    Duration getPingInterval();

    /**
     * 이 커넥터의 활성화 여부를 반환한다.
     *
     * <p>{@code false}를 반환하는 커넥터는 {@code ConnectorManager}에 의해
     * {@link #start()} 호출 없이 건너뛴다.
     *
     * @return 활성화 여부
     */
    boolean isEnabled();

    /**
     * WebSocket 연결을 시작하고 데이터 스트리밍을 개시한다.
     *
     * <p>{@code ConnectorManager}가 {@code ApplicationReadyEvent} 수신 후
     * 활성화된 커넥터에 대해 호출한다. 연결 실패 시 지수 백오프 방식으로
     * 재연결을 시도하는 것은 {@code AbstractExchangeConnector}의 공통 구현에서 처리한다.
     */
    void start();

    /**
     * WebSocket 세션에 거래소 전용 구독 메시지를 전송한다.
     *
     * <p>거래소마다 구독 메시지 포맷이 다르므로 구현체가 직접 정의한다.
     * 예: Upbit는 {@code [{"ticket":…}, {"type":"ticker","codes":[…]}]} 형태의 JSON을 전송한다.
     *
     * @param session 데이터를 수신할 WebSocket 세션
     * @param marketCodes 구독할 마켓코드 목록
     * @return 구독 메시지 전송 완료를 나타내는 Mono
     */
    Mono<Void> sendSubscription(WebSocketSession session, List<String> marketCodes);

    /**
     * 수신된 원시 JSON 메시지를 파싱하고 처리한다.
     *
     * <p>메시지 타입 식별 방식은 거래소마다 다르므로 구현체가 직접 정의한다.
     * 예: Upbit는 {@code "ty"} 필드로 {@code ticker} / {@code trade} / {@code orderbook}을 구분한다.
     *
     * <p>파싱할 수 없는 메시지(연결 수락 응답 등)는 빈 Mono를 반환하여 무시한다.
     *
     * @param rawJson WebSocket으로부터 수신된 원시 JSON 문자열
     * @return 처리 완료를 나타내는 Mono. 파싱 불가 메시지의 경우 빈 Mono
     */
    Mono<Void> handleMessage(String rawJson);

    /**
     * 구독할 마켓코드 목록을 로드한다.
     *
     * <p>설정 파일 또는 외부 API에서 마켓코드를 조회하는 방식은
     * 구현체가 직접 정의한다. 로드된 목록은 {@code AbstractExchangeConnector}의
     * 청크 분할 로직에 의해 연결당 최대 구독 수 이하로 나뉘어 처리된다.
     *
     * @return 구독할 마켓코드 목록
     */
    List<String> loadMarketCodes();
}
