package vn.edu.iuh.regrag.user;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("db")
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class UserRepositoryIntegrationTests {

    @Autowired
    private UserRepository users;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void persistsAndFindsUserByEmailRegardlessOfCase() {
        User saved = users.saveAndFlush(new User("  Student@IUH.EDU.VN  ", "test-hash", UserRole.STUDENT));
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());

        entityManager.clear();

        User loaded = users.findByEmailIgnoreCase("STUDENT@IUH.EDU.VN").orElseThrow();
        assertThat(loaded.getId()).isEqualTo(saved.getId());
        assertThat(loaded.getEmail()).isEqualTo("student@iuh.edu.vn");
        assertThat(loaded.getPasswordHash()).isEqualTo("test-hash");
        assertThat(loaded.getRole()).isEqualTo(UserRole.STUDENT);
        assertThat(users.existsByEmailIgnoreCase("student@IUH.edu.vn")).isTrue();

        loaded.setRole(UserRole.ADMIN);
        users.flush();
        entityManager.clear();

        User updated = users.findById(saved.getId()).orElseThrow();
        assertThat(updated.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(updated.getCreatedAt());
    }
}
