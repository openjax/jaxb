/* Copyright (c) 2017 lib4j
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * You should have received a copy of The MIT License (MIT) along with this
 * program. If not, see <http://opensource.org/licenses/MIT/>.
 */

package org.libx4j.maven.plugin.xjc;

import java.io.File;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.List;

import javax.xml.bind.JAXBException;

import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.lib4j.net.URLs;
import org.lib4j.util.Collections;
import org.lib4j.xml.jaxb.XJCompiler;
import org.libx4j.maven.specification.GeneratorMojo;
import org.libx4j.maven.specification.MojoUtil;

/**
 * Mojo that creates compile-scope Java source or binaries from XML schema(s) by
 * invoking the JAXB XJC binding compiler.
 */
@Mojo(name = "xjc", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresDependencyResolution = ResolutionScope.TEST)
@Execute(goal = "xjc")
public final class XJCMojo extends GeneratorMojo {
  // Corresponding XJC parameter: mark-generated.
  // This feature causes all of the generated code to have @Generated annotation.
  @Parameter(property = "addGeneratedAnnotation")
  private boolean addGeneratedAnnotation = false;

  // Corresponding XJC parameter: catalog.
  // Specify catalog files to resolve external entity references. Supports
  // TR9401, XCatalog, and OASIS XML Catalog format.
  @Parameter(property = "catalog")
  private File catalog;

  // Corresponding XJC parameter: enableIntrospection.
  // Enable correct generation of Boolean getters/setters to enable Bean
  // Introspection APIs.
  @Parameter(property = "enableIntrospection")
  private boolean enableIntrospection = true;

  // Defines the encoding used by XJC (for generating Java Source files) and
  // schemagen (for generating XSDs). The corresponding argument parameter for
  // XJC and SchemaGen is: encoding.
  //
  // The algorithm for finding the encoding to use is as follows (where the
  // first non-null value found is used for encoding):
  //
  // 1. If the configuration property is explicitly given within the plugin's
  //    configuration, use that value.
  // 2. If the Maven property project.build.sourceEncoding is defined, use its
  //    value.
  // 3. Otherwise use the value from the system property file.encoding.
  @Parameter(property = "encoding", defaultValue = "${project.build.sourceEncoding}")
  private String encoding;

  // Corresponding XJC parameter: extension.
  //
  // By default, the XJC binding compiler strictly enforces the rules outlined
  // in the Compatibility chapter of the JAXB Specification. Appendix E.2
  // defines a set of W3C XML Schema features that are not completely supported
  // by JAXB v1.0. In some cases, you may be allowed to use them in the
  // '-extension' mode enabled by this switch. In the default (strict) mode,
  // you are also limited to using only the binding customizations defined in
  // the specification.
  @Parameter(property = "extension")
  private boolean extension = false;

  public static class ExtraFacets {
    public static class ExtraFacet {
      private String implementation;
    }

    private List<ExtraFacet> extraFacet;
  }

  // Defines a set of extra EnvironmentFacet instances which are used to
  // further configure the ToolExecutionEnvironment used by this plugin to fire
  // XJC or SchemaGen.
  //
  // Example: If you implement the EnvironmentFacet interface in the class
  // org.acme.MyCoolEnvironmentFacetImplementation, its setup() method is
  // called before the XJC or SchemaGen tools are executed to setup some facet
  // of their Execution environment. Correspondingly, the restore() method in
  // your org.acme.MyCoolEnvironmentFacetImplementation class is invoked after
  // the XJC or SchemaGen execution terminates.
  @Parameter(property = "extraFacets")
  private ExtraFacets extraFacets;

  // Corresponding XJC parameter: episode.
  //
  // Generate an episode file from this compilation, so that other schemas that
  // rely on this schema can be compiled later and rely on classes that are
  // generated from this compilation. The generated episode file is really just
  // a JAXB customization file (but with vendor extensions.)
  //
  // If this parameter is true, the episode file generated is called
  // META-INF/sun-jaxb.episode, and included in the artifact.
  @Parameter(property = "generateEpisode")
  private boolean generateEpisode = false;

  // Corresponding XJC parameter: nv.
  //
  // By default, the XJC binding compiler performs strict validation of the
  // source schema before processing it. Use this option to disable strict
  // schema validation. This does not mean that the binding compiler will not
  // perform any validation, it simply means that it will perform less-strict
  // validation.
  @Parameter(property = "laxSchemaValidation")
  private boolean laxSchemaValidation = false;

  // Corresponding XJC parameter: no-header.
  //
  // Suppress the generation of a file header comment that includes some note
  // and timestamp. Using this makes the generated code more diff-friendly.
  @Parameter(property = "noGeneratedHeaderComments")
  private boolean noGeneratedHeaderComments = false;

  // Corresponding XJC parameter: npa.
  //
  // Suppress the generation of package level annotations into
  // package-info.java. Using this switch causes the generated code to
  // internalize those annotations into the other generated classes.
  @Parameter(property = "noPackageLevelAnnotations")
  private boolean noPackageLevelAnnotations = false;

  // Corresponding XJC parameter: p.
  //
  // The package under which the source files will be generated. Quoting the
  // XJC documentation: 'Specifying a target package via this command-line
  // option overrides any binding customization for package name and the
  // default package name algorithm defined in the specification'.
  @Parameter(property = "packageName")
  private String packageName;

  // Corresponding XJC parameter: quiet.
  // Suppress compiler output, such as progress information and warnings.
  @Parameter(property = "quiet")
  private boolean quiet = false;

  // Defines the content type of sources for the XJC. To simplify usage of the
  // JAXB2 maven plugin, all source files are assumed to have the same type of
  // content.
  //
  // This parameter replaces the previous multiple-choice boolean configuration
  // options for the jaxb2-maven-plugin (i.e. dtd, xmlschema, wsdl), and
  // corresponds to setting one of those flags as an XJC argument.
  @Parameter(property = "sourceType")
  private String sourceType = "xmlschema";

  // Corresponding XJC parameter: target.
  //
  // Permitted values: '2.0' and '2.1'. Avoid generating code that relies on
  // JAXB newer than the version given. This will allow the generated code to
  // run with JAXB 2.0 runtime (such as JavaSE 6.).
  @Parameter(property = "target")
  private String targetVersion;

  // Corresponding XJC parameter: verbose.
  //
  // Tells XJC to be extra verbose, such as printing informational messages or
  // displaying stack traces.
  // User property: xjc.verbose
  @Parameter(property = "verbose")
  private boolean verbose = false;

  public static class XjbExcludeFilters {
    public static class Filter {
      private String implementation;

      public static class Patterns {
        private List<String> pattern;
      }

      private Patterns patterns;
    }

    private List<Filter> filter;
  }

  // Parameter holding a List of Filters, used to match all files under the
  // xjbSources directories which should not be considered XJB files. (The
  // filters identify files to exclude, and hence this parameter is called
  // xjbExcludeFilters). If a file matches at least one of the supplied
  // Filters, it is not considered an XJB file, and therefore excluded from
  // processing.
  //
  // If not explicitly provided, the Mojo uses the value within
  // STANDARD_XJB_EXCLUDE_FILTERS.
  //
  // Example: The following configuration would exclude any XJB files whose
  // names end with xml or foo:
  //
  //  <configuration>
  //  ...
  //  <xjbExcludeFilters>
  //  <filter
  // implementation='org.codehaus.mojo.jaxb2.shared.filters.pattern.PatternFileFilter'>
  //  <patterns>
  //  <pattern>\.txt</pattern>
  //  <pattern>\.foo</pattern>
  //  </patterns>
  //  </filter>
  //  </xjbExcludeFilters>
  //  ...
  //  </configuration>
  //
  //
  // Note that inner workings of the Dependency Injection mechanism used by
  // Maven Plugins (i.e. the DI from the Plexus container) requires that the
  // full class name to the Filter implementation should be supplied for each
  // filter, as is illustrated in the sample above. This is true also if you
  // implement custom Filters.
  @Parameter(property = "xjbExcludeFilters")
  private XjbExcludeFilters xjbExcludeFilters;

  // Parameter holding List of XJB Files and/or directories which should be
  // recursively searched for XJB files. Only files or directories that
  // actually exist will be included (in the case of files) or recursively
  // searched for XJB files to include (in the case of directories). JAXB
  // binding files are used to configure parts of the Java source generation.
  // Supply the configuration using the standard Maven structure for
  // configuring plugin Lists:
  //
  //  <configuration>
  //  ...
  //  <xjbs>
  //  <xjb>bindings/aBindingConfiguration.xjb</xjb>
  //  <xjb>bindings/config/directory</xjb>
  //  </xjbs>
  //  </configuration>
  @Parameter(property = "xjbs")
  private List<String> xjbs;

  // Parameter holding a List of Filters, used to match all files under the
  // sources directories which should not be considered XJC source files. (The
  // filters identify files to exclude, and hence this parameter is called
  // xjcSourceExcludeFilters). If a file under any of the source directories
  // matches at least one of the Filters supplied in the
  // xjcSourceExcludeFilters, it is not considered an XJC source file, and
  // therefore excluded from processing.
  //
  // If not explicitly provided, the Mojo uses the value within
  // STANDARD_SOURCE_EXCLUDE_FILTERS. The algorithm for finding XJC sources is
  // as follows:
  //
  // 1.  Find all files given in the sources List. Any Directories provided are
  //   searched for files recursively.
  // 2.  Exclude any found files matching any of the supplied
  //   xjcSourceExcludeFilters List.
  // 3.  The remaining Files are submitted for processing by the XJC tool.
  //
  // Example: The following configuration would exclude any sources whose names
  // end with txt or foo:
  //
  //  <configuration>
  //  ...
  //  <xjcSourceExcludeFilters>
  //  <filter
  // implementation='org.codehaus.mojo.jaxb2.shared.filters.pattern.PatternFileFilter'>
  //  <patterns>
  //  <pattern>\.txt</pattern>
  //  <pattern>\.foo</pattern>
  //  </patterns>
  //  </filter>
  //  </xjcSourceExcludeFilters>
  //  </configuration>
  //
  // Note that inner workings of the Dependency Injection mechanism used by
  // Maven Plugins (i.e. the DI from the Plexus container) requires that the
  // full class name to the Filter implementation should be supplied for each
  // filter, as is illustrated in the sample above. This is true also if you
  // implement custom Filters.
  @Parameter(property = "xjcSourceExcludeFilters")
  private XjbExcludeFilters xjcSourceExcludeFilters;

  // If provided, this parameter indicates that the XSDs used by XJC to
  // generate Java code should be copied into the resulting artifact of this
  // project (the JAR, WAR or whichever artifact type is generated). The value
  // of the xsdPathWithinArtifact parameter is the relative path within the
  // artifact where all source XSDs are copied to (hence the name 'XSD Path
  // Within Artifact').
  //
  // The target directory is created within the artifact if it does not already
  // exist. If the xsdPathWithinArtifact parameter is not given, the XSDs used
  // to generate Java code are not included within the project's artifact.
  //
  // Example:Adding the sample configuration below would copy all source XSDs
  // to the given directory within the resulting JAR (and/or test-JAR). If the
  // directory META-INF/jaxb/xsd does not exist, it will be created.
  //
  //  <configuration>
  //  ...
  //  <xsdPathWithinArtifact>META-INF/jaxb/xsd</xsdPathWithinArtifact>
  //  </configuration>
  //
  //
  // Note: This parameter was previously called includeSchemasOutputPath in the
  // 1.x versions of this plugin, but was renamed and re-documented for
  // improved usability and clarity.
  @Parameter(property = "xsdPathWithinArtifact")
  private String xsdPathWithinArtifact;

  @Parameter(defaultValue = "${localRepository}")
  private ArtifactRepository localRepository;

  @Override
  public void execute(final Configuration configuration) throws MojoExecutionException, MojoFailureException {
    final ArtifactHandler artifactHandler = new DefaultArtifactHandler("jar");
    try {
      final XJCompiler.Command command = new XJCompiler.Command();
      command.setAddGeneratedAnnotation(addGeneratedAnnotation);
      command.setCatalog(catalog);
      command.setEnableIntrospection(enableIntrospection);
      command.setExtension(extension);
      command.setLaxSchemaValidation(laxSchemaValidation);
      command.setNoGeneratedHeaderComments(noGeneratedHeaderComments);
      command.setNoPackageLevelAnnotations(noPackageLevelAnnotations);
      command.setQuiet(quiet);
      if (targetVersion != null)
        command.setTargetVersion(XJCompiler.Command.TargetVersion.fromString(targetVersion));

      command.setVerbose(verbose);

      // TODO:...
      if (xjbExcludeFilters != null);

      // TODO:...
      if (xjcSourceExcludeFilters != null);

      // TODO:...
      if (xsdPathWithinArtifact != null);

      if (sourceType != null)
        command.setSourceType(XJCompiler.Command.SourceType.fromString(sourceType));

      command.setEncoding(encoding);
      command.setPackageName(packageName);
      command.setDestDir(configuration.getDestDir());
      command.setOverwrite(configuration.isOverwrite());
      command.setGenerateEpisode(generateEpisode);
      command.setSchemas(Collections.asCollection(new LinkedHashSet<URL>(), configuration.getResources()));

      if (this.xjbs != null && this.xjbs.size() > 0) {
        final LinkedHashSet<URL> xjbs = new LinkedHashSet<URL>();
        for (final String xjb : this.xjbs)
          xjbs.add(URLs.isAbsolute(xjb) ? URLs.makeUrlFromPath(xjb) : URLs.makeUrlFromPath(project.getBasedir().getAbsolutePath(), xjb));

        command.setXJBs(xjbs);
      }

      final List<String> classpath = MojoUtil.getPluginDependencyClassPath((PluginDescriptor)this.getPluginContext().get("pluginDescriptor"), localRepository, artifactHandler);
      classpath.addAll(project.getCompileClasspathElements());
      classpath.addAll(project.getRuntimeClasspathElements());
      if (isInTestPhase()) {
        classpath.addAll(project.getTestClasspathElements());
        classpath.addAll(MojoUtil.getProjectExecutionArtifactClassPath(project, localRepository, artifactHandler));
      }

      final File[] classpathFiles = new File[classpath.size()];
      for (int i = 0; i < classpathFiles.length; i++)
        classpathFiles[i] = new File(classpath.get(i));

      XJCompiler.compile(command, classpathFiles);

      if (isInTestPhase())
        project.addTestCompileSourceRoot(configuration.getDestDir().getAbsolutePath());
      else
        project.addCompileSourceRoot(configuration.getDestDir().getAbsolutePath());
    }
    catch (final JAXBException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
    catch (final Throwable t) {
      throw new MojoFailureException(t.getMessage(), t);
    }
  }
}