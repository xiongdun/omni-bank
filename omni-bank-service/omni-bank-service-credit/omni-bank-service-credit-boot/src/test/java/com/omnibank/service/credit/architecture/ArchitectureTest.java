package com.omnibank.service.credit.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * 信贷域架构守护：固化模块边界，违反即构建失败。
 * 覆盖范围：biz 适配层（adapter）+ 6 个场景领域模块（credit/loan/repay/limit/mortgage/postloan）。
 * 约定：场景包名正则 (credit|loan|repay|limit|mortgage|postloan) 需随新增场景同步扩展。
 */
class ArchitectureTest {

    private static final String SCENE_PACKAGES = "..service.credit.(credit|loan|repay|limit|mortgage|postloan)..";

    private static final String SCENE_APPLICATION_PACKAGES =
            "..service.credit.(credit|loan|repay|limit|mortgage|postloan).application..";

    private static final String SCENE_INFRASTRUCTURE_PACKAGES =
            "..service.credit.(credit|loan|repay|limit|mortgage|postloan).infrastructure..";

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.omnibank.service.credit");

    /** 领域纯净：场景模块不得依赖 Web/Servlet 与 Dubbo 框架（web/rpc 适配层统一在 boot adapter） */
    @Test
    void sceneModulesShouldNotDependOnWebAndRpcFrameworks() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(SCENE_PACKAGES)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web..", "jakarta.servlet..", "org.apache.dubbo..");
        rule.check(classes);
    }

    /** 依赖方向：场景模块不得依赖 boot 适配层（只能被 adapter 调用） */
    @Test
    void sceneModulesShouldNotDependOnAdapterLayer() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(SCENE_PACKAGES)
                .should().dependOnClassesThat().resideInAPackage("..service.credit.adapter..");
        rule.check(classes);
    }

    /** 分层依赖：适配层不得直达领域层（必须经 application 应用服务） */
    @Test
    void adapterShouldNotAccessDomainLayerDirectly() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..service.credit.adapter..")
                .should().dependOnClassesThat().resideInAPackage(
                        "..service.credit.(credit|loan|repay|limit|mortgage|postloan).domain..");
        rule.check(classes);
    }

    /** DDD 分层：application 不得依赖 infrastructure（仓储经 domain 接口注入） */
    @Test
    void applicationShouldNotDependOnInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(SCENE_APPLICATION_PACKAGES)
                .should().dependOnClassesThat().resideInAPackage(SCENE_INFRASTRUCTURE_PACKAGES);
        rule.check(classes);
    }

    /** 场景隔离：6 个场景模块之间不得互相依赖 */
    @Test
    void sceneModulesShouldNotDependOnEachOther() {
        ArchRule rule = slices()
                .matching("com.omnibank.service.credit.(credit|loan|repay|limit|mortgage|postloan).(**)")
                .should().notDependOnEachOther();
        rule.check(classes);
    }
}
