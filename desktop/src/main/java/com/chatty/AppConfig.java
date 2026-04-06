package com.chatty;

/**
 * Cấu hình ứng dụng
 */
public class AppConfig {
    
    // ===== API & SOCKET CONFIG =====
    // Cho production (Render)
    public static final String API_URL_PRODUCTION = "https://chatapp-production-e572.up.railway.app";
    
    // Cho local development
    public static final String API_URL_LOCAL = "http://localhost:3000";
    
    // ===== Chọn môi trường hiện tại =====
    // Thay đổi giá trị này để chuyển đổi giữa các môi trường
    private static final Environment CURRENT_ENV = Environment.PRODUCTION;
    
    public enum Environment {
        PRODUCTION,  // Chạy trên Render (production)
        LOCAL,       // Chạy trên máy local (localhost:3000)
    }
    
    /**
     * Lấy URL API hiện tại dựa trên môi trường
     */
    public static String getApiUrl() {
        return switch (CURRENT_ENV) {
            case PRODUCTION -> API_URL_PRODUCTION;
            case LOCAL -> API_URL_LOCAL;
        };
    }
    
    /**
     * Lấy mô tả môi trường hiện tại
     */
    public static String getEnvironmentName() {
        return CURRENT_ENV.name();
    }
}
