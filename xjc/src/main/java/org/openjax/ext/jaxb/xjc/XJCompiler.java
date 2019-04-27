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

package org.openjax.ext.jaxb.xjc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import javax.activation.DataSource;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.LogFactory;
import org.jvnet.annox.parser.XAnnotationParser;
import org.jvnet.jaxb2_commons.plugin.AbstractParameterizablePlugin;
import org.jvnet.jaxb2_commons.plugin.annotate.AnnotatePlugin;
import org.openjax.ext.exec.Processes;
import org.openjax.ext.io.Streams;
import org.openjax.ext.net.URLs;
import org.openjax.ext.util.ClassLoaders;
import org.openjax.ext.util.FastCollections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.istack.tools.MaskingClassLoader;
import com.sun.tools.xjc.XJCFacade;

import japa.parser.ast.Node;

public class XJCompiler {
  public static class Command {
    private boolean debug = false;

    /** Generated files will be in read-only mode. */
    private boolean readOnly = false;

    /** Suppress generation of a file header with timestamp. */
    private boolean noHeader = false;

    /** Generates code that works around issues specific to 1.4 runtime. */
    private boolean explicitAnnotation = false;

    /** If {@code true} XML security features when parsing XML documents will be disabled. The default value is {@code false}. */
    private boolean disableXmlSecurity = false;

    /** When on, generates content property for types with multiple xs:any derived elements (which is supposed to be correct behavior). */
    private boolean contentForWildcard = false;

    /** If true, try to resolve name conflicts automatically by assigning mechanical numbers. */
    private boolean autoNameResolution = false;

    /** This allocator has the final say on deciding the class name. */
    private boolean testClassNameAllocator = false;

    /** Java module name in {@code module-info.java}. */
    private String javaModule;

    /** File defining proxyHost:proxyPort */
    private File httpProxyFile;

    /** String defining proxyHost:proxyPort */
    private String httpProxy;

    // Corresponding XJC parameter: mark-generated.
    // This feature causes all of the generated code to have @Generated annotation.
    private boolean addGeneratedAnnotation = false;

    // Corresponding XJC parameter: catalog.
    // Specify catalog files to resolve external entity references. Supports
    // TR9401, XCatalog, and OASIS XML Catalog format.
    private File catalog;

    // Removes all files from the output directory before running XJC.
    private boolean overwrite = true;

    // Corresponding XJC parameter: enableIntrospection.
    // Enable correct generation of Boolean getters/setters to enable Bean
    // Introspection APIs.
    private boolean enableIntrospection = true;

    // Defines the encoding used by XJC (for generating Java Source files) and
    // schemagen (for generating XSDs). The corresponding argument parameter for
    // XJC and SchemaGen is: encoding.
    //
    // The algorithm for finding the encoding to use is as follows (where the
    // first non-null value found is used for encoding):
    //
    // 1. If the configuration property is explicitly given within the plugin's
    // configuration, use that value.
    // 2. If the Maven property project.build.sourceEncoding is defined, use its
    // value.
    // 3. Otherwise use the value from the system property file.encoding.
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
    private boolean extension = false;

    // Corresponding XJC parameter: episode.
    //
    // Generate an episode file from this compilation, so that other schemas that
    // rely on this schema can be compiled later and rely on classes that are
    // generated from this compilation. The generated episode file is really just
    // a JAXB customization file (but with vendor extensions.)
    //
    // If this parameter is true, the episode file generated is called
    // META-INF/sun-jaxb.episode, and included in the artifact.
    private boolean generateEpisode = false;

    // Corresponding XJC parameter: nv.
    //
    // By default, the XJC binding compiler performs strict validation of the
    // source schema before processing it. Use this option to disable strict
    // schema validation. This does not mean that the binding compiler will not
    // perform any validation, it simply means that it will perform less-strict
    // validation.
    private boolean laxSchemaValidation = false;

    // Corresponding XJC parameter: no-header.
    //
    // Suppress the generation of a file header comment that includes some note
    // and timestamp. Using this makes the generated code more diff-friendly.
    private boolean noGeneratedHeaderComments = false;

    // Corresponding XJC parameter: npa.
    //
    // Suppress the generation of package level annotations into
    // package-info.java. Using this switch causes the generated code to
    // internalize those annotations into the other generated classes.
    private boolean noPackageLevelAnnotations = false;

    // Corresponding XJC parameter: d.
    //
    // The working directory where the generated Java source files are created.
    // Required: Yes
    private File destDir;

    // Corresponding XJC parameter: p.
    //
    // The package under which the source files will be generated. Quoting the
    // XJC documentation: 'Specifying a target package via this command-line
    // option overrides any binding customization for package name and the
    // default package name algorithm defined in the specification'.
    private String packageName;

    // Corresponding XJC parameter: quiet.
    // Suppress compiler output, such as progress information and warnings.
    private boolean quiet = false;

    // Parameter holding List of XSD paths to files and/or directories which
    // should be recursively searched for XSD files. Only files or directories
    // that actually exist will be included (in the case of files) or recursively
    // searched for XSD files to include (in the case of directories). Configure
    // using standard Maven structure for Lists:
    //
    // <configuration>
    // ...
    // <schemas>
    // <schema>some/explicit/relative/file.xsd</schema>
    // <schema>/another/absolute/path/to/a/specification.xsd</schema>
    // <schema>a/directory/holding/xsds</schema>
    // </schemas>
    // </configuration>
    private LinkedHashSet<URL> schemas;

    public enum SourceType {
      DTD("dtd"),
      WSDL("wsdl"),
      XMLSCHEMA("xmlschema");

      private final String type;

      SourceType(final String type) {
        this.type = type;
      }

      public static SourceType fromString(final String sourceType) {
        return valueOf(sourceType.toUpperCase());
      }
    }

    // Defines the content type of sources for the XJC. To simplify usage of the
    // JAXB2 maven plugin, all source files are assumed to have the same type of
    // content.
    //
    // This parameter replaces the previous multiple-choice boolean configuration
    // options for the jaxb2-maven-plugin (i.e. dtd, xmlschema, wsdl), and
    // corresponds to setting one of those flags as an XJC argument.
    private SourceType sourceType;

    public enum TargetVersion {
      _2_0("2.0"),
      _2_1("2.1");

      private final String version;

      TargetVersion(final String type) {
        this.version = type;
      }

      public static TargetVersion fromString(final String version) {
        return valueOf("_" + version.replace('.', '_'));
      }
    }

    // Corresponding XJC parameter: target.
    //
    // Permitted values: '2.0' and '2.1'. Avoid generating code that relies on
    // JAXB newer than the version given. This will allow the generated code to
    // run with JAXB 2.0 runtime (such as JavaSE 6.).
    private TargetVersion targetVersion;

    // Corresponding XJC parameter: verbose.
    //
    // Tells XJC to be extra verbose, such as printing informational messages or
    // displaying stack traces.
    // User property: xjc.verbose
    private boolean verbose = false;

    // Parameter holding List of XJB Files and/or directories which should be
    // recursively searched for XJB files. Only files or directories that
    // actually exist will be included (in the case of files) or recursively
    // searched for XJB files to include (in the case of directories). JAXB
    // binding files are used to configure parts of the Java source generation.
    // Supply the configuration using the standard Maven structure for
    // configuring plugin Lists:
    //
    // <configuration>
    // ...
    // <xjbSources>
    // <xjbSource>bindings/aBindingConfiguration.xjb</xjbSource>
    // <xjbSource>bindings/config/directory</xjbSource>
    // </xjbSources>
    // </configuration>
    private LinkedHashSet<URL> xjbs;

    private final LinkedHashSet<File> classpath;

    public Command() {
      classpath = new LinkedHashSet<>();
      try {
        for (final Class<?> cls : new Class<?>[] {MaskingClassLoader.class, JAXBContext.class, AnnotatePlugin.class, AbstractParameterizablePlugin.class, LogFactory.class, XAnnotationParser.class, Node.class, DataSource.class, StringUtils.class})
          if (cls.getProtectionDomain().getCodeSource() != null)
            classpath.add(new File(cls.getProtectionDomain().getCodeSource().getLocation().toURI()));

        Collections.addAll(classpath, ClassLoaders.getClassPath());
      }
      catch (final URISyntaxException e) {
        throw new UnsupportedOperationException(e);
      }
    }

    public boolean getDebug() {
      return this.debug;
    }

    public void setDebug(final boolean debug) {
      this.debug = debug;
    }

    public boolean getReadOnly() {
      return this.readOnly;
    }

    public void setReadOnly(final boolean readOnly) {
      this.readOnly = readOnly;
    }

    public boolean getNoHeader() {
      return this.noHeader;
    }

    public void setNoHeader(final boolean noHeader) {
      this.noHeader = noHeader;
    }

    public boolean getExplicitAnnotation() {
      return explicitAnnotation;
    }

    public void setExplicitAnnotation(final boolean explicitAnnotation) {
      this.explicitAnnotation = explicitAnnotation;
    }

    public boolean getDisableXmlSecurity() {
      return disableXmlSecurity;
    }

    public void setDisableXmlSecurity(final boolean disableXmlSecurity) {
      this.disableXmlSecurity = disableXmlSecurity;
    }

    public boolean getContentForWildcard() {
      return contentForWildcard;
    }

    public void setContentForWildcard(final boolean contentForWildcard) {
      this.contentForWildcard = contentForWildcard;
    }

    public boolean getAutoNameResolution() {
      return autoNameResolution;
    }

    public void setAutoNameResolution(final boolean autoNameResolution) {
      this.autoNameResolution = autoNameResolution;
    }

    public boolean getTestClassNameAllocator() {
      return testClassNameAllocator;
    }

    public void setTestClassNameAllocator(final boolean testClassNameAllocator) {
      this.testClassNameAllocator = testClassNameAllocator;
    }

    public String getJavaModule() {
      return this.javaModule;
    }

    public void setJavaModule(final String javaModule) {
      this.javaModule = javaModule;
    }

    public File getHttpProxyFile() {
      return this.httpProxyFile;
    }

    public void setHttpProxyFile(final File httpProxyFile) {
      this.httpProxyFile = httpProxyFile;
    }

    public String getHttpProxy() {
      return this.httpProxy;
    }

    public void setHttpProxy(final String httpProxy) {
      this.httpProxy = httpProxy;
    }

    public boolean getAddGeneratedAnnotation() {
      return this.addGeneratedAnnotation;
    }

    public void setAddGeneratedAnnotation(final boolean addGeneratedAnnotation) {
      this.addGeneratedAnnotation = addGeneratedAnnotation;
    }

    public File getCatalog() {
      return this.catalog;
    }

    public void setCatalog(final File catalog) {
      this.catalog = catalog;
    }

    public boolean getOverwrite() {
      return this.overwrite;
    }

    public void setOverwrite(final boolean overwrite) {
      this.overwrite = overwrite;
    }

    public boolean getEnableIntrospection() {
      return this.enableIntrospection;
    }

    public void setEnableIntrospection(final boolean enableIntrospection) {
      this.enableIntrospection = enableIntrospection;
    }

    public String getEncoding() {
      return this.encoding;
    }

    public void setEncoding(final String encoding) {
      this.encoding = encoding;
    }

    public boolean getExtension() {
      return this.extension;
    }

    public void setExtension(final boolean extension) {
      this.extension = extension;
    }

    public boolean getGenerateEpisode() {
      return this.generateEpisode;
    }

    public void setGenerateEpisode(final boolean generateEpisode) {
      this.generateEpisode = generateEpisode;
    }

    public boolean getLaxSchemaValidation() {
      return this.laxSchemaValidation;
    }

    public void setLaxSchemaValidation(final boolean laxSchemaValidation) {
      this.laxSchemaValidation = laxSchemaValidation;
    }

    public boolean getNoGeneratedHeaderComments() {
      return this.noGeneratedHeaderComments;
    }

    public void setNoGeneratedHeaderComments(final boolean noGeneratedHeaderComments) {
      this.noGeneratedHeaderComments = noGeneratedHeaderComments;
    }

    public boolean getNoPackageLevelAnnotations() {
      return this.noPackageLevelAnnotations;
    }

    public void setNoPackageLevelAnnotations(final boolean noPackageLevelAnnotations) {
      this.noPackageLevelAnnotations = noPackageLevelAnnotations;
    }

    public File getDestDir() {
      return this.destDir;
    }

    public void setDestDir(final File destDir) {
      this.destDir = destDir;
    }

    public String getPackageName() {
      return this.packageName;
    }

    public void setPackageName(final String packageName) {
      this.packageName = packageName;
    }

    public boolean getQuiet() {
      return this.quiet;
    }

    public void setQuiet(final boolean quiet) {
      this.quiet = quiet;
    }

    public LinkedHashSet<URL> getSchemas() {
      return this.schemas;
    }

    public void setSchemas(final LinkedHashSet<URL> schemas) {
      this.schemas = schemas;
    }

    public SourceType getSourceType() {
      return this.sourceType;
    }

    public void setSourceType(final SourceType sourceType) {
      this.sourceType = sourceType;
    }

    public TargetVersion getTargetVersion() {
      return this.targetVersion;
    }

    public void setTargetVersion(final TargetVersion targetVersion) {
      this.targetVersion = targetVersion;
    }

    public boolean getVerbose() {
      return this.verbose;
    }

    public void setVerbose(final boolean verbose) {
      this.verbose = verbose;
    }

    public LinkedHashSet<URL> getXJBs() {
      return this.xjbs;
    }

    public void setXJBs(final LinkedHashSet<URL> xjbs) {
      this.xjbs = xjbs;
    }

    public LinkedHashSet<File> getClasspath() {
      return classpath;
    }

    public void addClasspath(final File path) {
      this.classpath.add(path);
    }

    public void addClasspath(final File ... paths) {
      Collections.addAll(classpath, paths);
    }
  }

  private static final Logger logger = LoggerFactory.getLogger(XJCompiler.class);
  // FIXME: Embedded mode breaks in openjax/rdb/sqlx when calling:
  // FIXME: mvn org.openjax.ext.jaxb:jaxb-maven-plugin:0.8.1-SNAPSHOT:xjc@jaxb-test-generate
  private static boolean embedded = false;

  public static void compile(final Command command) throws IOException, JAXBException {
    if (command.getSchemas() == null || command.getSchemas().size() == 0)
      return;

    final List<String> args = new ArrayList<>();
    if (command.classpath.size() > 0) {
      args.add("-cp");
      final StringBuilder cp = new StringBuilder();
      for (final File path : command.classpath)
        cp.append(File.pathSeparator).append(path.getAbsolutePath());

      args.add(cp.substring(1));
    }

    if (!embedded)
      args.add(XJCFacade.class.getName());

    args.add("-Xannotate");

    if (command.getDebug())
      args.add("-debug");

    if (command.getReadOnly())
      args.add("-readOnly");

    if (command.getNoHeader())
      args.add("-no-header");

    if (command.getExplicitAnnotation())
      args.add("-XexplicitAnnotation");

    if (command.getDisableXmlSecurity())
      args.add("-disableXmlSecurity");

    if (command.getContentForWildcard())
      args.add("-contentForWildcard");

    if (command.getAutoNameResolution())
      args.add("-XautoNameResolution");

    if (command.getTestClassNameAllocator())
      args.add("-Xtest-class-name-allocator");

    if (command.getHttpProxyFile() != null) {
      args.add("-httpproxyfile");
      args.add(command.getHttpProxyFile().getAbsolutePath());
    }

    if (command.getHttpProxy() != null) {
      args.add("-httpproxy");
      args.add(command.getHttpProxy());
    }

    if (command.getAddGeneratedAnnotation())
      args.add("-mark-generated");

    if (command.getCatalog() != null) {
      try {
        System.setProperty("xml.catalog.ignoreMissing", "true");
//        args.add(1, "-Dxml.catalog.ignoreMissing");
        args.add("-catalog");
        args.add(command.getCatalog().toURI().toURL().toString());
      }
      catch (final MalformedURLException e) {
        throw new UnsupportedOperationException(e);
      }
    }

    if (command.getEnableIntrospection())
      args.add("-enableIntrospection");

    if (command.getExtension())
      args.add("-extension");

    if (command.getLaxSchemaValidation())
      args.add("-nv");

    if (command.getNoGeneratedHeaderComments())
      args.add("-no-header");

    if (command.getNoPackageLevelAnnotations())
      args.add("-npa");

    if (command.getQuiet())
      args.add("-quiet");

    if (command.getTargetVersion() != null) {
      args.add("-target");
      args.add(command.getTargetVersion().version);
    }

    if (command.getVerbose())
      args.add("-verbose");

    if (command.getSourceType() != null)
      args.add("-" + command.getSourceType().type);

    if (command.getEncoding() != null) {
      args.add("-encoding");
      args.add(command.getEncoding());
    }

    if (command.getPackageName() != null) {
      args.add("-p");
      args.add(command.getPackageName());
    }

    if (command.getDestDir() != null) {
      args.add("-d");
      args.add(command.getDestDir().getAbsolutePath());

      if (!command.getDestDir().exists() && !command.getDestDir().mkdirs())
        throw new JAXBException("Unable to create output directory " + command.getDestDir().getAbsolutePath());

      // FIXME: This does not work because the files that are written are only known by xjc, so I cannot
      // FIXME: stop this generator from overwriting them if overwrite=false
//        else if (command.isOverwrite()) {
//          for (final File file : command.getDestDir().listFiles())
//            Files.walk(file.toPath()).map(Path::toFile).filter(a -> a.getName().endsWith(".java")).sorted((o1, o2) -> o2.compareTo(o1)).forEach(File::delete);
//        }
    }

    for (final URL schema : command.getSchemas()) {
      if (URLs.isLocalFile(schema)) {
        final File file = new File(schema.getFile());
        if (!file.exists())
          throw new FileNotFoundException(file.getAbsolutePath());

        args.add(file.getAbsolutePath());
      }
      else {
        try (final InputStream in = schema.openStream()) {
          final File file = File.createTempFile(URLs.getName(schema), "");
          args.add(file.getAbsolutePath());
          file.deleteOnExit();
          Files.write(file.toPath(), Streams.readBytes(in));
        }
      }

      args.add(schema.getFile());
    }

    if (command.getXJBs() != null) {
      for (final URL xjb : command.getXJBs()) {
        args.add("-b");
        if (URLs.isLocalFile(xjb)) {
          args.add(xjb.getFile());
        }
        else {
          try (final InputStream in = xjb.openStream()) {
            final File file = File.createTempFile(URLs.getName(xjb), "");
            args.add(file.getAbsolutePath());
            file.deleteOnExit();
            Files.write(file.toPath(), Streams.readBytes(in));
          }
        }
      }
    }

    if (command.getGenerateEpisode()) {
      final File metaInfDir = new File(command.getDestDir(), "META-INF" + File.separator + "sun-jaxb.episode");
      if (!metaInfDir.getParentFile().mkdirs())
        throw new JAXBException("Unable to create output directory: " + metaInfDir.getParentFile().getAbsolutePath());

      args.add("-episode");
      args.add(metaInfDir.getAbsolutePath());
    }

    final FilterOutputStream out = new FilterOutputStream(System.out) {
      final StringBuilder buffer = new StringBuilder();

      @Override
      public void write(final int b) throws IOException {
        if (b == '\n')
          flush();
        else
          buffer.append((char)b);
      }

      @Override
      public void flush() throws IOException {
        super.flush();
        if (buffer.length() == 0)
          return;

        final String line = buffer.toString();
        buffer.setLength(0);
        if (line.startsWith("[ERROR] "))
          logger.error(line.substring(8));
        else if (line.startsWith("[WARNING] "))
          logger.warn(line.substring(10));
        else
          logger.info(line);
      }
    };

    final MySecurityManager securityManager = new MySecurityManager(System.getSecurityManager());
    try {
      if (embedded) {
        System.setSecurityManager(securityManager);

        System.setProperty("com.sun.tools.xjc.XJCFacade.nohack", "true");
        System.setProperty("com.sun.tools.xjc.Options.findServices", "true");
        XJCFacade.main(args.toArray(new String[args.size()]));
      }
      else {
        addJavaArgs(args, false);
        final int exitCode = Processes.forkSync(null, out, null, true, null, null, args.toArray(new String[args.size()]));
        if (exitCode != 0)
          throw new JAXBException("xjc finished with code: " + exitCode + "\n" + FastCollections.toString(args, " "));
      }
    }
    catch (final IOException e) {
      throw e;
    }
    catch (final Throwable e) {
      if (!(e instanceof ExitPolicyException))
        throw new JAXBException(e.getMessage(), e);

      securityManager.disable();
      if (((ExitPolicyException)e).exitCode != 0) {
        throw new JAXBException(FastCollections.toString(embedded ? addJavaArgs(args, true) : args, " "));
      }
    }
  }

  private static List<String> addJavaArgs(final List<String> args, final boolean addClassPath) {
    args.addAll(0, Arrays.asList("-Dcom.sun.tools.xjc.XJCFacade.nohack=true", "-Dcom.sun.tools.xjc.Options.findServices=true"));
    if (addClassPath)
      args.addAll(0, Arrays.asList("-cp", System.getProperty("java.class.path"), XJCFacade.class.getName()));

    args.add(0, "java");
    return args;
  }

  private static class ExitPolicyException extends SecurityException {
    private static final long serialVersionUID = 8756682857129876527L;
    private final int exitCode;

    private ExitPolicyException(final int exitCode) {
      this.exitCode = exitCode;
    }
  }

  private static class MySecurityManager extends SecurityManager {
    private final SecurityManager securityManager;
    private boolean enabled = true;

    public MySecurityManager(final SecurityManager securityManager) {
      this.securityManager = securityManager;
    }

    public void disable() {
      enabled = false;
    }

    @Override
    public void checkPermission(final Permission permission) {
      if (enabled && permission.getName().startsWith("exitVM"))
        throw new ExitPolicyException(Integer.parseInt(permission.getName().substring(permission.getName().indexOf('.') + 1)));

      if (securityManager != null)
        securityManager.checkPermission(permission);
    }
  }
}