package com.rabbittick.streamer.connector.dto.upbit;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
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
	@NotBlank(message = "마켓 코드는 필수값입니다")
	@Pattern(regexp = "^[A-Z]{3,4}-[A-Z0-9]{2,10}$", message = "마켓 코드 형식이 올바르지 않습니다 (예: KRW-BTC)")
	private String marketCode;

	/**
	 * 현재가 (단위: 원 또는 해당 화폐).
	 *
	 * <p>Upbit API 필드: tp (trade_price)
	 */
	@JsonProperty("tp")
	@NotNull(message = "현재가는 필수값입니다")
	@DecimalMin(value = "0.0", inclusive = false, message = "현재가는 0보다 커야 합니다")
	private BigDecimal tradePrice;

	/**
	 * 최근 거래량.
	 *
	 * <p>Upbit API 필드: tv (trade_volume)
	 */
	@JsonProperty("tv")
	@DecimalMin(value = "0.0", message = "거래량은 0 이상이어야 합니다")
	private BigDecimal tradeVolume;

	/**
	 * 시가 (당일 첫 거래가).
	 *
	 * <p>Upbit API 필드: op (opening_price)
	 */
	@JsonProperty("op")
	@DecimalMin(value = "0.0", inclusive = false, message = "시가는 0보다 커야 합니다")
	private BigDecimal openingPrice;

	/**
	 * 고가 (당일 최고가).
	 *
	 * <p>Upbit API 필드: hp (high_price)
	 */
	@JsonProperty("hp")
	@DecimalMin(value = "0.0", inclusive = false, message = "고가는 0보다 커야 합니다")
	private BigDecimal highPrice;

	/**
	 * 저가 (당일 최저가).
	 *
	 * <p>Upbit API 필드: lp (low_price)
	 */
	@JsonProperty("lp")
	@DecimalMin(value = "0.0", inclusive = false, message = "저가는 0보다 커야 합니다")
	private BigDecimal lowPrice;

	/**
	 * 전일 종가.
	 *
	 * <p>Upbit API 필드: pcp (prev_closing_price)
	 */
	@JsonProperty("pcp")
	@DecimalMin(value = "0.0", inclusive = false, message = "전일 종가는 0보다 커야 합니다")
	private BigDecimal prevClosingPrice;

	/**
	 * 24시간 누적 거래대금.
	 *
	 * <p>Upbit API 필드: atp24h (acc_trade_price_24h)
	 */
	@JsonProperty("atp24h")
	@DecimalMin(value = "0.0", message = "24시간 누적 거래대금은 0 이상이어야 합니다")
	private BigDecimal accTradePrice24h;

	/**
	 * 24시간 누적 거래량.
	 *
	 * <p>Upbit API 필드: atv24h (acc_trade_volume_24h)
	 */
	@JsonProperty("atv24h")
	@DecimalMin(value = "0.0", message = "24시간 누적 거래량은 0 이상이어야 합니다")
	private BigDecimal accTradeVolume24h;

	/**
	 * 데이터 생성 시각 (Unix timestamp, milliseconds).
	 *
	 * <p>Upbit API 필드: tms (timestamp)
	 */
	@JsonProperty("tms")
	@Positive(message = "타임스탬프는 양수여야 합니다")
	private long timestamp;
}