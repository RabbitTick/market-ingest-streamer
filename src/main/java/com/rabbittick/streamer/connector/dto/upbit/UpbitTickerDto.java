package com.rabbittick.streamer.connector.dto.upbit;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Upbit WebSocket Ticker 응답 데이터를 담는 DTO 클래스.
 *
 * <p>주요 책임:
 * <ul>
 *   <li>Upbit WebSocket API의 ticker 메시지 역직렬화</li>
 *   <li>외부 API 응답과 내부 시스템 간의 데이터 격리</li>
 *   <li>JSON 필드명과 Java 필드명 간의 매핑</li>
 *   <li>입력 데이터의 기본적인 유효성 검증</li>
 * </ul>
 *
 * <p>Upbit API 참조:
 * <a href="https://docs.upbit.com/docs/upbit-quotation-websocket">Upbit WebSocket API</a>
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpbitTickerDto {

	/**
	 * 마켓 코드 (예: KRW-BTC, KRW-ETH).
	 *
	 * <p>Upbit API 필드: cd (code)
	 */
	@JsonProperty("cd")
	private String marketCode;

	/**
	 * 현재가 (단위: 원 또는 해당 화폐).
	 *
	 * <p>Upbit API 필드: tp (trade_price)
	 */
	@JsonProperty("tp")
	private BigDecimal tradePrice;

	/**
	 * 최근 거래량.
	 *
	 * <p>Upbit API 필드: tv (trade_volume)
	 */
	@JsonProperty("tv")
	private BigDecimal tradeVolume;

	/**
	 * 시가 (당일 첫 거래가).
	 *
	 * <p>Upbit API 필드: op (opening_price)
	 */
	@JsonProperty("op")
	private BigDecimal openingPrice;

	/**
	 * 고가 (당일 최고가).
	 *
	 * <p>Upbit API 필드: hp (high_price)
	 */
	@JsonProperty("hp")
	private BigDecimal highPrice;

	/**
	 * 저가 (당일 최저가).
	 *
	 * <p>Upbit API 필드: lp (low_price)
	 */
	@JsonProperty("lp")
	private BigDecimal lowPrice;

	/**
	 * 전일 종가.
	 *
	 * <p>Upbit API 필드: pcp (prev_closing_price)
	 */
	@JsonProperty("pcp")
	private BigDecimal prevClosingPrice;

	/**
	 * 24시간 누적 거래대금.
	 *
	 * <p>Upbit API 필드: atp24h (acc_trade_price_24h)
	 */
	@JsonProperty("atp24h")
	private BigDecimal accTradePrice24h;

	/**
	 * 24시간 누적 거래량.
	 *
	 * <p>Upbit API 필드: atv24h (acc_trade_volume_24h)
	 */
	@JsonProperty("atv24h")
	private BigDecimal accTradeVolume24h;

	/**
	 * 데이터 생성 시각 (Unix timestamp, milliseconds).
	 *
	 * <p>Upbit API 필드: tms (timestamp)
	 */
	@JsonProperty("tms")
	private long timestamp;
}