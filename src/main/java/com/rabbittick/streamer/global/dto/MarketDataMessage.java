package com.rabbittick.streamer.global.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 표준화된 시장 데이터 메시지를 담는 DTO 클래스.
 *
 * <p>주요 책임:
 * <ul>
 *   <li>외부 거래소 API 데이터의 표준화된 래핑</li>
 *   <li>메타데이터와 실제 데이터의 명확한 분리</li>
 *   <li>메시지 추적 및 디버깅을 위한 정보 제공</li>
 *   <li>다양한 데이터 타입(Ticker, Trade, OrderBook)의 통합 처리</li>
 * </ul>
 *
 * <p>이 클래스는 모든 거래소의 데이터를 동일한 형태로 처리할 수 있도록
 * 추상화 계층을 제공한다. 새로운 거래소나 데이터 타입 추가 시에도
 * 일관된 구조를 유지할 수 있다.
 *
 * @param <T> 실제 데이터의 타입 (TickerPayload, TradePayload 등)
 */
@Data
@Builder
public class MarketDataMessage<T> {

	/**
	 * 메시지의 메타정보 (발신자, 타입, 시간 등).
	 *
	 * <p>메시지 추적, 라우팅, 디버깅에 필요한 모든 정보를 포함한다.
	 */
	private Metadata metadata;

	/**
	 * 실제 시장 데이터 (티커, 거래내역, 호가 등).
	 *
	 * <p>제네릭 타입을 사용하여 다양한 종류의 데이터를
	 * 동일한 구조로 처리할 수 있다.
	 */
	private T payload;
}