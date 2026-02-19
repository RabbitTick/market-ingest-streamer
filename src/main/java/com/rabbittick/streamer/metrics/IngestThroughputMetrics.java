package com.rabbittick.streamer.metrics;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 시장 데이터 수신량(초당 메시지 수)을 집계하고 주기적으로 로그에 출력한다.
 *
 * <p>매 초마다 직전 1초 동안 수신된 메시지 수를 로그로 남기며,
 * 마지막 구간의 수치와 누적 평균을 API로 조회할 수 있도록 보관한다.
 */
@Slf4j
@Component
public class IngestThroughputMetrics {

	private final AtomicLong currentTicker = new AtomicLong(0);
	private final AtomicLong currentTrade = new AtomicLong(0);
	private final AtomicLong currentOrderbook = new AtomicLong(0);

	private final AtomicLong cumulativeTicker = new AtomicLong(0);
	private final AtomicLong cumulativeTrade = new AtomicLong(0);
	private final AtomicLong cumulativeOrderbook = new AtomicLong(0);

	private volatile long startedAtMs;

	private volatile long lastSecondTotal;
	private volatile long lastSecondTicker;
	private volatile long lastSecondTrade;
	private volatile long lastSecondOrderbook;

	/**
	 * 수신된 메시지 1건을 데이터 타입별로 기록한다.
	 *
	 * @param dataType 메타데이터의 dataType (TICKER, TRADE, ORDERBOOK 등)
	 */
	public void record(String dataType) {
		if (dataType == null) {
			return;
		}
		if (startedAtMs == 0) {
			synchronized (this) {
				if (startedAtMs == 0) {
					startedAtMs = System.currentTimeMillis();
				}
			}
		}
		String normalized = dataType.toUpperCase();
		switch (normalized) {
			case "TICKER" -> {
				currentTicker.incrementAndGet();
				cumulativeTicker.incrementAndGet();
			}
			case "TRADE" -> {
				currentTrade.incrementAndGet();
				cumulativeTrade.incrementAndGet();
			}
			case "ORDERBOOK" -> {
				currentOrderbook.incrementAndGet();
				cumulativeOrderbook.incrementAndGet();
			}
			default -> { /* 알 수 없는 타입은 총합만 필요 시 별도 카운터로 확장 가능 */ }
		}
	}

	/**
	 * 매 초 실행: 직전 1초 구간의 수신량을 로그에 남기고, API 조회용으로 저장한 뒤 카운터를 리셋한다.
	 */
	@Scheduled(fixedRate = 1000)
	public void flushAndLog() {
		long ticker = currentTicker.getAndSet(0);
		long trade = currentTrade.getAndSet(0);
		long orderbook = currentOrderbook.getAndSet(0);
		long total = ticker + trade + orderbook;

		lastSecondTotal = total;
		lastSecondTicker = ticker;
		lastSecondTrade = trade;
		lastSecondOrderbook = orderbook;

		if (total > 0) {
			double avg = getAveragePerSecondTotal();
			log.info("Ingest throughput: {} msg/s (ticker: {}, trade: {}, orderbook: {}), avg: {} msg/s",
				total, ticker, trade, orderbook, String.format("%.1f", avg));
		}
	}

	/** 경과 시간(초). 첫 메시지 수신 전이면 0. */
	public double getElapsedSeconds() {
		if (startedAtMs == 0) {
			return 0;
		}
		return (System.currentTimeMillis() - startedAtMs) / 1000.0;
	}

	/** 전체 평균 초당 메시지 수. */
	public double getAveragePerSecondTotal() {
		double elapsed = getElapsedSeconds();
		if (elapsed <= 0) {
			return 0;
		}
		long total = cumulativeTicker.get() + cumulativeTrade.get() + cumulativeOrderbook.get();
		return total / elapsed;
	}

	public double getAveragePerSecondTicker() {
		return getElapsedSeconds() > 0 ? cumulativeTicker.get() / getElapsedSeconds() : 0;
	}

	public double getAveragePerSecondTrade() {
		return getElapsedSeconds() > 0 ? cumulativeTrade.get() / getElapsedSeconds() : 0;
	}

	public double getAveragePerSecondOrderbook() {
		return getElapsedSeconds() > 0 ? cumulativeOrderbook.get() / getElapsedSeconds() : 0;
	}

	public long getCumulativeTotal() {
		return cumulativeTicker.get() + cumulativeTrade.get() + cumulativeOrderbook.get();
	}

	public long getCumulativeTicker() {
		return cumulativeTicker.get();
	}

	public long getCumulativeTrade() {
		return cumulativeTrade.get();
	}

	public long getCumulativeOrderbook() {
		return cumulativeOrderbook.get();
	}

	public long getLastSecondTotal() {
		return lastSecondTotal;
	}

	public long getLastSecondTicker() {
		return lastSecondTicker;
	}

	public long getLastSecondTrade() {
		return lastSecondTrade;
	}

	public long getLastSecondOrderbook() {
		return lastSecondOrderbook;
	}
}
