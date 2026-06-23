package com.batteryhealth.app.utils;

public final class Constants {

    private Constants() {}

    public static class Battery {
        public static final int MIN_DESIGN_CAPACITY = 500;
        public static final int MAX_DESIGN_CAPACITY = 100000;
        public static final int HEALTH_EXCELLENT_THRESHOLD = 95;
        public static final int HEALTH_GOOD_THRESHOLD = 85;
        public static final int HEALTH_AVERAGE_THRESHOLD = 75;
        public static final int HEALTH_POOR_THRESHOLD = 60;
        public static final int HEALTH_ALERT_THRESHOLD = 80;
        public static final float DAILY_LOSS_RATE = 0.026f;
        public static final int MEDIAN_FILTER_WINDOW = 5;
    }

    public static class Network {
        public static final int TIMEOUT_MS = 30000;
        public static final int RETRY_COUNT = 3;
        public static final long RETRY_DELAY_MS = 1000;
    }

    public static class Database {
        public static final String DB_NAME = "battery_health_db";
        public static final int MAX_WAIT_SECONDS = 30;
        public static final int MAX_RESTORE_SECONDS = 60;
        public static final int HISTORY_RETENTION_DAYS = 7;
    }

    public static class Worker {
        public static final int DATA_COLLECTION_INTERVAL_MINUTES = 5;
        public static final int DATA_COLLECTION_FLEX_MINUTES = 1;
        public static final int HEALTH_ALERT_INTERVAL_HOURS = 1;
        public static final int HEALTH_ALERT_FLEX_MINUTES = 15;
    }

    public static class Notification {
        public static final String CHANNEL_ID = "battery_health_alert";
        public static final int ALERT_NOTIFICATION_ID = 1001;
    }

    public static class Charging {
        public static final float SUPER_FAST_THRESHOLD = 100f;
        public static final float FAST_THRESHOLD = 60f;
        public static final float QUICK_THRESHOLD = 30f;
        public static final float NORMAL_THRESHOLD = 10f;
    }

    public static class HealthGrade {
        public static final float A_PLUS = 95f;
        public static final float A = 90f;
        public static final float A_MINUS = 85f;
        public static final float B_PLUS = 80f;
        public static final float B = 75f;
        public static final float B_MINUS = 70f;
        public static final float C = 60f;
    }
}