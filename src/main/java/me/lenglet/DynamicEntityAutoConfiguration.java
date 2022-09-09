package me.lenglet;

import me.lenglet.entity.Book;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.matcher.ElementMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;

import javax.persistence.Column;
import javax.persistence.Table;
import java.io.IOException;

@AutoConfiguration(before = JpaRepositoriesAutoConfiguration.class)
@Import(DynamicEntityAutoConfiguration.Config.class)
public class DynamicEntityAutoConfiguration {


    static class Config implements ImportSelector {

        @Override
        public String[] selectImports(AnnotationMetadata importingClassMetadata) {
            return new String[]{DynamicEntityAutoConfiguration.Registrar.class.getName()};
        }
    }

    static class Registrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

        private static final Logger LOGGER = LoggerFactory.getLogger(Registrar.class);
        private Environment environment;

        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry, BeanNameGenerator importBeanNameGenerator) {
            LOGGER.info("Redefining entity Book");
            ByteBuddyAgent.install();
            try (
                    final var newType = new ByteBuddy().redefine(Book.class)
                            .annotateType(AnnotationDescription.Builder.ofType(Table.class)
                                    .define("name", this.environment.getProperty("dynamic.entities.book"))
                                    .build())
                            .field(ElementMatchers.is(Book.class.getDeclaredField("title")))
                            .annotateField(AnnotationDescription.Builder.ofType(Column.class)
                                    .define("name", "THE_TITLE")
                                    .build())
                            .make()
            ) {
                newType.load(Book.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
            } catch (IOException | NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void setEnvironment(Environment environment) {
            this.environment = environment;
        }
    }

}
