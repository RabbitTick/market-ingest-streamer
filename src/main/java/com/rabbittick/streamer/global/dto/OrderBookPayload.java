package com.rabbittick.streamer.global.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * 표준화된 호가 데이터를 담는 Payload DTO 클래스.
 *
 * <p>주요 책임:
 * <ul>
 *   <li>거래소별 orderbook 데이터의 표준화된 표현</li>
 *   <li>매수/매도 잔량 및 호가 단위 정보 제공</li>
 *   <li>데이터 무결성 유지</li>
 * </ul>
 */
@Data
@Builder
public class OrderBookPayload {

	/**
	 * 마켓 코드 (예: KRW-BTC, KRW-ETH).
	 */
	private String marketCode;

	/**
	 * 데이터 생성 시각 (Unix timestamp, milliseconds).
	 */
	private long timestamp;

	/**
	 * 총 매도 잔량.
	 */
	private BigDecimal totalAskSize;

	/**
	 * 총 매수 잔량.
	 */
	private BigDecimal totalBidSize;

	/**
	 * 호가 단위 목록.
	 */
	private List<OrderBookUnitPayload> orderbookUnits;
}
