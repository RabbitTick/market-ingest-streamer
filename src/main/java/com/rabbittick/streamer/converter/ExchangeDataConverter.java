package com.rabbittick.streamer.converter;

import com.rabbittick.streamer.global.dto.MarketDataMessage;
import com.rabbittick.streamer.global.dto.OrderBookPayload;
import com.rabbittick.streamer.global.dto.TickerPayload;
import com.rabbittick.streamer.global.dto.TradePayload;

/**
 * 거래소 WebSocket 수신 데이터를 시스템 표준 DTO로 변환하는 컨버터의 표준 계약을 정의하는 인터페이스.
 *
 * <p>주요 책임:
 * <ul>
 *   <li>거래소별 원시 JSON 메시지를 표준 {@link MarketDataMessage}로 변환</li>
 *   <li>거래소 식별자 제공 (라우팅 키 생성에 사용)</li>
 * </ul>
 *
 * <p>각 거래소 구현체는 이 인터페이스를 통해 거래소별 JSON 포맷 차이를 캡슐화한다.
 * 변환된 {@link MarketDataMessage}는 거래소 종류에 관계없이 동일한 구조를 가지므로
 * 하위 계층(발행, 저장)은 거래소에 대한 지식 없이 데이터를 처리할 수 있다.
 *
 * <p>새로운 거래소를 추가하려면 이 인터페이스를 구현하고
 * {@code AbstractExchangeConnector}의 {@code handleMessage()} 안에서 호출한다.
 */
public interface ExchangeDataConverter {

    /**
     * 거래소 식별자를 반환한다.
     *
     * <p>반환값은 {@link com.rabbittick.streamer.global.dto.Metadata}의
     * {@code exchange} 필드에 기록되며, RabbitMQ 라우팅 키 생성에 사용된다.
     *
     * @return 대문자 거래소 식별자 (예: "UPBIT", "BINANCE")
     */
    String getExchangeName();

    /**
     * 원시 JSON 문자열을 파싱하여 표준 ticker {@link MarketDataMessage}로 변환한다.
     *
     * @param rawJson 거래소 WebSocket으로부터 수신된 원시 JSON 문자열
     * @return 표준화된 ticker MarketDataMessage
     * @throws IllegalArgumentException JSON 파싱 실패 또는 필수 필드 누락 시
     */
    MarketDataMessage<TickerPayload> convertTicker(String rawJson);

    /**
     * 원시 JSON 문자열을 파싱하여 표준 trade {@link MarketDataMessage}로 변환한다.
     *
     * @param rawJson 거래소 WebSocket으로부터 수신된 원시 JSON 문자열
     * @return 표준화된 trade MarketDataMessage
     * @throws IllegalArgumentException JSON 파싱 실패 또는 필수 필드 누락 시
     */
    MarketDataMessage<TradePayload> convertTrade(String rawJson);

    /**
     * 원시 JSON 문자열을 파싱하여 표준 orderbook {@link MarketDataMessage}로 변환한다.
     *
     * @param rawJson 거래소 WebSocket으로부터 수신된 원시 JSON 문자열
     * @return 표준화된 orderbook MarketDataMessage
     * @throws IllegalArgumentException JSON 파싱 실패 또는 필수 필드 누락 시
     */
    MarketDataMessage<OrderBookPayload> convertOrderBook(String rawJson);
}
