package com.cinema.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm thử Kiến trúc Tự động (Gate B — Architecture Compliance).
 * Đảm bảo:
 * 1. Tầng giao diện (..ui.. và ..app..) tuyệt đối không gọi trực tiếp java.sql hoặc javax.sql.
 * 2. Tầng domain độc lập hoàn toàn với UI và JDBC.
 * 3. Tầng repository và service không bao giờ phụ thuộc ngược vào tầng UI.
 */
class ArchitectureComplianceTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importProductionClasses() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.cinema");
    }

    @Test
    @DisplayName("Gate B.1: Các lớp giao diện Swing (..ui..) không được truy cập trực tiếp java.sql hoặc javax.sql")
    void uiClassesMustNeverAccessSqlDirectly() {
        noClasses()
                .that().resideInAnyPackage("..ui..", "com.cinema.app..")
                .should().dependOnClassesThat().resideInAnyPackage("java.sql..", "javax.sql..")
                .because("Giao diện người dùng (UI) tuyệt đối không được thực thi SQL trực tiếp (Rule 2 & Gate B)")
                .check(importedClasses);
    }

    @Test
    @DisplayName("Gate B.2: Các lớp giao diện Feature UI không được gọi trực tiếp Repository mà phải đi qua Service")
    void featureUiClassesMustNotAccessRepositoriesDirectly() {
        noClasses()
                .that().resideInAPackage("com.cinema.feature..ui..")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .because("Màn hình giao diện chỉ được giao tiếp với tầng Service")
                .check(importedClasses);
    }

    @Test
    @DisplayName("Gate B.3: Tầng Domain, Repository và Service không được phụ thuộc ngược vào tầng Giao diện")
    void backendLayersMustNotDependOnUi() {
        noClasses()
                .that().resideInAnyPackage("com.cinema.domain..", "com.cinema.shared.db..")
                .or().haveSimpleNameEndingWith("Repository")
                .or().haveSimpleNameEndingWith("Service")
                .should().dependOnClassesThat().resideInAnyPackage("..ui..", "com.cinema.app..", "javax.swing..")
                .because("Tầng nghiệp vụ và dữ liệu phải hoàn toàn độc lập với Swing UI")
                .check(importedClasses);
    }

    @Test
    @DisplayName("Gate B.4: Mã nguồn vận hành chính (src/main/java) tuyệt đối không chứa lệnh DROP, TRUNCATE hoặc DELETE hàng loạt")
    void productionCodeMustNeverExecuteDestructiveSchemaOrBulkDeleteCommands() throws IOException {
        Pattern destructiveSql = Pattern.compile(
                "(?i)\\b(DROP\\s+DATABASE|DROP\\s+TABLE|TRUNCATE\\s+TABLE|DELETE\\s+FROM)\\b");
        Path mainSrc = Path.of("src", "main", "java");
        try (Stream<Path> files = Files.walk(mainSrc)) {
            List<Path> javaFiles = files.filter(p -> p.toString().endsWith(".java")).toList();
            for (Path file : javaFiles) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                assertTrue(!destructiveSql.matcher(content).find(),
                        "Phát hiện câu lệnh SQL phá hủy trong mã nguồn vận hành: " + file);
            }
        }
    }

    @Test
    @DisplayName("Gate B.5: Khởi động ứng dụng (Main, AppShell) không được phép gọi tiến trình hệ thống để chạy script reset CSDL")
    void applicationStartupMustNotInvokeExternalResetProcesses() {
        noClasses()
                .that().resideInAPackage("com.cinema..")
                .should().dependOnClassesThat().areAssignableTo(ProcessBuilder.class)
                .orShould().dependOnClassesThat().areAssignableTo(Runtime.class)
                .because("Ứng dụng vận hành bình thường không được gọi script reset/khởi tạo CSDL bên ngoài")
                .check(importedClasses);
    }
}
