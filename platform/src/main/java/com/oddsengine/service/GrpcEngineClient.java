package com.oddsengine.service;

import com.oddsengine.proto.PredictionRecord;
import com.oddsengine.proto.PredictionServiceGrpc;
import com.oddsengine.proto.PredictRequest;
import com.oddsengine.proto.RatingResponse;
import com.oddsengine.proto.RatingServiceGrpc;
import com.oddsengine.proto.EventResult;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class GrpcEngineClient {
    private static final Logger log = LoggerFactory.getLogger(GrpcEngineClient.class);

    private final ManagedChannel channel;
    private final RatingServiceGrpc.RatingServiceBlockingStub ratingStub;
    private final PredictionServiceGrpc.PredictionServiceBlockingStub predictionStub;

    public GrpcEngineClient() {
        this.channel = ManagedChannelBuilder.forAddress("localhost", 50051)
                .usePlaintext()
                .build();
        this.ratingStub = RatingServiceGrpc.newBlockingStub(channel);
        this.predictionStub = PredictionServiceGrpc.newBlockingStub(channel);
    }

    public RatingResponse updateRatingWithRetry(EventResult request, int maxRetries) {
        int attempt = 0;
        while (attempt < maxRetries) {
            try {
                return ratingStub.withDeadlineAfter(5, TimeUnit.SECONDS).updateRating(request);
            } catch (StatusRuntimeException e) {
                attempt++;
                log.warn("Rating service call failed (attempt {}/{}): {}", attempt, maxRetries, e.getStatus());
                if (attempt >= maxRetries) {
                    throw e;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry wait interrupted", ie);
                }
            }
        }
        throw new RuntimeException("Failed to call rating service after " + maxRetries + " retries");
    }

    public PredictionRecord predictEventWithRetry(PredictRequest request, int maxRetries) {
        int attempt = 0;
        while (attempt < maxRetries) {
            try {
                return predictionStub.withDeadlineAfter(5, TimeUnit.SECONDS).predictEvent(request);
            } catch (StatusRuntimeException e) {
                attempt++;
                log.warn("Prediction service call failed (attempt {}/{}): {}", attempt, maxRetries, e.getStatus());
                if (attempt >= maxRetries) {
                    throw e;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry wait interrupted", ie);
                }
            }
        }
        throw new RuntimeException("Failed to call prediction service after " + maxRetries + " retries");
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
}
