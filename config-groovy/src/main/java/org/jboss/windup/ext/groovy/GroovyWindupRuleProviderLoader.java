package org.jboss.windup.ext.groovy;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.codehaus.groovy.control.CompilerConfiguration;
import org.jboss.forge.furnace.Furnace;
import org.jboss.forge.furnace.addons.Addon;
import org.jboss.forge.furnace.addons.AddonFilter;
import org.jboss.forge.furnace.services.Imported;
import org.jboss.windup.config.WindupRuleProvider;
import org.jboss.windup.config.loader.WindupRuleProviderLoader;
import org.jboss.windup.graph.GraphContext;
import org.jboss.windup.graph.model.WindupConfigurationModel;
import org.jboss.windup.graph.model.resource.FileModel;
import org.jboss.windup.graph.service.WindupConfigurationService;
import org.jboss.windup.util.FurnaceCompositeClassLoader;
import org.jboss.windup.util.Logging;
import org.jboss.windup.util.exception.WindupException;
import org.jboss.windup.util.furnace.FurnaceClasspathScanner;

/**
 * Loads files with the specified extension (specified in {@link GroovyWindupRuleProviderLoader#GROOVY_RULES_EXTENSION} ), interprets them as Groovy
 * scripts, and returns the resulting {@link WindupRuleProvider}s.
 * 
 * @author jsightler <jesse.sightler@gmail.com>
 *
 */
public class GroovyWindupRuleProviderLoader implements WindupRuleProviderLoader
{
    private static final Logger LOG = Logging.get(GroovyWindupRuleProviderLoader.class);

    public static final String CURRENT_WINDUP_SCRIPT = "CURRENT_WINDUP_SCRIPT";

    private static final String GROOVY_RULES_EXTENSION = "windup.groovy";

    @Inject
    private FurnaceClasspathScanner scanner;
    @Inject
    private Furnace furnace;

    @Inject
    private Imported<GroovyConfigMethod> methods;

    @Override
    @SuppressWarnings("unchecked")
    public List<WindupRuleProvider> getProviders(final GraphContext context)
    {
        final List<WindupRuleProvider> ruleProviders = new ArrayList<WindupRuleProvider>();

        Binding binding = new Binding();
        binding.setVariable("windupRuleProviderBuilders", ruleProviders);
        binding.setVariable("supportFunctions", new HashMap<>());
        binding.setVariable("graphContext", context);

        GroovyConfigContext configContext = new GroovyConfigContext()
        {

            @Override
            public void addRuleProvider(WindupRuleProvider provider)
            {
                ruleProviders.add(provider);
            }

            @Override
            public GraphContext getGraphContext()
            {
                return context;
            }
        };

        for (GroovyConfigMethod method : methods)
        {
            binding.setVariable(method.getName(configContext), method.getClosure(configContext));
        }

        CompilerConfiguration config = new CompilerConfiguration();
        // TODO import everything!!! config.addCompilationCustomizers(new ImportCustomizer());
        ClassLoader loader = getCompositeClassloader();
        GroovyShell shell = new GroovyShell(loader, binding, config);

        try (InputStream supportFuncsIS = getClass().getResourceAsStream(
                    "/org/jboss/windup/addon/groovy/WindupGroovySupportFunctions.groovy"))
        {
            InputStreamReader isr = new InputStreamReader(supportFuncsIS);
            shell.evaluate(isr);
        }
        catch (Exception e)
        {
            throw new WindupException("Failed to load support functions due to: " + e.getMessage(), e);
        }

        Map<String, ?> supportFunctions = (Map<String, ?>) binding.getVariable("supportFunctions");
        for (Map.Entry<String, ?> supportFunctionEntry : supportFunctions.entrySet())
        {
            binding.setVariable(supportFunctionEntry.getKey(), supportFunctionEntry.getValue());
        }
        binding.setVariable("supportFunctions", null);

        for (URL resource : getScripts(context))
        {
            try (Reader reader = new InputStreamReader(resource.openStream()))
            {
                binding.setVariable(CURRENT_WINDUP_SCRIPT, resource.toExternalForm());
                shell.evaluate(reader);
            }
            catch (Exception e)
            {
                throw new WindupException("Failed to evaluate configuration: ", e);
            }
        }

        List<WindupRuleProvider> providers = (List<WindupRuleProvider>) binding
                    .getVariable("windupRuleProviderBuilders");

        return providers;
    }

    private ClassLoader getCompositeClassloader()
    {
        List<ClassLoader> loaders = new ArrayList<>();
        AddonFilter filter = new AddonFilter()
        {
            @Override
            public boolean accept(Addon addon)
            {
                // TODO this should only accept addons that depend on windup-config-groovy or whatever we call that
                return true;
            }
        };

        for (Addon addon : furnace.getAddonRegistry().getAddons(filter))
        {
            loaders.add(addon.getClassLoader());
        }

        return new FurnaceCompositeClassLoader(getClass().getClassLoader(), loaders);
    }

    private Iterable<URL> getScripts(GraphContext context)
    {
        List<URL> results = new ArrayList<>();
        List<URL> scripts = scanner.scan(GROOVY_RULES_EXTENSION);
        results.addAll(scripts);

        WindupConfigurationModel cfg = WindupConfigurationService.getConfigurationModel(context);
        Iterable<FileModel> userRulesFileModels = cfg.getUserRulesPaths();
        for (FileModel fm : userRulesFileModels)
        {
            results.addAll(getScripts(fm));
        }
        return results;
    }

    private Collection<URL> getScripts(FileModel userRulesFileModel)
    {
        String userRulesDirectory = userRulesFileModel == null ? null : userRulesFileModel.getFilePath();

        List<URL> scripts = scanner.scan(GROOVY_RULES_EXTENSION);

        // no user dir, so just return the ones that we found in the classpath
        if (userRulesDirectory == null)
        {
            return scripts;
        }
        Path userRulesPath = Paths.get(userRulesDirectory);

        if (!Files.isDirectory(userRulesPath))
        {
            LOG.warning("Not scanning: " + userRulesPath.normalize().toString() + " for rules as the directory could not be found!");
            return Collections.emptyList();
        }
        if (!Files.isDirectory(userRulesPath))
        {
            LOG.warning("Not scanning: " + userRulesPath.normalize().toString() + " for rules as the directory could not be read!");
            return Collections.emptyList();
        }

        // create the results as a copy (as we will be adding user groovy files to them)
        final List<URL> results = new ArrayList<>(scripts);

        try
        {
            Files.walkFileTree(userRulesPath, new SimpleFileVisitor<Path>()
            {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
                {
                    if (file.getFileName().toString().endsWith(GROOVY_RULES_EXTENSION))
                    {
                        results.add(file.toUri().toURL());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (IOException e)
        {
            throw new WindupException("Failed to search userdir: \"" + userRulesPath + "\" for groovy rules due to: "
                        + e.getMessage(), e);
        }

        return results;
    }
}
