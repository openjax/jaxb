<!--
  Copyright (c) 2021 OpenJAX

  Permission is hereby granted, free of charge, to any person obtaining a copy
  of this software and associated documentation files (the "Software"), to deal
  in the Software without restriction, including without limitation the rights
  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
  copies of the Software, and to permit persons to whom the Software is
  furnished to do so, subject to the following conditions:

  The above copyright notice and this permission notice shall be included in
  all copies or substantial portions of the Software.

  You should have received a copy of The MIT License (MIT) along with this
  program. If not, see <http://opensource.org/licenses/MIT/>.
-->
<xsl:stylesheet
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema"
  version="2.0">

  <xsl:variable name="basedir" select="resolve-uri('.', base-uri())"/>

  <xsl:template match="@* | node()">
    <xsl:copy>
      <xsl:apply-templates select="@* | node()"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="//*[@schemaLocation]">
    <xsl:copy>
      <xsl:copy-of select="@*"/>
      <xsl:attribute name="schemaLocation">
        <xsl:choose>
          <xsl:when test="starts-with(@schemaLocation, '/') or contains(@schemaLocation, ':/')">
            <xsl:value-of select="@schemaLocation"/>
          </xsl:when>
          <xsl:otherwise>
            <xsl:value-of select="concat($basedir, @schemaLocation)"/>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:attribute>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="xs:assert"/>

</xsl:stylesheet>