package com.rabbittick.streamer.connector.dto.upbit;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

@DisplayName("UpbitTickerDto Validation 테스트")
class UpbitTickerDtoValidationTest {

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
		UpbitTickerDto dto = createValidDto();

		// when
		Set<ConstraintViolation<UpbitTickerDto>> violations = validator.validate(dto);

		// then
		assertThat(violations).isEmpty();
	}

	@Test
	@DisplayName("marketCode가 null이면 검증에 실패한다")
	void nullMarketCode_shouldFailValidation() {
		// given
		UpbitTickerDto dto = createValidDto();
		dto.setMarketCode(null);

		// when
		Set<ConstraintViolation<UpbitTickerDto>> violations = validator.validate(dto);

		// then
		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getMessage()).isEqualTo("마켓 코드는 필수값입니다");
	}

	@Test
	@DisplayName("marketCode가 빈 문자열이면 검증에 실패한다")
	void emptyMarketCode_shouldFailValidation() {
		// given
		UpbitTickerDto dto = createValidDto();
		dto.setMarketCode("");

		// when
		Set<ConstraintViolation<UpbitTickerDto>> violations = validator.validate(dto);

		// then
		assertThat(violations).hasSize(2);
		assertThat(violations.stream().map(ConstraintViolation::getMessage)).containsExactlyInAnyOrder("마켓 코드는 필수값입니다",
			"마켓 코드 형식이 올바르지 않습니다 (예: KRW-BTC)");
	}

	@Test
	@DisplayName("marketCode가 공백 문자열이면 검증에 실패한다")
	void blankMarketCode_shouldFailValidation() {
		// given
		UpbitTickerDto dto = createValidDto();
		dto.setMarketCode("   ");

		// when
		Set<ConstraintViolation<UpbitTickerDto>> violations = validator.validate(dto);

		// then
		assertThat(violations).hasSize(2);
		assertThat(violations.stream().map(ConstraintViolation::getMessage)).containsExactlyInAnyOrder("마켓 코드는 필수값입니다",
			"마켓 코드 형식이 올바르지 않습니다 (예: KRW-BTC)");
	}

	@Test
	@DisplayName("잘못된 marketCode 형식이면 검증 실패")
	void invalidMarketCodeFormat_shouldFailValidation() {
		// given
		UpbitTickerDto dto = createValidDto();
		dto.setMarketCode("INVALID-FORMAT");

		// when
		Set<ConstraintViolation<UpbitTickerDto>> violations = validator.validate(dto);

		// then
		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getMessage()).isEqualTo("마켓 코드 형식이 올바르지 않습니다 (예: KRW-BTC)");
	}

	@Test
	@DisplayName("tradePrice가 null이면 검증 실패")
	void nullTradePrice_shouldFailValidation() {
		// given
		UpbitTickerDto dto = createValidDto();
		dto.setTradePrice(null);

		// when
		Set<ConstraintViolation<UpbitTickerDto>> violations = validator.validate(dto);

		// then
		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getMessage()).isEqualTo("현재가는 필수값입니다");
	}

	@Test
	@DisplayName("tradePrice가 0이면 검증 실패")
	void zeroTradePrice_shouldFailValidation() {
		// given
		UpbitTickerDto dto = createValidDto();
		dto.setTradePrice(BigDecimal.ZERO);

		// when
		Set<ConstraintViolation<UpbitTickerDto>> violations = validator.validate(dto);

		// then
		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getMessage()).isEqualTo("현재가는 0보다 커야 합니다");
	}

	@Test
	@DisplayName("음수 가격들은 검증 실패")
	void negativePrices_shouldFailValidation() {
		// given
		UpbitTickerDto dto = createValidDto();
		dto.setHighPrice(new BigDecimal("-1"));
		dto.setLowPrice(new BigDecimal("-1"));
		dto.setOpeningPrice(new BigDecimal("-1"));

		// when
		Set<ConstraintViolation<UpbitTickerDto>> violations = validator.validate(dto);

		// then
		assertThat(violations).hasSize(3);
	}

	@Test
	@DisplayName("음수 타임스탬프는 검증 실패")
	void negativeTimestamp_shouldFailValidation() {
		// given
		UpbitTickerDto dto = createValidDto();
		dto.setTimestamp(-1L);

		// when
		Set<ConstraintViolation<UpbitTickerDto>> violations = validator.validate(dto);

		// then
		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getMessage()).isEqualTo("타임스탬프는 양수여야 합니다");
	}

	@Test
	@DisplayName("여러 필드가 잘못되면 모든 검증 오류 반환")
	void multipleInvalidFields_shouldReturnAllViolations() {
		// given
		UpbitTickerDto dto = new UpbitTickerDto();
		dto.setMarketCode("");
		dto.setTradePrice(null);
		dto.setTimestamp(-1L);

		// when
		Set<ConstraintViolation<UpbitTickerDto>> violations = validator.validate(dto);

		// then
		assertThat(violations).hasSize(4);

		assertThat(violations.stream().map(ConstraintViolation::getMessage)).containsExactlyInAnyOrder("마켓 코드는 필수값입니다",
			"마켓 코드 형식이 올바르지 않습니다 (예: KRW-BTC)", "현재가는 필수값입니다", "타임스탬프는 양수여야 합니다");
	}

	private UpbitTickerDto createValidDto() {
		UpbitTickerDto dto = new UpbitTickerDto();
		dto.setMarketCode("KRW-BTC");
		dto.setTradePrice(new BigDecimal("50000000"));
		dto.setTradeVolume(new BigDecimal("1.5"));
		dto.setOpeningPrice(new BigDecimal("49000000"));
		dto.setHighPrice(new BigDecimal("51000000"));
		dto.setLowPrice(new BigDecimal("48000000"));
		dto.setPrevClosingPrice(new BigDecimal("49500000"));
		dto.setAccTradePrice24h(new BigDecimal("1000000000"));
		dto.setAccTradeVolume24h(new BigDecimal("20.5"));
		dto.setTimestamp(System.currentTimeMillis());
		return dto;
	}
}