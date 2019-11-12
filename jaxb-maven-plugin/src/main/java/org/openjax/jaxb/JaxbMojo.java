/* Copyright (c) 2017 OpenJAX
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

package org.openjax.jaxb;

import java.io.File;
import java.io.FileWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

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
import org.libj.net.URLs;
import org.openjax.jaxb.xjc.XJCompiler;
import org.openjax.maven.mojo.FilterParameter;
import org.openjax.maven.mojo.FilterType;
import org.openjax.maven.mojo.GeneratorMojo;
import org.openjax.maven.mojo.MojoUtil;
import org.openjax.xml.sax.XMLCatalog;

/**
 * Mojo that creates compile-scope Java source or binaries from XML schema(s) by
 * invoking the JAXB XJC binding compiler.
 */
@Mojo(name="xjc", defaultPhase=LifecyclePhase.GENERATE_SOURCES, requiresDependencyResolution=ResolutionScope.TEST)
@Execute(goal="xjc")
public class JaxbMojo extends GeneratorMojo {
  /** Turn on debug mode. */
  @Parameter(property="debug")
  private boolean debug = false;

  /** Generated files will be in read-only mode. */
  @Parameter(property="readOnly")
  private boolean readOnly = false;

  /** Suppress generation of a file header with timestamp. */
  @Parameter(property="noHeader")
  private boolean noHeader = false;

  /** Generates code that works around issues specific to 1.4 runtime. */
  @Parameter(property="explicitAnnotation")
  private boolean explicitAnnotation = false;

  /** If {@code true} XML security features when parsing XML documents will be disabled. The default value is {@code false}. */
  @Parameter(property="disableXmlSecurity")
  private boolean disableXmlSecurity = false;

  /** When on, generates content property for types with multiple xs:any derived elements (which is supposed to be correct behavior). */
  @Parameter(property="contentForWildcard")
  private boolean contentForWildcard = false;

  /** If true, try to resolve name conflicts automatically by assigning mechanical numbers. */
  @Parameter(property="autoNameResolution")
  private boolean autoNameResolution = false;

  /** This allocator has the final say on deciding the class name. */
  @Parameter(property="testClassNameAllocator")
  private boolean testClassNameAllocator = false;

  /** Java module name in {@code module-info.java}. */
  @Parameter(property="javaModule")
  private String javaModule;

  /** File defining proxyHost:proxyPort */
  @Parameter(property="httpProxyFile")
  private File httpProxyFile;

  /** String defining proxyHost:proxyPort */
  @Parameter(property="httpProxy")
  private String httpProxy;

  /**
   * Corresponding XJC parameter: mark-generated.
   * This feature causes all of the generated code to have @Generated annotation.
   */
  @Parameter(property="addGeneratedAnnotation")
  private boolean addGeneratedAnnotation = false;

  /**
   * Corresponding XJC parameter: catalog.
   * Specify catalog files to resolve external entity references. Supports
   * TR9401, XCatalog, and OASIS XML Catalog format.
   */
  @Parameter(property="catalog")
  private File catalog;

  /**
   * Corresponding XJC parameter: enableIntrospection.
   * Enable correct generation of Boolean getters/setters to enable Bean
   * Introspection APIs.
   */
  @Parameter(property="enableIntrospection")
  private boolean enableIntrospection = true;

  /**
   * Defines the encoding used by XJC (for generating Java Source files) and
   * schemagen (for generating XSDs). The corresponding argument parameter for
   * XJC and SchemaGen is: encoding.
   *
   * The algorithm for finding the encoding to use is as follows (where the
   * first non-null value found is used for encoding):
   *
   * 1. If the configuration property is explicitly given within the plugin's
   *    configuration, use that value.
   * 2. If the Maven property project.build.sourceEncoding is defined, use its
   *    value.
   * 3. Otherwise use the value from the system property file.encoding.
   */
  @Parameter(property="encoding", defaultValue="${project.build.sourceEncoding}")
  private String encoding;

  /**
   * Corresponding XJC parameter: extension.
   *
   * By default, the XJC binding compiler strictly enforces the rules outlined
   * in the Compatibility chapter of the JAXB Specification. Appendix E.2
   * defines a set of W3C XML Schema features that are not completely supported
   * by JAXB v1.0. In some cases, you may be allowed to use them in the
   * '-extension' mode enabled by this switch. In the default (strict) mode,
   * you are also limited to using only the binding customizations defined in
   * the specification.
   */
  @Parameter(property="extension")
  private boolean extension = false;

  /**
   * Corresponding XJC parameter: episode.
   *
   * Generate an episode file from this compilation, so that other schemas that
   * rely on this schema can be compiled later and rely on classes that are
   * generated from this compilation. The generated episode file is really just
   * a JAXB customization file (but with vendor extensions.)
   *
   * If this parameter is true, the episode file generated is called
   * META-INF/sun-jaxb.episode, and included in the artifact.
   */
  @Parameter(property="generateEpisode")
  private boolean generateEpisode = false;

  /**
   * Corresponding XJC parameter: nv.
   *
   * By default, the XJC binding compiler performs strict validation of the
   * source schema before processing it. Use this option to disable strict
   * schema validation. This does not mean that the binding compiler will not
   * perform any validation, it simply means that it will perform less-strict
   * validation.
   */
  @Parameter(property="laxSchemaValidation")
  private boolean laxSchemaValidation = false;

  /**
   * Corresponding XJC parameter: no-header.
   *
   * Suppress the generation of a file header comment that includes some note
   * and timestamp. Using this makes the generated code more diff-friendly.
   */
  @Parameter(property="noGeneratedHeaderComments")
  private boolean noGeneratedHeaderComments = false;

  /**
   * Corresponding XJC parameter: npa.
   *
   * Suppress the generation of package level annotations into
   * package-info.java. Using this switch causes the generated code to
   * internalize those annotations into the other generated classes.
   */
  @Parameter(property="noPackageLevelAnnotations")
  private boolean noPackageLevelAnnotations = false;

  /**
   * Corresponding XJC parameter: p.
   *
   * The package under which the source files will be generated. Quoting the
   * XJC documentation: 'Specifying a target package via this command-line
   * option overrides any binding customization for package name and the
   * default package name algorithm defined in the specification'.
   */
  @Parameter(property="packageName")
  private String packageName;

  /**
   * Corresponding XJC parameter: quiet.
   * Suppress compiler output, such as progress information and warnings.
   */
  @Parameter(property="quiet")
  private boolean quiet = false;

  /**
   * Defines the content type of sources for the XJC. To simplify usage of the
   * JAXB2 maven plugin, all source files are assumed to have the same type of
   * content.
   *
   * This parameter replaces the previous multiple-choice boolean configuration
   * options for the jaxb2-maven-plugin (i.e. dtd, xmlschema, wsdl), and
   * corresponds to setting one of those flags as an XJC argument.
   */
  @Parameter(property="sourceType")
  private String sourceType = "xmlschema";

  /**
   * Corresponding XJC parameter: target.
   *
   * Permitted values: '2.0' and '2.1'. Avoid generating code that relies on
   * JAXB newer than the version given. This will allow the generated code to
   * run with JAXB 2.0 runtime (such as JavaSE 6.).
   */
  @Parameter(property="target")
  private String targetVersion;

  /**
   * Corresponding XJC parameter: verbose.
   *
   * Tells XJC to be extra verbose, such as printing informational messages or
   * displaying stack traces.
   * User property: xjc.verbose
   */
  @Parameter(property="verbose")
  private boolean verbose = false;

  /**
   * Parameter holding List of XSD paths to files and/or directories which
   * should be recursively searched for XSD files. Only files or directories
   * that actually exist will be included (in the case of files) or recursively
   * searched for XSD files to include (in the case of directories). Configure
   * using standard Maven structure for Lists:
   *
   * <blockquote><pre>
   * {@code <configuration>
   *   ...
   *   <schemas>
   *     <schema>some/explicit/relative/file.xsd</schema>
   *     <schema>/another/absolute/path/to/a/specification.xsd</schema>
   *     <schema>a/directory/holding/xsds</schema>
   *   </schemas>
   * </configuration>
   * }</pre></blockquote>
   */
  @FilterParameter(FilterType.URL)
  @Parameter(property="schemas", required=true)
  private List<String> schemas;

  /**
   * Parameter holding List of XJB Files and/or directories which should be
   * recursively searched for XJB files. Only files or directories that
   * actually exist will be included (in the case of files) or recursively
   * searched for XJB files to include (in the case of directories). JAXB
   * binding files are used to configure parts of the Java source generation.
   * Supply the configuration using the standard Maven structure for
   * configuring plugin Lists:
   *
   * <blockquote><pre>
   * {@code <configuration>
   *   ...
   *   <xjbs>
   *     <xjb>bindings/aBindingConfiguration.xjb</xjb>
   *     <xjb>bindings/config/directory</xjb>
   *   </xjbs>
   * </configuration>
   * }</pre></blockquote>
   */
  @FilterParameter(FilterType.URL)
  @Parameter(property="bindings")
  private List<String> bindings;

  @Parameter(defaultValue="${localRepository}")
  private ArtifactRepository localRepository;

  private static final ArtifactHandler artifactHandler = new DefaultArtifactHandler("jar");

  @Override
  public void execute(final Configuration configuration) throws MojoExecutionException, MojoFailureException {
    File masterCatalog = null;
    final XJCompiler.Command command = new XJCompiler.Command();
    try {
      command.setDebug(debug);
      command.setReadOnly(readOnly);
      command.setNoHeader(noHeader);
      command.setExplicitAnnotation(explicitAnnotation);
      command.setDisableXmlSecurity(disableXmlSecurity);
      command.setContentForWildcard(contentForWildcard);
      command.setAutoNameResolution(autoNameResolution);
      command.setTestClassNameAllocator(testClassNameAllocator);
      command.setJavaModule(javaModule);
      command.setHttpProxyFile(httpProxyFile);
      command.setHttpProxy(httpProxy);
      command.setAddGeneratedAnnotation(addGeneratedAnnotation);
      command.setEnableIntrospection(enableIntrospection);
      command.setExtension(extension);
      command.setLaxSchemaValidation(laxSchemaValidation);
      command.setNoGeneratedHeaderComments(noGeneratedHeaderComments);
      command.setNoPackageLevelAnnotations(noPackageLevelAnnotations);
      command.setQuiet(quiet);
      if (targetVersion != null)
        command.setTargetVersion(XJCompiler.Command.TargetVersion.fromString(targetVersion));

      if (sourceType != null)
        command.setSourceType(XJCompiler.Command.SourceType.fromString(sourceType));

      command.setVerbose(verbose);
      command.setEncoding(encoding);
      command.setPackageName(packageName);
      command.setDestDir(configuration.getDestDir());
      command.setOverwrite(configuration.getOverwrite());
      command.setGenerateEpisode(generateEpisode);

      masterCatalog = Files.createTempFile("catalog", ".cat").toFile();
      if (catalog != null)
        Files.copy(catalog.toPath(), masterCatalog.toPath(), StandardCopyOption.REPLACE_EXISTING);

      final LinkedHashSet<URL> urls = new LinkedHashSet<>();
      try (final FileWriter out = new FileWriter(masterCatalog)) {
        for (final String schema : new LinkedHashSet<>(schemas)) {
          final URL url = new URL(schema);
          urls.add(url);
          out.write(XMLCatalog.parse(url).toTR9401());
        }
      }

      command.setCatalog(masterCatalog);

      command.setSchemas(urls);
      if (bindings != null && bindings.size() > 0)
        command.setXJBs(new LinkedHashSet<>(bindings).stream().map(URLs::create).collect(Collectors.toCollection(LinkedHashSet::new)));

      command.addClasspath(MojoUtil.getExecutionClasspash(project, execution, (PluginDescriptor)this.getPluginContext().get("pluginDescriptor"), localRepository, artifactHandler));
      XJCompiler.compile(command);
    }
    catch (final JAXBException e) {
      throw new MojoExecutionException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
    }
    catch (final Exception e) {
      throw new MojoFailureException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
    }
    finally {
      if (!command.getDebug())
        masterCatalog.delete();
    }
  }
}