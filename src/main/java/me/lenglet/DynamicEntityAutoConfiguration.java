package me.lenglet;

import me.lenglet.entity.Book;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.dynamic.scaffold.TypeValidation;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.AnnotationVisitor;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.pool.TypePool;
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

import javax.persistence.Table;
import java.io.IOException;

import static net.bytebuddy.jar.asm.Opcodes.ASM4;

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
            this.setTableNameOfEntity(Book.class, this.environment.getProperty("dynamic.entities.book"));
        }

        private void setTableNameOfEntity(Class<?> entityClass, String tableName) {
            LOGGER.info("Redefining entity {}, setting table to '{}'", entityClass, tableName);

            final var alreadyHasColumnAnnotation = entityClass.getDeclaredAnnotation(Table.class) != null;
            ByteBuddyAgent.install();

            var builder = new ByteBuddy()
                    .with(TypeValidation.DISABLED)
                    .redefine(entityClass);

            if (alreadyHasColumnAnnotation) {
                builder = builder.visit(new FirstTableAnnotationRemoval());
            }

            builder = builder.annotateType(AnnotationDescription.Builder.ofType(Table.class)
                    .define("name", tableName)
                    .build());
            try (
                    final var unloaded = builder.make();
            ) {
                unloaded.load(entityClass.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void setEnvironment(Environment environment) {
            this.environment = environment;
        }

        private static class FirstTableAnnotationRemoval implements AsmVisitorWrapper {
            @Override
            public int mergeWriter(int flags) {
                return 0;
            }

            @Override
            public int mergeReader(int flags) {
                return 0;
            }

            @Override
            public ClassVisitor wrap(
                    TypeDescription instrumentedType,
                    ClassVisitor classVisitor,
                    Implementation.Context implementationContext,
                    TypePool typePool,
                    FieldList<FieldDescription.InDefinedShape> fields,
                    MethodList<?> methods,
                    int writerFlags,
                    int readerFlags
            ) {
                return new ClassVisitor(ASM4, classVisitor) {

                    private int tableAnnotationCounter = 0;

                    @Override
                    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        if ("Ljavax/persistence/Table;".equals(descriptor) && tableAnnotationCounter == 0) {
                            tableAnnotationCounter++;
                            return null;
                        }
                        return super.visitAnnotation(descriptor, visible);
                    }
                };
            }
        }
    }

}
