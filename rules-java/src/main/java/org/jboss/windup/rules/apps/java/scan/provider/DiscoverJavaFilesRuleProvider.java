package org.jboss.windup.rules.apps.java.scan.provider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.forge.furnace.util.Strings;
import org.jboss.forge.roaster.ParserException;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.Extendable;
import org.jboss.forge.roaster.model.InterfaceCapable;
import org.jboss.forge.roaster.model.source.JavaSource;
import org.jboss.windup.config.GraphRewrite;
import org.jboss.windup.config.RulePhase;
import org.jboss.windup.config.WindupRuleProvider;
import org.jboss.windup.config.operation.ruleelement.AbstractIterationOperation;
import org.jboss.windup.config.query.Query;
import org.jboss.windup.config.query.QueryPropertyComparisonType;
import org.jboss.windup.graph.GraphContext;
import org.jboss.windup.graph.model.WindupConfigurationModel;
import org.jboss.windup.graph.model.resource.FileModel;
import org.jboss.windup.graph.service.GraphService;
import org.jboss.windup.reporting.model.TechnologyTagLevel;
import org.jboss.windup.reporting.service.ClassificationService;
import org.jboss.windup.reporting.service.TechnologyTagService;
import org.jboss.windup.rules.apps.java.model.JavaClassModel;
import org.jboss.windup.rules.apps.java.model.JavaSourceFileModel;
import org.jboss.windup.rules.apps.java.service.JavaClassService;
import org.jboss.windup.util.Logging;
import org.jboss.windup.util.exception.WindupException;
import org.ocpsoft.rewrite.config.ConditionBuilder;
import org.ocpsoft.rewrite.config.Configuration;
import org.ocpsoft.rewrite.config.ConfigurationBuilder;
import org.ocpsoft.rewrite.context.EvaluationContext;

/**
 * Discovers .java files from the applications being analyzed.
 * 
 * @author jsightler <jesse.sightler@gmail.com>
 */
public class DiscoverJavaFilesRuleProvider extends WindupRuleProvider
{
    private static Logger LOG = Logging.get(DiscoverJavaFilesRuleProvider.class);

    private static final String TECH_TAG = "Java Source";
    private static final TechnologyTagLevel TECH_TAG_LEVEL = TechnologyTagLevel.INFORMATIONAL;

    @Override
    public RulePhase getPhase()
    {
        return RulePhase.POST_DISCOVERY;
    }

    // @formatter:off
    @Override
    public Configuration getConfiguration(GraphContext context)
    {
        ConditionBuilder javaSourceQuery = Query
            .find(FileModel.class)
            .withProperty(FileModel.IS_DIRECTORY, false)
            .withProperty(FileModel.FILE_PATH, QueryPropertyComparisonType.REGEX, ".*\\.java$");

        return ConfigurationBuilder.begin()
            .addRule()
            .when(javaSourceQuery)
            .perform(new IndexJavaFileIterationOperator());
        // @formatter:on
    }

    private final class IndexJavaFileIterationOperator extends AbstractIterationOperation<FileModel>
    {
        private static final int JAVA_SUFFIX_LEN = 5;

        private IndexJavaFileIterationOperator()
        {
            super();
        }

        @Override
        public void perform(GraphRewrite event, EvaluationContext context, FileModel payload)
        {
            TechnologyTagService technologyTagService = new TechnologyTagService(event.getGraphContext());
            GraphContext graphContext = event.getGraphContext();
            WindupConfigurationModel configuration = new GraphService<>(graphContext, WindupConfigurationModel.class)
                        .getUnique();

            String inputDir = configuration.getInputPath().getFilePath();
            inputDir = Paths.get(inputDir).toAbsolutePath().toString();

            String filepath = payload.getFilePath();
            filepath = Paths.get(filepath).toAbsolutePath().toString();

            if (!filepath.startsWith(inputDir))
            {
                return;
            }

            String classFilePath = filepath.substring(inputDir.length() + 1);
            String qualifiedName = classFilePath.replace(File.separatorChar, '.').substring(0,
                        classFilePath.length() - JAVA_SUFFIX_LEN);

            String packageName = "";
            if (qualifiedName.contains("."))
                packageName = qualifiedName.substring(0, qualifiedName.lastIndexOf("."));

            if (packageName.startsWith("src.main.java."))
            {
                packageName = packageName.substring("src.main.java.".length());
            }

            // make sure we mark this as a Java file
            JavaSourceFileModel javaFileModel = GraphService.addTypeToModel(graphContext, payload,
                        JavaSourceFileModel.class);
            technologyTagService.addTagToFileModel(javaFileModel, TECH_TAG, TECH_TAG_LEVEL);

            javaFileModel.setPackageName(packageName);
            try (FileInputStream fis = new FileInputStream(payload.getFilePath()))
            {
                addParsedClassToFile(fis, event.getGraphContext(), javaFileModel);
            }
            catch (FileNotFoundException e)
            {
                throw new WindupException("File in " + payload.getFilePath() + " was not found.", e);
            }
            catch (IOException e)
            {
                throw new WindupException("IOException thrown when parsing file located in " + payload.getFilePath(), e);
            }
            catch (Exception e)
            {
                LOG.log(Level.WARNING, "Could not parse java file: " + payload.getFilePath() + " due to: " + e.getMessage(), e);
                ClassificationService classificationService = new ClassificationService(graphContext);
                classificationService.attachClassification(javaFileModel, JavaSourceFileModel.UNPARSEABLE_JAVA_CLASSIFICATION,
                            JavaSourceFileModel.UNPARSEABLE_JAVA_DESCRIPTION);
                return;
            }
        }

        private void addParsedClassToFile(FileInputStream fis, GraphContext context, JavaSourceFileModel sourceFileModel)
        {
            JavaSource<?> javaSource;
            try
            {
                javaSource = Roaster.parse(JavaSource.class, fis);
            }
            catch (ParserException e)
            {
                ClassificationService classificationService = new ClassificationService(context);
                classificationService.attachClassification(sourceFileModel,
                            JavaSourceFileModel.UNPARSEABLE_JAVA_CLASSIFICATION,
                            JavaSourceFileModel.UNPARSEABLE_JAVA_DESCRIPTION);
                return;
            }

            String packageName = javaSource.getPackage();
            // set the package name to the parsed value
            sourceFileModel.setPackageName(packageName);
            String qualifiedName = javaSource.getQualifiedName();

            String simpleName = qualifiedName;
            if (packageName != null && !packageName.equals("") && simpleName != null)
            {
                simpleName = simpleName.substring(packageName.length() + 1);
            }

            JavaClassService javaClassService = new JavaClassService(context);
            JavaClassModel javaClassModel = javaClassService.getOrCreate(qualifiedName);
            javaClassModel.setOriginalSource(sourceFileModel);
            javaClassModel.setSimpleName(simpleName);
            javaClassModel.setPackageName(packageName);
            javaClassModel.setQualifiedName(qualifiedName);
            javaClassModel.setClassFile(sourceFileModel);

            if (javaSource instanceof InterfaceCapable)
            {
                InterfaceCapable interfaceCapable = (InterfaceCapable) javaSource;
                List<String> interfaceNames = interfaceCapable.getInterfaces();
                if (interfaceNames != null)
                {
                    for (String iface : interfaceNames)
                    {
                        JavaClassModel interfaceModel = javaClassService.getOrCreate(iface);
                        javaClassModel.addImplements(interfaceModel);
                    }
                }
            }

            if (javaSource instanceof Extendable)
            {
                Extendable<?> extendable = (Extendable<?>) javaSource;
                String superclassName = extendable.getSuperType();
                if (Strings.isNullOrEmpty(superclassName))
                    javaClassModel.setExtends(javaClassService.getOrCreate(superclassName));
            }

            sourceFileModel.addJavaClass(javaClassModel);
        }

        @Override
        public String toString()
        {
            return "AttachJavaSourceInformationToGraph";
        }
    }
}
