<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:h="http://www.w3.org/1999/xhtml"
  version="1.0">

  <xsl:output method="xml" encoding="UTF-8" standalone="yes" version="1.0" indent="yes" />

  <xsl:param name="key" />
  <xsl:param name="href-base" select="'./'" />

  <xsl:param name="to-name" select="'Mr. Nobody'" />
  <xsl:param name="to-address" select="'nobody@example.com'" />

  <xsl:param name="from-name" select="'Test Suite'" />
  <xsl:param name="from-address" select="'noreply@bruun-rasmussen.dk'" />

  <xsl:param name="subject" select="'Sample Subject'" />

  <xsl:template match="/">

    <email>
      <addresses>
        <to>
          <email-address><xsl:value-of select="$to-address" /></email-address>
          <personal><xsl:value-of select="$to-name" /></personal>
        </to>

        <from>
          <email-address><xsl:value-of select="$from-address" /></email-address>
          <personal><xsl:value-of select="$from-name" /></personal>
        </from>
      </addresses>

      <subject><xsl:value-of select="$subject" /></subject>

      <html-body>
        <html>
          <head>
            <base href="{$href-base}" />
            <xsl:apply-templates select="h:html/h:head" />
          </head>
          <xsl:apply-templates select="h:html/h:body" />
        </html>
      </html-body>
    </email>
  </xsl:template>

  <xsl:template match="h:head">
    <xsl:apply-templates select="@*|node()"/>
  </xsl:template>

  <!-- strip http://www.w3.org/1999/xhtml from elements: -->
  <xsl:template match="h:*">
    <xsl:element name="{local-name()}">
      <xsl:apply-templates select="@* | node()"/>
    </xsl:element>
  </xsl:template>

  <!-- strip http://www.w3.org/1999/xhtml from attributes: -->
  <xsl:template match="h:@*">
    <xsl:attribute name="{local-name()}">
      <xsl:value-of select="."/>
    </xsl:attribute>
  </xsl:template>

  <!-- copy remaining: -->
  <xsl:template match="@*|node()">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>

</xsl:stylesheet>
