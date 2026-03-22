package com.antiesp.plugin.detection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Statistical Analysis Engine for Advanced Detection
 * Uses behavioral statistics to identify cheating patterns
 * Even if individual detections are bypassed, statistical patterns remain
 */
public class StatisticalAnalysisEngine {
    
    /**
     * Player behavior profile - normal humans vs cheaters have different distributions
     */
    public static class BehaviorProfile {
        public double[] miningAccuracy;           // % accurate blocks per session
        public double[] attackIntervals;          // Time between attacks (ms)
        public double[] aimConsistency;          // Rotation smoothness
        public double[] movePatterns;            // Movement vector consistency
        public double[] reactionTimes;           // Response time to stimuli
        public double[] resourceFinding;         // How quickly finds valuable blocks
        public long sessionStartTime;
        
        // Statistical measures
        public double miningMean;
        public double miningStdDev;
        public double attackMean;
        public double attackStdDev;
        public double aimMean;
        public double aimStdDev;
        
        // Behavioral flags
        public int suspiciousActions = 0;
        public int totalActions = 0;
        
        BehaviorProfile() {
            this.sessionStartTime = System.currentTimeMillis();
            this.miningAccuracy = new double[100];
            this.attackIntervals = new double[100];
            this.aimConsistency = new double[100];
            this.movePatterns = new double[100];
            this.reactionTimes = new double[100];
            this.resourceFinding = new double[100];
        }
        
        /**
         * Calculate statistical measures from collected data
         */
        public void analyzeProfile() {
            this.miningMean = calculateMean(miningAccuracy);
            this.miningStdDev = calculateStdDev(miningAccuracy, miningMean);
            this.attackMean = calculateMean(attackIntervals);
            this.attackStdDev = calculateStdDev(attackIntervals, attackMean);
            this.aimMean = calculateMean(aimConsistency);
            this.aimStdDev = calculateStdDev(aimConsistency, aimMean);
        }
        
        /**
         * Get cheat probability based on statistical profile
         * Uses multiple statistical tests
         */
        public double calculateCheatProbability() {
            double score = 0;
            int tests = 0;
            
            // Test 1: Too consistent mining accuracy
            // Cheaters have <0.1 std dev, humans have >0.15
            if (miningStdDev < 0.1 && miningMean > 0.65) {
                score += 0.25;
                tests++;
            }
            
            // Test 2: Impossible attack intervals
            // Cheaters have <150ms intervals, humans have >400ms
            if (attackMean < 150 && attackStdDev < 30) {
                score += 0.25;
                tests++;
            }
            
            // Test 3: Perfect aim consistency
            // Cheaters have >0.95 consistency, humans <0.8
            if (aimMean > 0.95 && aimStdDev < 0.05) {
                score += 0.25;
                tests++;
            }
            
            // Test 4: Behavioral anomalies
            double anomalyRate = (double) suspiciousActions / Math.max(1, totalActions);
            if (anomalyRate > 0.15) {
                score += 0.25;
                tests++;
            }
            
            return tests > 0 ? score / tests : 0;
        }
        
        /**
         * Z-score analysis - how many standard deviations from mean
         * Z > 3 is statistically significant anomaly
         */
        public double getZScore(double value, double mean, double stdDev) {
            return Math.abs((value - mean) / (stdDev + 0.01));
        }
    }
    
    /**
     * Calculate mean of array (ignoring zeros)
     */
    private static double calculateMean(double[] values) {
        return Arrays.stream(values)
            .filter(v -> v > 0)
            .average()
            .orElse(0);
    }
    
    /**
     * Calculate standard deviation
     */
    private static double calculateStdDev(double[] values, double mean) {
        double[] nonZero = Arrays.stream(values)
            .filter(v -> v > 0)
            .toArray();
        
        if (nonZero.length == 0) return 0;
        
        double variance = Arrays.stream(nonZero)
            .map(v -> Math.pow(v - mean, 2))
            .average()
            .orElse(0);
        
        return Math.sqrt(variance);
    }
    
    /**
     * Anomaly Detection using Isolation Forest concept
     * Identifies patterns that don't match normal player behavior
     */
    public static class AnomalyDetector {
        private final Map<String, List<Double>> baselineProfiles;
        private final double ANOMALY_THRESHOLD = 0.7;
        
        public AnomalyDetector() {
            this.baselineProfiles = new ConcurrentHashMap<>();
            initializeBaselines();
        }
        
        /**
         * Initialize baseline behavior for legitimate players
         */
        private void initializeBaselines() {
            // Normal player mining accuracy distribution
            baselineProfiles.put("mining_accuracy", Arrays.asList(
                0.35, 0.38, 0.40, 0.42, 0.45, 0.48, 0.50, 0.52, 0.55
            ));
            
            // Normal attack interval distribution (ms)
            baselineProfiles.put("attack_interval", Arrays.asList(
                400.0, 450.0, 500.0, 550.0, 600.0, 650.0, 700.0
            ));
            
            // Normal aim consistency distribution
            baselineProfiles.put("aim_consistency", Arrays.asList(
                0.60, 0.65, 0.70, 0.75, 0.80, 0.85, 0.88
            ));
            
            // Normal move consistency distribution
            baselineProfiles.put("move_consistency", Arrays.asList(
                0.55, 0.60, 0.65, 0.70, 0.75, 0.80
            ));
        }
        
        /**
         * Detect if a behavioral value is anomalous
         */
        public boolean isAnomaly(String behaviorType, double value) {
            List<Double> baseline = baselineProfiles.get(behaviorType);
            if (baseline == null || baseline.isEmpty()) return false;
            
            // Calculate how many baseline values are within reasonable range
            double mean = baseline.stream().mapToDouble(d -> d).average().orElse(0);
            double stdDev = Math.sqrt(baseline.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0));
            
            // Z-score test
            double zScore = Math.abs((value - mean) / (stdDev + 0.01));
            return zScore > 2.5; // 3-sigma rule variation
        }
        
        /**
         * Calculate anomaly score (0-1, higher = more anomalous)
         */
        public double calculateAnomalyScore(String behaviorType, double value) {
            List<Double> baseline = baselineProfiles.get(behaviorType);
            if (baseline == null || baseline.isEmpty()) return 0;
            
            double mean = baseline.stream().mapToDouble(d -> d).average().orElse(0);
            double stdDev = Math.sqrt(baseline.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0));
            
            double zScore = Math.abs((value - mean) / (stdDev + 0.01));
            
            // Sigmoid function to convert z-score to probability
            return 1.0 / (1.0 + Math.exp(-zScore + 2));
        }
    }
    
    /**
     * Time-series anomaly detection
     * Detects when behavior changes suddenly
     */
    public static class TimeSeriesAnalyzer {
        private static final int WINDOW_SIZE = 30;
        
        /**
         * Detect sudden changes in behavior (e.g., suddenly perfect aim)
         */
        public static double detectBehaviorChange(List<Double> timeSeries) {
            if (timeSeries.size() < WINDOW_SIZE * 2) return 0;
            
            int mid = timeSeries.size() / 2;
            List<Double> before = timeSeries.subList(0, mid);
            List<Double> after = timeSeries.subList(mid, timeSeries.size());
            
            double meanBefore = before.stream().mapToDouble(d -> d).average().orElse(0);
            double meanAfter = after.stream().mapToDouble(d -> d).average().orElse(0);
            
            // Calculate variance
            double varBefore = before.stream()
                .mapToDouble(v -> Math.pow(v - meanBefore, 2))
                .average().orElse(0);
            double varAfter = after.stream()
                .mapToDouble(v -> Math.pow(v - meanAfter, 2))
                .average().orElse(0);
            
            // T-test for significant difference
            double pooledVar = (varBefore + varAfter) / 2;
            double t = Math.abs(meanBefore - meanAfter) / Math.sqrt(pooledVar / before.size());
            
            // Higher t = more significant change = more suspicious
            return Math.min(1.0, t / 5.0); // Normalize to 0-1
        }
        
        /**
         * Detect periodic patterns (e.g., bot's attack cycle)
         */
        public static boolean detectPeriodicBehavior(List<Double> timeSeries) {
            if (timeSeries.size() < WINDOW_SIZE) return false;
            
            // Calculate FFT (Fast Fourier Transform) - simplified autocorrelation
            double maxCorrelation = 0;
            
            for (int lag = 5; lag < Math.min(WINDOW_SIZE, timeSeries.size() / 2); lag++) {
                double correlation = calculateCorrelation(timeSeries, lag);
                maxCorrelation = Math.max(maxCorrelation, correlation);
            }
            
            // High correlation at regular intervals = periodic = suspicious
            return maxCorrelation > 0.85;
        }
        
        /**
         * Calculate autocorrelation at given lag
         */
        private static double calculateCorrelation(List<Double> series, int lag) {
            double sumProduct = 0;
            double sumSq1 = 0;
            double sumSq2 = 0;
            
            for (int i = 0; i < series.size() - lag; i++) {
                double v1 = series.get(i);
                double v2 = series.get(i + lag);
                
                sumProduct += v1 * v2;
                sumSq1 += v1 * v1;
                sumSq2 += v2 * v2;
            }
            
            double denominator = Math.sqrt(sumSq1 * sumSq2);
            return denominator > 0 ? sumProduct / denominator : 0;
        }
    }
    
    /**
     * Clustering analysis - identify cheater group behaviors
     */
    public static class BehaviorClustering {
        
        /**
         * Identify if player behavior is similar to known cheaters
         */
        public static double calculateBehaviorSimilarity(BehaviorProfile p1, BehaviorProfile p2) {
            double score = 0;
            int comparisons = 0;
            
            // Compare mining accuracy
            double miningDiff = Math.abs(p1.miningMean - p2.miningMean);
            if (miningDiff < 0.05) {
                score += 0.25;
                comparisons++;
            }
            
            // Compare attack intervals
            double attackDiff = Math.abs(p1.attackMean - p2.attackMean);
            if (attackDiff < 30) {
                score += 0.25;
                comparisons++;
            }
            
            // Compare aim consistency
            double aimDiff = Math.abs(p1.aimMean - p2.aimMean);
            if (aimDiff < 0.05) {
                score += 0.25;
                comparisons++;
            }
            
            // Compare cheat probability
            if (Math.abs(p1.calculateCheatProbability() - p2.calculateCheatProbability()) < 0.1) {
                score += 0.25;
                comparisons++;
            }
            
            return comparisons > 0 ? score / comparisons : 0;
        }
    }
}
