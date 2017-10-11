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
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Mojo that creates compile-scope Java source or binaries from XML schema(s) by
 * invoking the JAXB XJC binding compiler. This implementation is tailored to use
 * the JAXB Reference Implementation from project Kenai.
 *
 * Note that the XjcMojo was completely re-implemented for the 2.x versions. Its
 * configuration semantics and parameter set is not necessarily backwards
 * compatible with the 1.x plugin versions. If you are upgrading from version 1.x
 * of the plugin, read the documentation carefully.
 */
@Mojo(name = "xjc", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
@Execute(goal = "xjc")
public final class XJCMojo extends AbstractMojo {
  @Parameter(defaultValue="${project}", readonly=true)
  private MavenProject project;

  @Parameter(defaultValue="${mojoExecution}", readonly=true)
  private MojoExecution execution;

  @Parameter(property="maven.test.skip", defaultValue="false")
  private boolean mavenTestSkip;

  // Corresponding XJC parameter: mark-generated.
  // This feature causes all of the generated code to have @Generated annotation.
  @Parameter(property="addGeneratedAnnotation")
  private boolean addGeneratedAnnotation = false;

  // Corresponding XJC parameter: catalog.
  // Specify catalog files to resolve external entity references. Supports
  // TR9401, XCatalog, and OASIS XML Catalog format.
  @Parameter(property="catalog")
  private File catalog;

  // Removes all files from the output directory before running XJC.
  @Parameter(property="clearOutputDir")
  private boolean clearOutputDir = true;

  // Corresponding XJC parameter: enableIntrospection.
  // Enable correct generation of Boolean getters/setters to enable Bean
  // Introspection APIs.
  @Parameter(property="enableIntrospection")
  private boolean enableIntrospection = false;

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
  @Parameter(property="encoding", defaultValue="${project.build.sourceEncoding}")
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
  @Parameter(property="extension")
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
  @Parameter(property="extraFacets")
  private ExtraFacets extraFacets;

  // Fails the Mojo execution if no XSDs/schemas are found.
  @Parameter(property="failOnNoSchemas")
  private boolean failOnNoSchemas = true;

  // Corresponding XJC parameter: episode.
  //
  // Generate an episode file from this compilation, so that other schemas that
  // rely on this schema can be compiled later and rely on classes that are
  // generated from this compilation. The generated episode file is really just
  // a JAXB customization file (but with vendor extensions.)
  //
  // If this parameter is true, the episode file generated is called
  // META-INF/sun-jaxb.episode, and included in the artifact.
  @Parameter(property="generateEpisode")
  private boolean generateEpisode = true;

  // Corresponding XJC parameter: nv.
  //
  // By default, the XJC binding compiler performs strict validation of the
  // source schema before processing it. Use this option to disable strict
  // schema validation. This does not mean that the binding compiler will not
  // perform any validation, it simply means that it will perform less-strict
  // validation.
  @Parameter(property="laxSchemaValidation")
  private boolean laxSchemaValidation = false;

  // Corresponding XJC parameter: no-header.
  //
  // Suppress the generation of a file header comment that includes some note
  // and timestamp. Using this makes the generated code more diff-friendly.
  @Parameter(property="noGeneratedHeaderComments")
  private boolean noGeneratedHeaderComments = false;

  // Corresponding XJC parameter: npa.
  //
  // Suppress the generation of package level annotations into
  // package-info.java. Using this switch causes the generated code to
  // internalize those annotations into the other generated classes.
  @Parameter(property="noPackageLevelAnnotations")
  private boolean noPackageLevelAnnotations = false;

  // Corresponding XJC parameter: d.
  //
  // The working directory where the generated Java source files are created.
  // Required: Yes
  @Parameter(property="outputDirectory", defaultValue="${project.build.directory}/generated-sources/jaxb")
  private File outputDirectory;

  // Corresponding XJC parameter: p.
  //
  // The package under which the source files will be generated. Quoting the
  // XJC documentation: 'Specifying a target package via this command-line
  // option overrides any binding customization for package name and the
  // default package name algorithm defined in the specification'.
  @Parameter(property="packageName")
  private String packageName;

  // Corresponding XJC parameter: quiet.
  // Suppress compiler output, such as progress information and warnings.
  @Parameter(property="quiet")
  private boolean quiet = false;

  // Corresponding XJC parameter: readOnly.
  //
  // By default, the XJC binding compiler does not write-protect the Java
  // source files it generates. Use this option to force the XJC binding
  // compiler to mark the generated Java sources read-only.
  @Parameter(property="readOnly")
  private boolean readOnly = false;

  // Indicate if the XjcMojo execution should be skipped.
  // User property: xjc.skip
  @Parameter(property="xjc.skip")
  private boolean skipXjc = false;

  // Parameter holding List of XSD paths to files and/or directories which
  // should be recursively searched for XSD files. Only files or directories
  // that actually exist will be included (in the case of files) or recursively
  // searched for XSD files to include (in the case of directories). Configure
  // using standard Maven structure for Lists:
  //
  //  <configuration>
  //  ...
  //  <sources>
  //  <source>some/explicit/relative/file.xsd</source>
  //  <source>/another/absolute/path/to/a/specification.xsd</source>
  //  <source>a/directory/holding/xsds</source>
  //  </sources>
  //  </configuration>
  @Parameter(property="sources", required=true)
  private List<File> sources;

  private static final String[] sourceTypes = new String[] {"dtd", "wsdl", "xmlschema"};
  // Defines the content type of sources for the XJC. To simplify usage of the
  // JAXB2 maven plugin, all source files are assumed to have the same type of
  // content.
  //
  // This parameter replaces the previous multiple-choice boolean configuration
  // options for the jaxb2-maven-plugin (i.e. dtd, xmlschema, wsdl), and
  // corresponds to setting one of those flags as an XJC argument.
  @Parameter(property="sourceType")
  private String sourceType = "xmlschema";

  // Corresponding XJC parameter: target.
  //
  // Permitted values: '2.0' and '2.1'. Avoid generating code that relies on
  // JAXB newer than the version given. This will allow the generated code to
  // run with JAXB 2.0 runtime (such as JavaSE 6.).
  @Parameter(property="target")
  private String target;

  // Corresponding XJC parameter: verbose.
  //
  // Tells XJC to be extra verbose, such as printing informational messages or
  // displaying stack traces.
  // User property: xjc.verbose
  @Parameter(property="verbose")
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
  @Parameter(property="xjbExcludeFilters")
  private XjbExcludeFilters xjbExcludeFilters;

  public static class XjbSources {
    private List<File> xjbSource;
  }

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
  //  <xjbSources>
  //  <xjbSource>bindings/aBindingConfiguration.xjb</xjbSource>
  //  <xjbSource>bindings/config/directory</xjbSource>
  //  </xjbSources>
  //  </configuration>
  @Parameter(property="xjbSources")
  private XjbSources xjbSources;

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
  @Parameter(property="xjcSourceExcludeFilters")
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
  @Parameter(property="xsdPathWithinArtifact")
  private String xsdPathWithinArtifact;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    final boolean inTestPhase = execution.getLifecyclePhase() != null && execution.getLifecyclePhase().contains("test");
    if (mavenTestSkip && inTestPhase)
      return;

    if (skipXjc) {
      getLog().info("Execution skipped.");
      return;
    }

    final List<String> command = new ArrayList<String>();
    command.add("xjc");
    if (addGeneratedAnnotation)
      command.add("-mark-generated");

    if (catalog != null) {
      command.add("-catalog");
      command.add(catalog.getAbsolutePath());
    }

    if (enableIntrospection)
      command.add("-enableIntrospection");

    if (extension)
      command.add("-extension");

    // TODO:...
    if (extraFacets != null);

    if (laxSchemaValidation)
      command.add("-nv");

    if (noGeneratedHeaderComments)
      command.add("-no-header");

    if (noPackageLevelAnnotations)
      command.add("-npa");

    if (quiet)
      command.add("-quiet");

    if (readOnly)
      command.add("-readOnly");

    if (target != null) {
      command.add("-target");
      command.add(target);
    }

    if (verbose)
      command.add("-verbose");

    // TODO:...
    if (xjbExcludeFilters != null);

    // TODO:...
    if (xjcSourceExcludeFilters != null);

    // TODO:...
    if (xsdPathWithinArtifact != null);

    if (sourceType != null) {
      if (Arrays.binarySearch(sourceTypes, sourceType) < 0)
        throw new MojoExecutionException("Unknown sourceType: " + sourceType);

      command.add("-" + sourceType);
    }

    if (encoding != null) {
      command.add("-encoding");
      command.add(encoding);
    }

    if (packageName != null) {
      command.add("-p");
      command.add(packageName);
    }

    if (outputDirectory != null) {
      command.add("-d");
      command.add(outputDirectory.getAbsolutePath());
    }

    try {
      if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
        throw new MojoExecutionException("Unable to create output directory " + outputDirectory.getAbsolutePath());
      }
      else if (clearOutputDir) {
        for (final File file : outputDirectory.listFiles())
          Files.walk(file.toPath()).map(Path::toFile).sorted((o1, o2) -> o2.compareTo(o1)).forEach(File::delete);
      }

      if (generateEpisode) {
        command.add("-episode");
        final File metaInfDir = new File(outputDirectory, "META-INF" + File.separator + "sun-jaxb.episode");
        if (!metaInfDir.getParentFile().mkdirs())
          throw new MojoExecutionException("Unable to create output directory META-INF" + metaInfDir.getParentFile().getAbsolutePath());

        command.add(metaInfDir.getAbsolutePath());
      }

      if (sources != null && sources != null) {
        boolean found = false;
        for (final File source : sources) {
          try {
            if (source.exists()) {
              if (source.isFile()) {
                found = source.getName().endsWith(".xsd");
              }
              else {
                try (final Stream<Path> stream = Files.find(source.toPath(), 999, new BiPredicate<Path,BasicFileAttributes>() {
                  @Override
                  public boolean test(final Path t, final BasicFileAttributes u) {
                    return t.endsWith(".xsd");
                  }
                }, FileVisitOption.FOLLOW_LINKS)) {
                  found = stream.limit(1).count() == 1;
                }
              }

              command.add(source.getAbsolutePath());
            }
            else {
              getLog().warn("File not found: " + source.getAbsolutePath());
            }
          }
          catch (final NoSuchFileException e) {
            getLog().warn("File not found: " + e.getMessage());
          }
        }

        if (!found) {
          if (failOnNoSchemas)
            throw new MojoExecutionException("No XSDs/schemas found.");

          return;
        }
      }

      if (xjbSources != null && xjbSources.xjbSource != null) {
        for (final File xjbSource : xjbSources.xjbSource) {
          command.add("-b");
          command.add(xjbSource.getAbsolutePath());
        }
      }

      final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
      try (final Scanner scanner = new Scanner(process.getInputStream())) {
        while (scanner.hasNextLine())
          log(scanner.nextLine().trim());

        final StringBuilder lastLine = new StringBuilder();
        while (scanner.hasNextByte())
          lastLine.append(scanner.nextByte());

        if (lastLine.length() > 0)
          log(lastLine.toString());
      }

      final int exitCode = process.waitFor();
      if (exitCode != 0)
        throw new MojoExecutionException("xjc finished with code: " + exitCode);
    }
    catch (final InterruptedException | IOException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }

    if (inTestPhase)
      project.addTestCompileSourceRoot(outputDirectory.getAbsolutePath());
    else
      project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
  }

  private void log(final String line) {
    if (line.startsWith("[ERROR] "))
      getLog().error(line.substring(8));
    else if (line.startsWith("[WARNING] "))
      getLog().warn(line.substring(10));
    else
      getLog().info(line);
  }
}