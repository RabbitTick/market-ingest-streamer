package com.rabbittick.streamer.connector.dto.upbit;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

@DisplayName("UpbitTradeDto Validation 테스트")
class UpbitTradeDtoValidationTest {

	private Validator validator;

	@BeforeEach
	void setUp() {
		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@Test
	@DisplayName("유효한 데이터가 주어지면 검증에 성공한다")
	void validData_shouldPassValidation() {
		// given
		UpbitTradeDto dto = createValidDto();

		// when
		Set<ConstraintViolation<UpbitTradeDto>> violations = validator.validate(dto);

		// then
		assertThat(violations).isEmpty();
	}

	@Test
	@DisplayName("marketCode가 null이면 검증에 실패한다")
	void nullMarketCode_shouldFailValidation() {
		// given
		UpbitTradeDto dto = createValidDto();
		dto.setMarketCode(null);

		// when
		Set<ConstraintViolation<UpbitTradeDto>> violations = validator.validate(dto);

		// then
		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getMessage()).isEqualTo("마켓 코드는 필수값입니다");
	}

	@Test
	@DisplayName("marketCode가 빈 문자열이면 검증에 실패한다")
	void emptyMarketCode_shouldFailValidation() {
		// given
		UpbitTradeDto dto = createValidDto();
		dto.setMarketCode("");

		// when
		Set<ConstraintViolation<UpbitTradeDto>> violations = validator.validate(dto);

		// then
		assertThat(violations).hasSize(2);
		assertThat(violations.stream().map(ConstraintViolation::getMessage)).containsExactlyInAnyOrder("마켓 코드는 필수값입니다",
			"마켓 코드 형식이 올바르지 않습니다 (예: KRW-BTC)");
	}

	@Test
	@DisplayName("tradeDate 형식이 올바르지 않으면 검증에 실패한다")
	void invalidTradeDate_shouldFailValidation() {
		// given
		UpbitTradeDto dto = createValidDto();
		dto.setTradeDate("20240101");

		// when
		Set<ConstraintViolation<UpbitTradeDto>> violations = validator.validate(dto);

		// then
		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getMessage()).isEqualTo("거래일 형식이 올바르지 않습니다 (yyyy-MM-dd)");
	}

	@Test
	@DisplayName("tradeTime 형식이 올바르지 않으면 검증에 실패한다")
	void invalidTradeTime_shouldFailValidation() {
		// given
		UpbitTradeDto dto = createValidDto();
		dto.setTradeTime("1:2:3");

		// when
		Set<ConstraintViolation<UpbitTradeDto>> violations = validator.validate(dto);

		// then
		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getMessage()).isEqualTo("거래시각 형식이 올바르지 않습니다 (HH:mm:ss)");
	}

	@Test
	@DisplayName("tradePrice가 null이면 검증에 실패한다")
	void nullTradePrice_shouldFailValidation() {
		// given
		UpbitTradeDto dto = createValidDto();
		dto.setTradePrice(null);

		// when
		Set<ConstraintViolation<UpbitTradeDto>> violations = validator.validate(dto);

		// then
		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getMessage()).isEqualTo("체결 가격은 필수값입니다");
	}

	@Test
	@DisplayName("tradeVolume이 0이면 검증에 실패한다")
	void zeroTradeVolume_shouldFailValidation() {
		// given
		UpbitTradeDto dto = createValidDto();
		dto.setTradeVolume(BigDecimal.ZERO);

		// when
		Set<ConstraintViolation<UpbitTradeDto>> violations = validator.validate(dto);

		// then
		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getMessage()).isEqualTo("체결량은 0보다 커야 합니다");
	}

	@Test
	@DisplayName("askBid가 유효하지 않으면 검증에 실패한다")
	void invalidAskBid_shouldFailValidation() {
		// given
		UpbitTradeDto dto = createValidDto();
		dto.setAskBid("BUY");

		// when
		Set<ConstraintViolation<UpbitTradeDto>> violations = validator.validate(dto);

		// then
		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getMessage()).isEqualTo("매수/매도 구분은 ASK 또는 BID여야 합니다");
	}

	@Test
	@DisplayName("timestamp가 음수면 검증에 실패한다")
	void negativeTimestamp_shouldFailValidation() {
		// given
		UpbitTradeDto dto = createValidDto();
		dto.setTimestamp(-1L);

		// when
		Set<ConstraintViolation<UpbitTradeDto>> violations = validator.validate(dto);

		// then
		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getMessage()).isEqualTo("타임스탬프는 양수여야 합니다");
	}

	@Test
	@DisplayName("sequentialId가 음수면 검증에 실패한다")
	void negativeSequentialId_shouldFailValidation() {
		// given
		UpbitTradeDto dto = createValidDto();
		dto.setSequentialId(-1L);

		// when
		Set<ConstraintViolation<UpbitTradeDto>> violations = validator.validate(dto);

		// then
		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getMessage()).isEqualTo("체결 고유 ID는 양수여야 합니다");
	}

	private UpbitTradeDto createValidDto() {
		UpbitTradeDto dto = new UpbitTradeDto();
		dto.setMarketCode("KRW-BTC");
		dto.setTimestamp(System.currentTimeMillis());
		dto.setTradeDate("2024-01-01");
		dto.setTradeTime("12:34:56");
		dto.setTradeTimestamp(System.currentTimeMillis());
		dto.setTradePrice(new BigDecimal("70000000.1234"));
		dto.setTradeVolume(new BigDecimal("0.1234"));
		dto.setAskBid("ASK");
		dto.setPrevClosingPrice(new BigDecimal("69900000"));
		dto.setChange("RISE");
		dto.setChangePrice(new BigDecimal("100000"));
		dto.setSequentialId(123456L);
		dto.setBestAskPrice(new BigDecimal("70010000"));
		dto.setBestAskSize(new BigDecimal("1.2"));
		dto.setBestBidPrice(new BigDecimal("69990000"));
		dto.setBestBidSize(new BigDecimal("0.8"));
		dto.setStreamType("SNAPSHOT");
		return dto;
	}
}
