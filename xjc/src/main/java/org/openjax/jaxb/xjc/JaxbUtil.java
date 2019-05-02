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

package org.openjax.jaxb.xjc;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.UnmarshalException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchema;
import javax.xml.bind.annotation.XmlType;
import javax.xml.namespace.QName;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;

import org.openjax.xml.sax.LoggingErrorHandler;
import org.openjax.xml.sax.Validator;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

/**
 * Utility functions for operations pertaining to JAXB.
 */
public final class JaxbUtil {
  private static final String DEFAULT = "##default";

  /**
   * Returns a string representation of the specified {@code binding}.
   *
   * @param <T> The type of specified {@code binding}.
   * @param binding The JAXB binding.
   * @return A string representation of the specified {@code binding}.
   * @throws JAXBException If an error was encountered while creating the
   *           {@link JAXBContext}, such as (but not limited to):
   *           <ol>
   *           <li>No JAXB implementation was discovered
   *           <li>Classes use JAXB annotations incorrectly
   *           <li>Classes have colliding annotations (i.e., two classes with
   *           the same type name)
   *           <li>The JAXB implementation was unable to locate
   *           provider-specific out-of-band information (such as additional
   *           files generated at the development time.)
   *           <li>{@code classesToBeBound} are not open to
   *           {@code java.xml.bind} module
   *           </ol>
   *           Or if an error was encountered while creating the
   *           {@link Marshaller} object.
   *           <p>
   *           Or if the {@link ValidationEventHandler} returns false from its
   *           handleEvent method or the {@link Marshaller} is unable to marshal
   *           the binding.
   */
  @SuppressWarnings("unchecked")
  public static <T>String toXmlString(final T binding) throws JAXBException {
    final StringWriter stringWriter = new StringWriter();
    final JAXBContext jaxbContext = JAXBContext.newInstance(binding.getClass());
    final Marshaller marshaller = jaxbContext.createMarshaller();

    marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

    final XmlRootElement xmlRootElement = binding.getClass().getAnnotation(XmlRootElement.class);
    if (xmlRootElement != null) {
      marshaller.marshal(binding, stringWriter);
      return stringWriter.toString();
    }

    final XmlType xmlType = binding.getClass().getAnnotation(XmlType.class);
    final String localName = DEFAULT.equals(xmlType.name()) ? binding.getClass().getSimpleName() : xmlType.name();
    final String namespace;
    if (DEFAULT.equals(xmlType.namespace())) {
      final XmlSchema xmlSchema = binding.getClass().getPackage().getAnnotation(XmlSchema.class);
      namespace = xmlSchema != null ? xmlSchema.namespace() : DEFAULT;
    }
    else {
      namespace = xmlType.namespace();
    }

    final QName qName = new QName(namespace, localName);
    final JAXBElement<T> element = new JAXBElement<>(qName, (Class<T>)binding.getClass(), binding);
    marshaller.marshal(element, stringWriter);
    return stringWriter.toString();
  }

  /**
   * Parses an XML document at the specified {@code url} as an instance of a
   * JAXB binding class {@code cls}.
   *
   * @param <T> The generic type of specified binding class {@code cls}.
   * @param cls The JAXB binding class.
   * @param url The location of the XML document to parse.
   * @return An XML document at the specified {@code url} as an instance of a
   *         JAXB binding class {@code cls}.
   * @throws IOException If an I/O error has occurred.
   * @throws UnmarshalException If {@code validate} is set to true, and
   *           validation of the XML document at {@code url} fails; or if this
   *           method is unable to perform the XML to Java binding.
   */
  public static <T>T parse(final Class<T> cls, final URL url) throws IOException, UnmarshalException {
    return parse(cls, Thread.currentThread().getContextClassLoader(), url, new LoggingErrorHandler(), true);
  }

  /**
   * Parses an XML document at the specified {@code url} as an instance of a
   * JAXB binding class {@code cls}.
   *
   * @param <T> The generic type of specified binding class {@code cls}.
   * @param cls The JAXB binding class.
   * @param classLoader Class loader used to locate the implementation classes.
   * @param url The location of the XML document to parse.
   * @return An XML document at the specified {@code url} as an instance of a
   *         JAXB binding class {@code cls}.
   * @throws IOException If an I/O error has occurred.
   * @throws UnmarshalException If {@code validate} is set to true, and
   *           validation of the XML document at {@code url} fails; or if this
   *           method is unable to perform the XML to Java binding.
   */
  public static <T>T parse(final Class<T> cls, final ClassLoader classLoader, final URL url) throws IOException, UnmarshalException {
    return parse(cls, classLoader, url, new LoggingErrorHandler(), true);
  }

  /**
   * Parses an XML document at the specified {@code url} as an instance of a
   * JAXB binding class {@code cls}.
   *
   * @param <T> The generic type of specified binding class {@code cls}.
   * @param cls The JAXB binding class.
   * @param url The location of the XML document to parse.
   * @param validate If {@code true}, the XML document at {@code url} will first
   *          be validated.
   * @return An XML document at the specified {@code url} as an instance of a
   *         JAXB binding class {@code cls}.
   * @throws IOException If an I/O error has occurred.
   * @throws UnmarshalException If {@code validate} is set to true, and
   *           validation of the XML document at {@code url} fails; or if this
   *           method is unable to perform the XML to Java binding.
   */
  public static <T>T parse(final Class<T> cls, final URL url, final boolean validate) throws IOException, UnmarshalException {
    return parse(cls, Thread.currentThread().getContextClassLoader(), url, new LoggingErrorHandler(), validate);
  }

  /**
   * Parses an XML document at the specified {@code url} as an instance of a
   * JAXB binding class {@code cls}.
   *
   * @param <T> The generic type of specified binding class {@code cls}.
   * @param cls The JAXB binding class.
   * @param classLoader Class loader used to locate the implementation classes.
   * @param url The location of the XML document to parse.
   * @param validate If {@code true}, the XML document at {@code url} will first
   *          be validated.
   * @return An XML document at the specified {@code url} as an instance of a
   *         JAXB binding class {@code cls}.
   * @throws IOException If an I/O error has occurred.
   * @throws UnmarshalException If {@code validate} is set to true, and
   *           validation of the XML document at {@code url} fails; or if this
   *           method is unable to perform the XML to Java binding.
   */
  public static <T>T parse(final Class<T> cls, final ClassLoader classLoader, final URL url, final boolean validate) throws IOException, UnmarshalException {
    return parse(cls, classLoader, url, new LoggingErrorHandler(), validate);
  }

  /**
   * Parses an XML document at the specified {@code url} as an instance of a
   * JAXB binding class {@code cls}.
   *
   * @param <T> The generic type of specified binding class {@code cls}.
   * @param cls The JAXB binding class.
   * @param url The location of the XML document to parse.
   * @param errorHandler The {@link ErrorHandler} for SAX validation.
   * @param validate If {@code true}, the XML document at {@code url} will first
   *          be validated.
   * @return An XML document at the specified {@code url} as an instance of a
   *         JAXB binding class {@code cls}.
   * @throws IOException If an I/O error has occurred.
   * @throws UnmarshalException If {@code validate} is set to true, and
   *           validation of the XML document at {@code url} fails; or if this
   *           method is unable to perform the XML to Java binding.
   */
  public static <T>T parse(final Class<T> cls, final URL url, final ErrorHandler errorHandler, final boolean validate) throws IOException, UnmarshalException {
    return parse(cls, Thread.currentThread().getContextClassLoader(), url, errorHandler, validate);
  }

  /**
   * Parses an XML document at the specified {@code url} as an instance of a
   * JAXB binding class {@code cls}.
   *
   * @param <T> The generic type of specified binding class {@code cls}.
   * @param cls The JAXB binding class.
   * @param classLoader Class loader used to locate the implementation classes.
   * @param url The location of the XML document to parse.
   * @param errorHandler The {@link ErrorHandler} for SAX validation.
   * @param validate If {@code true}, the XML document at {@code url} will first
   *          be validated.
   * @return An XML document at the specified {@code url} as an instance of a
   *         JAXB binding class {@code cls}.
   * @throws IOException If an I/O error has occurred.
   * @throws UnmarshalException If {@code validate} is set to true, and
   *           validation of the XML document at {@code url} fails; or if this
   *           method is unable to perform the XML to Java binding.
   */
  public static <T>T parse(final Class<T> cls, final ClassLoader classLoader, final URL url, final ErrorHandler errorHandler, final boolean validate) throws IOException, UnmarshalException {
    if (validate) {
      try {
        Validator.validate(url, false, errorHandler);
      }
      catch (final SAXException e) {
        throw new UnmarshalException(e);
      }
    }

    try (final InputStream in = url.openStream()) {
      final Unmarshaller unmarshaller = JAXBContext.newInstance(cls.getPackage().getName(), classLoader).createUnmarshaller();
      final JAXBElement<T> element = unmarshaller.unmarshal(XMLInputFactory.newInstance().createXMLStreamReader(in), cls);
      return element.getValue();
    }
    catch (final UnmarshalException e) {
      throw e;
    }
    catch (final FactoryConfigurationError | JAXBException | XMLStreamException e) {
      throw new IllegalStateException(e);
    }
  }

  private JaxbUtil() {
  }
}