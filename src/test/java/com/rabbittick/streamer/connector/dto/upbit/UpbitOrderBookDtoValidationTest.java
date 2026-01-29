package com.rabbittick.streamer.connector.dto.upbit;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

@DisplayName("UpbitOrderBookDto Validation 테스트")
class UpbitOrderBookDtoValidationTest {

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
		UpbitOrderBookDto dto = createValidDto();

		// when
		Set<ConstraintViolation<UpbitOrderBookDto>> violations = validator.validate(dto);

		// then
		assertThat(violations).isEmpty();
	}

	@Test
	@DisplayName("marketCode가 null이면 검증에 실패한다")
	void nullMarketCode_shouldFailValidation() {
		// given
		UpbitOrderBookDto dto = createValidDto();
		dto.setMarketCode(null);

		// when
		Set<ConstraintViolation<UpbitOrderBookDto>> violations = validator.validate(dto);

		// then
		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getMessage()).isEqualTo("마켓 코드는 필수값입니다");
	}

	@Test
	@DisplayName("orderbookUnits가 null이면 검증에 실패한다")
	void nullOrderbookUnits_shouldFailValidation() {
		// given
		UpbitOrderBookDto dto = createValidDto();
		dto.setOrderbookUnits(null);

		// when
		Set<ConstraintViolation<UpbitOrderBookDto>> violations = validator.validate(dto);

		// then
		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getMessage()).isEqualTo("호가 리스트는 필수값입니다");
	}

	@Test
	@DisplayName("orderbookUnits가 비어있으면 검증에 실패한다")
	void emptyOrderbookUnits_shouldFailValidation() {
		// given
		UpbitOrderBookDto dto = createValidDto();
		dto.setOrderbookUnits(List.of());

		// when
		Set<ConstraintViolation<UpbitOrderBookDto>> violations = validator.validate(dto);

		// then
		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getMessage()).isEqualTo("호가 리스트는 최소 1개 이상이어야 합니다");
	}

	@Test
	@DisplayName("음수 타임스탬프는 검증에 실패한다")
	void negativeTimestamp_shouldFailValidation() {
		// given
		UpbitOrderBookDto dto = createValidDto();
		dto.setTimestamp(-1L);

		// when
		Set<ConstraintViolation<UpbitOrderBookDto>> violations = validator.validate(dto);

		// then
		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getMessage()).isEqualTo("타임스탬프는 양수여야 합니다");
	}

	@Test
	@DisplayName("호가 단위의 필수 필드가 없으면 검증에 실패한다")
	void invalidOrderbookUnit_shouldFailValidation() {
		// given
		UpbitOrderBookDto dto = createValidDto();
		dto.getOrderbookUnits().get(0).setAskPrice(null);

		// when
		Set<ConstraintViolation<UpbitOrderBookDto>> violations = validator.validate(dto);

		// then
		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getMessage()).isEqualTo("매도 호가 가격은 필수값입니다");
	}

	private UpbitOrderBookDto createValidDto() {
		UpbitOrderBookDto dto = new UpbitOrderBookDto();
		dto.setMarketCode("KRW-BTC");
		dto.setTimestamp(System.currentTimeMillis());
		dto.setTotalAskSize(new BigDecimal("12.34"));
		dto.setTotalBidSize(new BigDecimal("10.56"));
		dto.setOrderbookUnits(List.of(createUnit(new BigDecimal("70000000"), new BigDecimal("1.2"),
			new BigDecimal("69900000"), new BigDecimal("0.8"))));
		return dto;
	}

	private UpbitOrderBookDto.OrderBookUnit createUnit(BigDecimal askPrice, BigDecimal askSize,
		BigDecimal bidPrice, BigDecimal bidSize) {
		UpbitOrderBookDto.OrderBookUnit unit = new UpbitOrderBookDto.OrderBookUnit();
		unit.setAskPrice(askPrice);
		unit.setAskSize(askSize);
		unit.setBidPrice(bidPrice);
		unit.setBidSize(bidSize);
		return unit;
	}
}
