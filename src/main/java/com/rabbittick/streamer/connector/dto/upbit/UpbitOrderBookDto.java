package com.rabbittick.streamer.connector.dto.upbit;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Upbit WebSocket OrderBook 응답 데이터를 담는 DTO 클래스.
 *
 * <p>주요 책임:
 * <ul>
 *   <li>Upbit WebSocket API의 orderbook 메시지 역직렬화</li>
 *   <li>호가 단위(orderbook_units) 구조화</li>
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
public class UpbitOrderBookDto {

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
	 * 데이터 생성 시각 (Unix timestamp, milliseconds).
	 *
	 * <p>Upbit API 필드: tms (timestamp)
	 */
	@JsonProperty("tms")
	@Positive(message = "타임스탬프는 양수여야 합니다")
	private long timestamp;

	/**
	 * 총 매도 잔량.
	 *
	 * <p>Upbit API 필드: tas (total_ask_size)
	 */
	@JsonProperty("tas")
	@DecimalMin(value = "0.0", message = "총 매도 잔량은 0 이상이어야 합니다")
	private BigDecimal totalAskSize;

	/**
	 * 총 매수 잔량.
	 *
	 * <p>Upbit API 필드: tbs (total_bid_size)
	 */
	@JsonProperty("tbs")
	@DecimalMin(value = "0.0", message = "총 매수 잔량은 0 이상이어야 합니다")
	private BigDecimal totalBidSize;

	/**
	 * 호가 정보 목록.
	 *
	 * <p>Upbit API 필드: obu (orderbook_units)
	 */
	@JsonProperty("obu")
	@NotNull(message = "호가 리스트는 필수값입니다")
	@Size(min = 1, message = "호가 리스트는 최소 1개 이상이어야 합니다")
	@Valid
	private List<OrderBookUnit> orderbookUnits;

	/**
	 * 호가 단위를 담는 DTO 클래스.
	 */
	@Data
	@NoArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class OrderBookUnit {

		/**
		 * 매도 호가 가격.
		 *
		 * <p>Upbit API 필드: ap (ask_price)
		 */
		@JsonProperty("ap")
		@NotNull(message = "매도 호가 가격은 필수값입니다")
		@DecimalMin(value = "0.0", inclusive = false, message = "매도 호가 가격은 0보다 커야 합니다")
		private BigDecimal askPrice;

		/**
		 * 매도 호가 수량.
		 *
		 * <p>Upbit API 필드: as (ask_size)
		 */
		@JsonProperty("as")
		@NotNull(message = "매도 호가 수량은 필수값입니다")
		@DecimalMin(value = "0.0", message = "매도 호가 수량은 0 이상이어야 합니다")
		private BigDecimal askSize;

		/**
		 * 매수 호가 가격.
		 *
		 * <p>Upbit API 필드: bp (bid_price)
		 */
		@JsonProperty("bp")
		@NotNull(message = "매수 호가 가격은 필수값입니다")
		@DecimalMin(value = "0.0", inclusive = false, message = "매수 호가 가격은 0보다 커야 합니다")
		private BigDecimal bidPrice;

		/**
		 * 매수 호가 수량.
		 *
		 * <p>Upbit API 필드: bs (bid_size)
		 */
		@JsonProperty("bs")
		@NotNull(message = "매수 호가 수량은 필수값입니다")
		@DecimalMin(value = "0.0", message = "매수 호가 수량은 0 이상이어야 합니다")
		private BigDecimal bidSize;
	}
}
