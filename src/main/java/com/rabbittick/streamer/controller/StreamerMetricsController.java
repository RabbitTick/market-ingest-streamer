package com.rabbittick.streamer.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rabbittick.streamer.metrics.IngestThroughputMetrics;
import com.rabbittick.streamer.metrics.PublishFailureMetrics;

import lombok.RequiredArgsConstructor;

/**
 * 스트리머의 메시지 처리 메트릭을 조회하는 API.
 *
 * <p>수신량(throughput)과 발행 실패(failures) 메트릭을 제공한다.
 */
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class StreamerMetricsController {

    private final IngestThroughputMetrics ingestThroughputMetrics;
    private final PublishFailureMetrics publishFailureMetrics;

    /**
     * 직전 1초 구간 수신량과 누적/평균 통계를 반환한다.
     *
     * @return lastSecond, average(초당 평균), cumulative(누적), elapsedSeconds
     */
    @GetMapping("/throughput")
    public ThroughputResponse getThroughput() {
        return new ThroughputResponse(
                new DataTypeSnapshot(
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
                new DataTypeSnapshot(
                        ingestThroughputMetrics.getCumulativeTotal(),
                        ingestThroughputMetrics.getCumulativeTicker(),
                        ingestThroughputMetrics.getCumulativeTrade(),
                        ingestThroughputMetrics.getCumulativeOrderbook()
                ),
                ingestThroughputMetrics.getElapsedSeconds()
        );
    }

    /**
     * 직전 1초 구간 발행 실패 건수와 누적 통계를 반환한다.
     *
     * @return lastSecond, cumulative(누적)
     */
    @GetMapping("/failures")
    public FailureResponse getFailures() {
        return new FailureResponse(
                new DataTypeSnapshot(
                        publishFailureMetrics.getLastSecondTotal(),
                        publishFailureMetrics.getLastSecondTicker(),
                        publishFailureMetrics.getLastSecondTrade(),
                        publishFailureMetrics.getLastSecondOrderbook()
                ),
                new DataTypeSnapshot(
                        publishFailureMetrics.getCumulativeTotal(),
                        publishFailureMetrics.getCumulativeTicker(),
                        publishFailureMetrics.getCumulativeTrade(),
                        publishFailureMetrics.getCumulativeOrderbook()
                )
        );
    }

    /**
     * 수신량과 발행 실패를 한 번에 조회한다.
     *
     * @return throughput + failures 통합 응답
     */
    @GetMapping("/summary")
    public SummaryResponse getSummary() {
        return new SummaryResponse(
                getThroughput(),
                getFailures()
        );
    }

    public record ThroughputResponse(
            DataTypeSnapshot lastSecond,
            AverageSnapshot average,
            DataTypeSnapshot cumulative,
            double elapsedSeconds
    ) {}

    public record FailureResponse(
            DataTypeSnapshot lastSecond,
            DataTypeSnapshot cumulative
    ) {}

    public record SummaryResponse(
            ThroughputResponse throughput,
            FailureResponse failures
    ) {}

    public record DataTypeSnapshot(long total, long ticker, long trade, long orderbook) {}

    public record AverageSnapshot(double total, double ticker, double trade, double orderbook) {}
}