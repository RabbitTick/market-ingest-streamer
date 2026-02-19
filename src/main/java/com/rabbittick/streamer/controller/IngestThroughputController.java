package com.rabbittick.streamer.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rabbittick.streamer.metrics.IngestThroughputMetrics;

import lombok.RequiredArgsConstructor;

/**
 * 시장 데이터 수신량(초당 메시지 수)을 조회하는 API.
 */
@RestController
@RequestMapping("/api/ingest")
@RequiredArgsConstructor
public class IngestThroughputController {

	private final IngestThroughputMetrics ingestThroughputMetrics;

	/**
	 * 직전 1초 구간 수신량과 누적/평균 통계를 반환한다.
	 *
	 * @return lastSecond, average(초당 평균), cumulative(누적), elapsedSeconds
	 */
	@GetMapping("/throughput")
	public ThroughputResponse getThroughput() {
		return new ThroughputResponse(
			new ThroughputSnapshot(
				ingestThroughputMetrics.getLastSecondTotal(),
				ingestThroughputMetrics.getLastSecondTicker(),
				ingestThroughputMetrics.getLastSecondTrade(),
				ingestThroughputMetrics.getLastSecondOrderbook()
			),
			new AverageSnapshot(
				ingestThroughputMetrics.getAveragePerSecondTotal(),
				ingestThroughputMetrics.getAveragePerSecondTicker(),
				ingestThroughputMetrics.getAveragePerSecondTrade(),
				ingestThroughputMetrics.getAveragePerSecondOrderbook()
			),
			new CumulativeSnapshot(
				ingestThroughputMetrics.getCumulativeTotal(),
				ingestThroughputMetrics.getCumulativeTicker(),
				ingestThroughputMetrics.getCumulativeTrade(),
				ingestThroughputMetrics.getCumulativeOrderbook()
			),
			ingestThroughputMetrics.getElapsedSeconds()
		);
	}

	public record ThroughputResponse(
		ThroughputSnapshot lastSecond,
		AverageSnapshot average,
		CumulativeSnapshot cumulative,
		double elapsedSeconds
	) {}

	public record ThroughputSnapshot(long total, long ticker, long trade, long orderbook) {}

	public record AverageSnapshot(double total, double ticker, double trade, double orderbook) {}

	public record CumulativeSnapshot(long total, long ticker, long trade, long orderbook) {}
}
