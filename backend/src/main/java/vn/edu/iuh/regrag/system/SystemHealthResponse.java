package vn.edu.iuh.regrag.system;

import java.time.Instant;
import java.util.Map;

public record SystemHealthResponse(
        String status,
        Instant timestamp,
        Map<String, ComponentHealth> components) {

    public record ComponentHealth(String status, String details) {
        public static ComponentHealth up() {
            return new ComponentHealth("UP", null);
        }

        public static ComponentHealth down(String details) {
            return new ComponentHealth("DOWN", details);
        }
    }
}
