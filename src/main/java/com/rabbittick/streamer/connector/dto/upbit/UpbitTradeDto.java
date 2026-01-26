package com.rabbittick.streamer.connector.dto.upbit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Upbit WebSocket Trade 응답 데이터를 담는 DTO 클래스.
 *
 * <p>주요 책임:
 * <ul>
 *   <li>Upbit WebSocket API의 trade 메시지 역직렬화</li>
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
public class UpbitTradeDto {

    /**
     * 마켓 코드 (예: KRW-BTC, KRW-ETH).
     *
     * <p>Upbit API 필드: code
     */
    @JsonProperty("cd")
    @NotBlank(message = "마켓 코드는 필수값입니다")
    @Pattern(regexp = "^[A-Z]{3,4}-[A-Z0-9]{2,10}$", message = "마켓 코드 형식이 올바르지 않습니다 (예: KRW-BTC)")
    private String marketCode;

    /**
     * 메시지 수신 시각 (Unix timestamp, milliseconds).
     *
     * <p>Upbit API 필드: timestamp
     */
    @JsonProperty("tms")
    @Positive(message = "타임스탬프는 양수여야 합니다")
    private long timestamp;

    /**
     * 거래일 (yyyy-MM-dd 형식).
     *
     * <p>Upbit API 필드: trade_date
     */
    @JsonProperty("td")
    @NotBlank(message = "거래일은 필수값입니다")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "거래일 형식이 올바르지 않습니다 (yyyy-MM-dd)")
    private String tradeDate;

    /**
     * 거래시각 (HH:mm:ss 형식).
     *
     * <p>Upbit API 필드: trade_time
     */
    @JsonProperty("ttm")
    @NotBlank(message = "거래시각은 필수값입니다")
    @Pattern(regexp = "^\\d{2}:\\d{2}:\\d{2}$", message = "거래시각 형식이 올바르지 않습니다 (HH:mm:ss)")
    private String tradeTime;

    /**
     * 거래 체결 시각 (Unix timestamp, milliseconds).
     *
     * <p>Upbit API 필드: trade_timestamp
     */
    @JsonProperty("ttms")
    @Positive(message = "거래 체결 시각은 양수여야 합니다")
    private long tradeTimestamp;

    /**
     * 체결 가격 (단위: 원 또는 해당 화폐).
     *
     * <p>Upbit API 필드: trade_price
     */
    @JsonProperty("tp")
    @NotNull(message = "체결 가격은 필수값입니다")
    @DecimalMin(value = "0.0", inclusive = false, message = "체결 가격은 0보다 커야 합니다")
    private BigDecimal tradePrice;

    /**
     * 체결량.
     *
     * <p>Upbit API 필드: trade_volume
     */
    @JsonProperty("tv")
    @NotNull(message = "체결량은 필수값입니다")
    @DecimalMin(value = "0.0", inclusive = false, message = "체결량은 0보다 커야 합니다")
    private BigDecimal tradeVolume;

    /**
     * 매수/매도 구분.
     *
     * <p>가능한 값:
     * <ul>
     *   <li>ASK: 매도</li>
     *   <li>BID: 매수</li>
     * </ul>
     *
     * <p>Upbit API 필드: ask_bid
     */
    @JsonProperty("ab")
    @NotBlank(message = "매수/매도 구분은 필수값입니다")
    @Pattern(regexp = "^(ASK|BID)$", message = "매수/매도 구분은 ASK 또는 BID여야 합니다")
    private String askBid;

    /**
     * 전일 종가.
     *
     * <p>Upbit API 필드: prev_closing_price
     */
    @JsonProperty("pcp")
    @DecimalMin(value = "0.0", inclusive = false, message = "전일 종가는 0보다 커야 합니다")
    private BigDecimal prevClosingPrice;

    /**
     * 가격 변화 방향.
     *
     * <p>가능한 값:
     * <ul>
     *   <li>RISE: 상승</li>
     *   <li>EVEN: 보합</li>
     *   <li>FALL: 하락</li>
     * </ul>
     *
     * <p>Upbit API 필드: change
     */
    @JsonProperty("c")
    @Pattern(regexp = "^(RISE|EVEN|FALL)$", message = "가격 변화 방향은 RISE, EVEN, FALL 중 하나여야 합니다")
    private String change;

    /**
     * 변화 금액 (전일 종가 대비).
     *
     * <p>Upbit API 필드: change_price
     */
    @JsonProperty("cp")
    @DecimalMin(value = "0.0", message = "변화 금액은 0 이상이어야 합니다")
    private BigDecimal changePrice;

    /**
     * 체결 고유 ID.
     *
     * <p>Upbit API 필드: sequential_id
     */
    @JsonProperty("sid")
    @Positive(message = "체결 고유 ID는 양수여야 합니다")
    private long sequentialId;

    /**
     * 최우선 매도호가.
     *
     * <p>Upbit API 필드: best_ask_price
     */
    @JsonProperty("bap")
    @DecimalMin(value = "0.0", inclusive = false, message = "최우선 매도호가는 0보다 커야 합니다")
    private BigDecimal bestAskPrice;

    /**
     * 최우선 매도호가 수량.
     *
     * <p>Upbit API 필드: best_ask_size
     */
    @JsonProperty("bas")
    @DecimalMin(value = "0.0", message = "최우선 매도호가 수량은 0 이상이어야 합니다")
    private BigDecimal bestAskSize;

    /**
     * 최우선 매수호가.
     *
     * <p>Upbit API 필드: best_bid_price
     */
    @JsonProperty("bbp")
    @DecimalMin(value = "0.0", inclusive = false, message = "최우선 매수호가는 0보다 커야 합니다")
    private BigDecimal bestBidPrice;

    /**
     * 최우선 매수호가 수량.
     *
     * <p>Upbit API 필드: best_bid_size
     */
    @JsonProperty("bbs")
    @DecimalMin(value = "0.0", message = "최우선 매수호가 수량은 0 이상이어야 합니다")
    private BigDecimal bestBidSize;

    /**
     * 스트림 타입.
     *
     * <p>Upbit API 필드: stream_type
     */
    @JsonProperty("st")
    private String streamType;
}