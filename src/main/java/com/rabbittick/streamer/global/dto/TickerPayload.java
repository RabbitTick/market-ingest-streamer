package com.rabbittick.streamer.global.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

/**
 * 표준화된 티커 데이터를 담는 Payload DTO 클래스.
 *
 * <p>주요 책임:
 * <ul>
 *   <li>거래소별 티커 데이터의 표준화된 표현</li>
 *   <li>가격 및 거래량 정보의 정밀한 처리</li>
 *   <li>24시간 통계 데이터 제공</li>
 *   <li>데이터 무결성 및 유효성 보장</li>
 * </ul>
 *
 * <p>이 클래스는 다양한 거래소의 티커 데이터를 하나의 표준 형태로
 * 통합한다. BigDecimal을 사용하여 금융 데이터의 정밀도를 보장하고,
 * 적절한 검증 규칙을 통해 데이터 품질을 유지한다.
 */
@Data
@Builder
public class TickerPayload {

	/**
	 * 마켓 코드 (예: KRW-BTC, KRW-ETH).
	 *
	 * <p>거래소에서 사용하는 표준 마켓 식별자로,
	 * 기준 통화와 거래 통화의 쌍을 나타낸다.
	 */
	private String marketCode;

	/**
	 * 현재가 (최근 체결가).
	 *
	 * <p>가장 최근에 체결된 거래의 가격이다.
	 * BigDecimal을 사용하여 부동소수점 오차를 방지한다.
	 */
	private BigDecimal tradePrice;

	/**
	 * 최근 거래량.
	 *
	 * <p>가장 최근 거래에서 체결된 수량이다.
	 */
	private BigDecimal tradeVolume;

	/**
	 * 시가 (당일 첫 거래가).
	 *
	 * <p>당일 첫 번째 거래에서 체결된 가격이다.
	 */
	private BigDecimal openingPrice;

	/**
	 * 고가 (당일 최고가).
	 *
	 * <p>당일 거래 중 가장 높은 체결 가격이다.
	 */
	private BigDecimal highPrice;

	/**
	 * 저가 (당일 최저가).
	 *
	 * <p>당일 거래 중 가장 낮은 체결 가격이다.
	 */
	private BigDecimal lowPrice;

	/**
	 * 전일 종가.
	 *
	 * <p>전날 마지막 거래에서 체결된 가격이다.
	 * 등락률 계산의 기준이 된다.
	 */
	private BigDecimal prevClosingPrice;

	/**
	 * 24시간 누적 거래대금.
	 *
	 * <p>최근 24시간 동안의 총 거래 금액이다.
	 * 시장의 활성도를 나타내는 지표로 사용된다.
	 */
	private BigDecimal accTradePrice24h;

	/**
	 * 24시간 누적 거래량.
	 *
	 * <p>최근 24시간 동안의 총 거래 수량이다.
	 */
	private BigDecimal accTradeVolume24h;

	/**
	 * 티커 데이터 생성 시각 (Unix timestamp, milliseconds).
	 *
	 * <p>거래소에서 이 티커 정보를 생성한 시점이다.
	 * 데이터의 신선도를 판단하는 기준이 된다.
	 */
	private long timestamp;
}